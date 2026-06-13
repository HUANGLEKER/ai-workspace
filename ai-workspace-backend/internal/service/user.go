package service

import (
	"errors"

	"golang.org/x/crypto/bcrypt"
	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
)

// UserSvc 是用户服务全局单例
var UserSvc *UserService

// UserService 依赖经构造函数注入（P1-1）
type UserService struct {
	db *gorm.DB
}

// NewUserService 构造服务
func NewUserService(db *gorm.DB) *UserService {
	return &UserService{db: db}
}

// PageUsers 分页查询用户列表，密码字段置空后返回，防止哈希泄露
func (s *UserService) PageUsers(pageNum, pageSize int, username string) (common.PageResult[model.SysUser], error) {
	var users []model.SysUser
	var total int64
	q := s.db.Model(&model.SysUser{})
	if username != "" {
		q = q.Where("username LIKE ?", "%"+username+"%")
	}
	if err := q.Count(&total).Error; err != nil {
		return common.PageResult[model.SysUser]{}, err
	}
	pg := common.PageQuery{PageNum: pageNum, PageSize: pageSize}
	pg.Normalize()
	if err := q.Offset(pg.Offset()).Limit(pg.PageSize).Find(&users).Error; err != nil {
		return common.PageResult[model.SysUser]{}, err
	}
	for i := range users {
		users[i].Password = ""
	}
	return common.PageResult[model.SysUser]{Total: total, PageNum: pg.PageNum, PageSize: pg.PageSize, List: users}, nil
}

// AddUser 新建用户，对用户名唯一性和密码强制 BCrypt 编码进行校验
func (s *UserService) AddUser(user *model.SysUser) error {
	var count int64
	s.db.Model(&model.SysUser{}).Where("username = ?", user.Username).Count(&count)
	if count > 0 {
		return common.NewBizError(common.CodeBadRequest, "用户名已存在")
	}
	if user.Password == "" {
		return common.NewBizError(common.CodeBadRequest, "密码不能为空")
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(user.Password), bcrypt.DefaultCost)
	if err != nil {
		return err
	}
	user.Password = string(hash)
	user.ID = 0
	if user.Status == 0 {
		user.Status = 1
	}
	return s.db.Create(user).Error
}

// UpdateUser 更新用户信息，保护用户名不可变，仅当传入新密码时才重新哈希
func (s *UserService) UpdateUser(user *model.SysUser) error {
	if user.ID == 0 {
		return common.NewBizError(common.CodeBadRequest, "用户ID不能为空")
	}
	var existing model.SysUser
	if err := s.db.First(&existing, user.ID).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return common.ErrNotFound("用户")
		}
		return err
	}
	user.Username = existing.Username
	// Save 会覆盖全部字段：必须回填创建时间，否则 create_time 会被写成零值
	user.CreatedAt = existing.CreatedAt
	// 状态变更走专用 /user/status 接口；请求未带 status（零值）时保留原状态，防止误禁用
	if user.Status == 0 {
		user.Status = existing.Status
	}
	if user.Password != "" {
		hash, err := bcrypt.GenerateFromPassword([]byte(user.Password), bcrypt.DefaultCost)
		if err != nil {
			return err
		}
		user.Password = string(hash)
	} else {
		user.Password = existing.Password
	}
	return s.db.Save(user).Error
}

// DeleteUser 按 ID 软删除用户
func (s *UserService) DeleteUser(id int64) error {
	return s.db.Delete(&model.SysUser{}, id).Error
}

// UpdateStatus 启用/禁用用户账号
func (s *UserService) UpdateStatus(id int64, status int8) error {
	return s.db.Model(&model.SysUser{}).Where("id = ?", id).Update("status", status).Error
}

// GetByID 按 ID 查询用户，不返回密码字段
func (s *UserService) GetByID(id int64) (*model.SysUser, error) {
	var user model.SysUser
	if err := s.db.First(&user, id).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, common.ErrNotFound("用户")
		}
		return nil, err
	}
	user.Password = ""
	return &user, nil
}

// UpdatePassword 允许用户修改自己的密码
func (s *UserService) UpdatePassword(userID int64, oldPassword, newPassword string) error {
	var user model.SysUser
	if err := s.db.First(&user, userID).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return common.ErrNotFound("用户")
		}
		return err
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(oldPassword)); err != nil {
		return common.NewBizError(common.CodeBadRequest, "原密码错误")
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(newPassword), bcrypt.DefaultCost)
	if err != nil {
		return err
	}

	// 改密后清除强制改密标记（P3-6）
	return s.db.Model(&user).Updates(map[string]any{"password": string(hash), "must_change_pwd": 0}).Error
}

// GetRoles 查询用户的角色码列表，用于 JWT 生成和权限校验
func (s *UserService) GetRoles(userID int64) []string {
	var roles []string
	s.db.Model(&model.SysRole{}).
		Joins("JOIN sys_user_role ur ON ur.role_id = sys_role.id").
		Where("ur.user_id = ?", userID).
		Pluck("role_code", &roles)
	return roles
}
