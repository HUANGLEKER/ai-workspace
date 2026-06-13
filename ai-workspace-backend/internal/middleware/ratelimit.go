package middleware

import (
	"fmt"
	"time"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/config"
	"github.com/aiworkspace/backend/pkg/logger"
	"github.com/aiworkspace/backend/pkg/redis"
)

// LLMRateLimit 基于 Redis 固定窗口（每分钟）限流，仅挂在直通 LLM 的高成本端点上。
// key 按「用户 + 路由模板」隔离，限额来自 config.yaml 的 ratelimit.llm_per_minute（<=0 表示关闭）。
// Redis 不可用时 fail-open 放行：限流是成本保护，不能成为 chat 的可用性单点。
func LLMRateLimit() gin.HandlerFunc {
	return func(c *gin.Context) {
		// 限流分级（P3-6）：管理员用更宽松的限额（未配则回退普通限额）
		limit := config.Global.RateLimit.LLMPerMinute
		if IsAdmin(c) {
			if adm := config.Global.RateLimit.LLMPerMinuteAdmin; adm > 0 {
				limit = adm
			}
		}
		if limit <= 0 {
			c.Next()
			return
		}

		// FullPath 取路由模板（如 /api/agent/:id/run），避免按具体 id 拆散计数
		key := fmt.Sprintf("rl:%d:%s:%d",
			CurrentUserID(c), c.FullPath(), time.Now().Unix()/60)

		ctx := c.Request.Context()
		count, err := redis.Client.Incr(ctx, key).Result()
		if err != nil {
			logger.Log.Warn("ratelimit redis unavailable, fail-open", zap.Error(err))
			c.Next()
			return
		}
		if count == 1 {
			// 窗口首次计数时设置过期；多留 5s 余量防止边界上 key 提前消失
			redis.Client.Expire(ctx, key, 65*time.Second)
		}
		if count > int64(limit) {
			common.Fail(c, common.CodeTooManyRequests, "请求过于频繁，请稍后再试")
			c.Abort()
			return
		}
		c.Next()
	}
}
