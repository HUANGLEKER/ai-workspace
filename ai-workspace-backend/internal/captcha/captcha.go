// Package captcha 封装登录滑块拼图验证：用 go-captcha 生成拼图，答案与一次性通行令牌存 Redis。
// 不读 MySQL，仅依赖 pkg/redis。生成的图片以 base64 data URI 直接回前端。
package captcha

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/wenlng/go-captcha-assets/resources/imagesv2"
	"github.com/wenlng/go-captcha-assets/resources/tiles"
	"github.com/wenlng/go-captcha/v2/slide"

	"github.com/aiworkspace/backend/pkg/redis"
)

const (
	ansKeyPrefix  = "captcha:ans:"  // 拼图答案，按 captchaId
	passKeyPrefix = "captcha:pass:" // 校验通过后的一次性通行令牌
	ansTTL        = 2 * time.Minute // 答案有效期：用户需在此期间内完成拖动
	passTTL       = 5 * time.Minute // 通行令牌有效期：须在此期间内提交登录
	validatePad   = 8               // 横向校验容差（像素）
)

var (
	builder     slide.Builder
	builderOnce sync.Once
	builderErr  error
)

// answer 持久化到 Redis 的拼图正确落点。
type answer struct {
	X int `json:"x"`
	Y int `json:"y"`
}

// GenResult 是一次拼图生成的产物，masterImage/tileImage 为带前缀的 base64 data URI。
type GenResult struct {
	CaptchaID   string `json:"captchaId"`
	MasterImage string `json:"masterImage"`
	TileImage   string `json:"tileImage"`
	TileX       int    `json:"tileX"` // 拼图块初始横向位置（前端起拖原点）
	TileY       int    `json:"tileY"` // 拼图块纵向位置（固定，仅横向拖动）
	TileWidth   int    `json:"tileWidth"`
}

// getBuilder 懒加载并复用 slide.Builder（构建含解码内嵌素材，较重，进程内只做一次）。
func getBuilder() (slide.Builder, error) {
	builderOnce.Do(func() {
		bgImages, err := imagesv2.GetImages()
		if err != nil {
			builderErr = fmt.Errorf("load backgrounds: %w", err)
			return
		}
		tileGraphs, err := tiles.GetTiles()
		if err != nil {
			builderErr = fmt.Errorf("load tiles: %w", err)
			return
		}
		graphs := make([]*slide.GraphImage, 0, len(tileGraphs))
		for _, g := range tileGraphs {
			graphs = append(graphs, &slide.GraphImage{
				OverlayImage: g.OverlayImage,
				MaskImage:    g.MaskImage,
				ShadowImage:  g.ShadowImage,
			})
		}
		b := slide.NewBuilder()
		b.SetResources(
			slide.WithBackgrounds(bgImages),
			slide.WithGraphImages(graphs),
		)
		builder = b
	})
	return builder, builderErr
}

// ensureDataURI 给裸 base64 补上 data URI 前缀；若已带前缀则原样返回（兼容不同库版本）。
func ensureDataURI(b64, mime string) string {
	if strings.HasPrefix(b64, "data:") {
		return b64
	}
	return "data:" + mime + ";base64," + b64
}

// randHex 返回 n 字节的十六进制随机串，用作 captchaId / 通行令牌。
func randHex(n int) (string, error) {
	buf := make([]byte, n)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return hex.EncodeToString(buf), nil
}

// Generate 生成一张拼图，把答案写入 Redis，返回图片与元信息。
func Generate(ctx context.Context) (*GenResult, error) {
	b, err := getBuilder()
	if err != nil {
		return nil, err
	}
	capt := b.Make()
	data, err := capt.Generate()
	if err != nil {
		return nil, fmt.Errorf("generate slide captcha: %w", err)
	}
	block := data.GetData()
	if block == nil {
		return nil, fmt.Errorf("empty captcha block")
	}

	// 注意：go-captcha v2.0.5 的 ToBase64Data 返回裸 base64（不含 data URI scheme），
	// 需自行补前缀，否则前端 <img src> 无法识别（master 为 JPEG、tile 为 PNG）。
	masterB64, err := data.GetMasterImage().ToBase64Data()
	if err != nil {
		return nil, fmt.Errorf("master to base64: %w", err)
	}
	tileB64, err := data.GetTileImage().ToBase64Data()
	if err != nil {
		return nil, fmt.Errorf("tile to base64: %w", err)
	}
	masterURI := ensureDataURI(masterB64, "image/jpeg")
	tileURI := ensureDataURI(tileB64, "image/png")

	captchaID, err := randHex(16)
	if err != nil {
		return nil, err
	}
	ansBytes, _ := json.Marshal(answer{X: block.X, Y: block.Y})
	if err := redis.Client.Set(ctx, ansKeyPrefix+captchaID, ansBytes, ansTTL).Err(); err != nil {
		return nil, fmt.Errorf("store answer: %w", err)
	}

	return &GenResult{
		CaptchaID:   captchaID,
		MasterImage: masterURI,
		TileImage:   tileURI,
		TileX:       block.DX,
		TileY:       block.DY,
		TileWidth:   block.Width,
	}, nil
}

// Verify 校验拖动落点 srcX。通过则签发并返回一次性通行令牌；答案无论成败都被消费（防重放）。
func Verify(ctx context.Context, captchaID string, srcX int) (bool, string, error) {
	if captchaID == "" {
		return false, "", nil
	}
	// GetDel：取出即删除，答案一次性，乱拖不可复用同一题
	raw, err := redis.Client.GetDel(ctx, ansKeyPrefix+captchaID).Result()
	if err != nil {
		// key 不存在（过期/已用）或 Redis 异常，一律判失败
		return false, "", nil
	}
	var ans answer
	if json.Unmarshal([]byte(raw), &ans) != nil {
		return false, "", nil
	}
	// 仅横向校验：srcY 取答案 Y，纵向恒等（basic slide 拼图块只横向移动）
	if !slide.Validate(srcX, ans.Y, ans.X, ans.Y, validatePad) {
		return false, "", nil
	}

	token, err := randHex(16)
	if err != nil {
		return false, "", err
	}
	if err := redis.Client.Set(ctx, passKeyPrefix+token, "1", passTTL).Err(); err != nil {
		return false, "", fmt.Errorf("store pass token: %w", err)
	}
	return true, token, nil
}

// ConsumePassToken 在登录时消费一次性通行令牌：存在即删除并返回 true。
func ConsumePassToken(ctx context.Context, token string) bool {
	if token == "" {
		return false
	}
	n, err := redis.Client.Del(ctx, passKeyPrefix+token).Result()
	if err != nil {
		return false
	}
	return n > 0
}
