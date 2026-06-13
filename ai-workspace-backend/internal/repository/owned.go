// Package repository 是数据访问层（DAO）：把 GORM 持久化操作从 service 中剥离出来，
// 让业务逻辑面向接口而非 *gorm.DB，从而可在不依赖真实数据库的情况下对 service 做单元测试
// （以 fake repository 替换），同时把用户私有资源的归属语义（create_by/user_id/upload_by）
// 统一收口在一处。
//
// 归属列由 model.Owned 接口声明（见 internal/model/owned.go），新模块嵌入 UserOwnedModel
// 即自动获得正确的列，无需在 repository / service 层手写字面量归属条件。
package repository

import (
	"errors"

	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
)

// OwnedRepository 是用户私有资源的通用持久化接口（CRUD + 归属校验）。
// service 持有该接口而非 *gorm.DB，便于以 fake 实现做无数据库单测。
type OwnedRepository[T model.Owned] interface {
	// FindOwned 按主键取记录并校验归属：不存在返回 404，非本人返回 403。
	FindOwned(id, userID int64, resourceName string) (*T, error)
	// FindByID 按主键取记录但不校验归属（用于仅展示等场景），不存在返回 404。
	FindByID(id int64, resourceName string) (*T, error)
	// List 按归属过滤当前用户的记录；build 可选，用于追加关键字/排序等查询条件。
	List(userID int64, build func(*gorm.DB) *gorm.DB) ([]T, error)
	// Create 插入新记录。
	Create(entity *T) error
	// Save 全字段覆盖更新。
	Save(entity *T) error
	// DeleteByID 按主键删除（GORM 软删除）。
	DeleteByID(id int64) error
	// DB 暴露底层 *gorm.DB，供尚未迁移的复杂查询过渡使用；新代码应优先用上述方法。
	DB() *gorm.DB
}

// gormOwnedRepo 是 OwnedRepository 的 GORM 实现。
type gormOwnedRepo[T model.Owned] struct {
	db *gorm.DB
}

// NewOwnedRepository 基于给定 *gorm.DB 构造仓储。
func NewOwnedRepository[T model.Owned](db *gorm.DB) OwnedRepository[T] {
	return &gormOwnedRepo[T]{db: db}
}

func (r *gormOwnedRepo[T]) FindOwned(id, userID int64, resourceName string) (*T, error) {
	obj, err := r.FindByID(id, resourceName)
	if err != nil {
		return nil, err
	}
	if (*obj).OwnerID() != userID {
		return nil, common.ErrForbidden()
	}
	return obj, nil
}

func (r *gormOwnedRepo[T]) FindByID(id int64, resourceName string) (*T, error) {
	var obj T
	err := r.db.First(&obj, id).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, common.ErrNotFound(resourceName)
	}
	if err != nil {
		return nil, err
	}
	return &obj, nil
}

func (r *gormOwnedRepo[T]) List(userID int64, build func(*gorm.DB) *gorm.DB) ([]T, error) {
	var list []T
	q := r.db.Scopes(OwnedScope[T](userID))
	if build != nil {
		q = build(q)
	}
	err := q.Find(&list).Error
	return list, err
}

func (r *gormOwnedRepo[T]) Create(entity *T) error { return r.db.Create(entity).Error }

func (r *gormOwnedRepo[T]) Save(entity *T) error { return r.db.Save(entity).Error }

func (r *gormOwnedRepo[T]) DeleteByID(id int64) error {
	var zero T
	return r.db.Delete(&zero, id).Error
}

func (r *gormOwnedRepo[T]) DB() *gorm.DB { return r.db }

// OwnedScope 返回按归属列过滤当前用户记录的 GORM scope，供列表/统计查询复用。
func OwnedScope[T model.Owned](userID int64) func(*gorm.DB) *gorm.DB {
	var t T
	col := t.OwnerColumn()
	return func(db *gorm.DB) *gorm.DB {
		return db.Where(col+" = ?", userID)
	}
}
