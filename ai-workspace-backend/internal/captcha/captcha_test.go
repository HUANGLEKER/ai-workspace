package captcha

import (
	"testing"

	"github.com/wenlng/go-captcha/v2/slide"
)

// TestBuilderGenerates 验证 go-captcha 内嵌素材能正常加载并生成有效拼图（不依赖 Redis）。
func TestBuilderGenerates(t *testing.T) {
	b, err := getBuilder()
	if err != nil {
		t.Fatalf("getBuilder: %v", err)
	}
	capt := b.Make()
	captData, err := capt.Generate()
	if err != nil {
		t.Fatalf("generate: %v", err)
	}
	block := captData.GetData()
	if block == nil {
		t.Fatal("nil block")
	}
	if block.Width <= 0 || block.X <= 0 {
		t.Fatalf("unexpected block: width=%d x=%d", block.Width, block.X)
	}
	if s, err := captData.GetMasterImage().ToBase64Data(); err != nil || len(s) == 0 {
		t.Fatalf("master base64: %v len=%d", err, len(s))
	}
	if s, err := captData.GetTileImage().ToBase64Data(); err != nil || len(s) == 0 {
		t.Fatalf("tile base64: %v len=%d", err, len(s))
	}
}

// TestEnsureDataURI 锁定前端可直接用作 <img src> 的 data URI 拼装：
// go-captcha 的 ToBase64Data 返回裸 base64，必须补前缀，且对已带前缀的输入幂等。
func TestEnsureDataURI(t *testing.T) {
	got := ensureDataURI("/9j/2wCEAAEB", "image/jpeg")
	if got != "data:image/jpeg;base64,/9j/2wCEAAEB" {
		t.Fatalf("raw base64 not prefixed: %q", got)
	}
	already := "data:image/png;base64,iVBORw0K"
	if ensureDataURI(already, "image/png") != already {
		t.Fatal("prefixed input should be returned unchanged")
	}
}

// TestValidateSemantics 锁定坐标语义：落点等于答案 X 时通过、偏离超过容差时失败。
// 这正是前端上报 reportX 后端校验的判定逻辑（仅横向，srcY 取答案 Y）。
func TestValidateSemantics(t *testing.T) {
	const x, y = 150, 40
	if !slide.Validate(x, y, x, y, validatePad) {
		t.Fatal("exact match should pass")
	}
	if !slide.Validate(x+validatePad, y, x, y, validatePad) {
		t.Fatal("within padding should pass")
	}
	if slide.Validate(x+validatePad+5, y, x, y, validatePad) {
		t.Fatal("beyond padding should fail")
	}
}
