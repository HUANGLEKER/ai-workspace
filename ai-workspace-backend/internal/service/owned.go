package service

import (
	"errors"

	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
)

// 本文件是用户私有资源归属过滤的强制入口（P1-2）。
//
// 约定：service 层一律通过 getOwnedResource / ownedScope 做归属校验与过滤，
// 禁止手写 Where("create_by = ?") 等字面量条件——三套归属列名
// （create_by / user_id / upload_by）由 model.Owned 接口收口，
// 新模块只要嵌入 UserOwnedModel（或实现 Owned）即自动获得正确的列。

// getOwnedResource 按主键取记录并校验归属：不存在返回 404 业务错误，
// 非本人返回 403。这是所有 get/update/delete 前置校验的统一实现。
func getOwnedResource[T model.Owned](db *gorm.DB, id, userID int64, resourceName string) (*T, error) {
	var obj T
	err := db.First(&obj, id).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, common.ErrNotFound(resourceName)
	}
	if err != nil {
		return nil, err
	}
	if obj.OwnerID() != userID {
		return nil, common.ErrForbidden()
	}
	return &obj, nil
}

// ownedScope 返回按归属列过滤当前用户记录的 GORM scope，供列表/统计查询使用：
//
//	db.Scopes(ownedScope[model.Agent](userID)).Find(&agents)
func ownedScope[T model.Owned](userID int64) func(*gorm.DB) *gorm.DB {
	var t T
	col := t.OwnerColumn()
	return func(db *gorm.DB) *gorm.DB {
		return db.Where(col+" = ?", userID)
	}
}
