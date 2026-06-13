package service

import (
	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/repository"
)

// 本文件是用户私有资源归属过滤的强制入口（P1-2）。
//
// 归属语义的唯一实现已下沉到 repository 层（internal/repository/owned.go）；这里保留
// 两个薄封装，供尚未持有 repository 的复杂 service（kb/chat/file/user 等）过渡复用。
//
// 约定：service 层一律通过 getOwnedResource / ownedScope（或 repository.OwnedRepository）
// 做归属校验与过滤，禁止手写 Where("create_by = ?") 等字面量条件——三套归属列名
// （create_by / user_id / upload_by）由 model.Owned 接口收口，新模块只要嵌入
// UserOwnedModel（或实现 Owned）即自动获得正确的列。

// getOwnedResource 按主键取记录并校验归属：不存在返回 404，非本人返回 403。
func getOwnedResource[T model.Owned](db *gorm.DB, id, userID int64, resourceName string) (*T, error) {
	return repository.NewOwnedRepository[T](db).FindOwned(id, userID, resourceName)
}

// ownedScope 返回按归属列过滤当前用户记录的 GORM scope，供列表/统计查询使用。
func ownedScope[T model.Owned](userID int64) func(*gorm.DB) *gorm.DB {
	return repository.OwnedScope[T](userID)
}
