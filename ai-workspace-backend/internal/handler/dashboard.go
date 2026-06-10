package handler

import (
	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/middleware"
	"github.com/aiworkspace/backend/internal/service"
)

// DashboardStats GET /api/dashboard/stats — 返回当前用户的仪表盘统计数据
//
// 所有计数均按用户隔离，注意各表归属列不同：
//   - chat_session: user_id
//   - kb_knowledge_base / kb_document: create_by
//   - file_info: upload_by
func DashboardStats(c *gin.Context) {
	stats := service.DashboardSvc.GetStats(middleware.CurrentUserID(c))
	common.OK(c, stats)
}
