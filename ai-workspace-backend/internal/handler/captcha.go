package handler

import (
	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/captcha"
	"github.com/aiworkspace/backend/internal/common"
)

// InitSlideCaptcha GET /api/auth/captcha/slide/init — 生成一张滑块拼图（公开，登录前调用）。
func InitSlideCaptcha(c *gin.Context) {
	res, err := captcha.Generate(c.Request.Context())
	if err != nil {
		common.ServerError(c, "生成验证码失败")
		return
	}
	common.OK(c, res)
}

type verifySlideReq struct {
	CaptchaID string `json:"captchaId" binding:"required"`
	X         int    `json:"x"`
}

// VerifySlideCaptcha POST /api/auth/captcha/slide/verify — 校验拖动落点，通过则签发一次性通行令牌。
// 始终返回 HTTP 200，前端据 data.success 判定；失败不暴露正确坐标。
func VerifySlideCaptcha(c *gin.Context) {
	var req verifySlideReq
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, "参数错误: "+err.Error())
		return
	}
	ok, token, err := captcha.Verify(c.Request.Context(), req.CaptchaID, req.X)
	if err != nil {
		common.ServerError(c, "校验验证码失败")
		return
	}
	if !ok {
		common.OK(c, gin.H{"success": false})
		return
	}
	common.OK(c, gin.H{"success": true, "captchaToken": token})
}
