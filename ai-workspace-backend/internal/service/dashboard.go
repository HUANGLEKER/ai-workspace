package service

import (
	"time"

	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/pkg/database"
)

// DashboardSvc 是仪表盘统计服务全局单例
var DashboardSvc = &dashboardService{}

type dashboardService struct{}

// DashboardStats 仪表盘统计视图，所有数字均按用户隔离
type DashboardStats struct {
	TodaySessions int64 `json:"todaySessions"` // 今日新建会话数
	KbCount       int64 `json:"kbCount"`       // 知识库总数
	DocCount      int64 `json:"docCount"`      // 文档总数
	FileCount     int64 `json:"fileCount"`     // 文件总数
}

// GetStats 聚合当前用户的仪表盘统计数据
// 注意各表归属列不同：chat 用 user_id，KB/文档用 create_by，文件用 upload_by
func (s *dashboardService) GetStats(userID int64) DashboardStats {
	startOfToday := time.Now().Truncate(24 * time.Hour)

	var todaySessions, kbCount, fileCount int64
	database.DB.Model(&model.ChatSession{}).
		Where("user_id = ? AND create_time >= ?", userID, startOfToday).
		Count(&todaySessions)

	database.DB.Model(&model.KbKnowledgeBase{}).
		Where("create_by = ?", userID).
		Count(&kbCount)

	// 文档不直接挂用户，经由用户名下的知识库 ID 集合间接统计
	var kbIDs []int64
	database.DB.Model(&model.KbKnowledgeBase{}).
		Where("create_by = ?", userID).
		Pluck("id", &kbIDs)
	var docCount int64
	if len(kbIDs) > 0 {
		database.DB.Model(&model.KbDocument{}).Where("kb_id IN ?", kbIDs).Count(&docCount)
	}

	// 文件归属列为 upload_by，区别于 KB 的 create_by
	database.DB.Model(&model.FileInfo{}).
		Where("upload_by = ?", userID).
		Count(&fileCount)

	return DashboardStats{
		TodaySessions: todaySessions,
		KbCount:       kbCount,
		DocCount:      docCount,
		FileCount:     fileCount,
	}
}
