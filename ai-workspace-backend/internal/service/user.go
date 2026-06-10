package service

import (
	"errors"

	"golang.org/x/crypto/bcrypt"
	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/pkg/database"
)

// UserSvc 是用户服务全局单例
var UserSvc = &userService{}

type userService struct{}

// PageUsers 分页查询用户列表，密码字段置空后返回，防止哈希泄露
func (s *userService) PageUsers(pageNum, pageSize int, username string) (common.PageResult[model.SysUser], error) {
	var users []model.SysUser
	var total int64
	q := database.DB.Model(&model.SysUser{})
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
func (s *userService) AddUser(user *model.SysUser) error {
	var count int64
	database.DB.Model(&model.SysUser{}).Where("username = ?", user.Username).Count(&count)
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
	return database.DB.Create(user).Error
}

// UpdateUser 更新用户信息，保护用户名不可变，仅当传入新密码时才重新哈希
func (s *userService) UpdateUser(user *model.SysUser) error {
	if user.ID == 0 {
		return common.NewBizError(common.CodeBadRequest, "用户ID不能为空")
	}
	var existing model.SysUser
	if err := database.DB.First(&existing, user.ID).Error; err != nil {
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
	return database.DB.Save(user).Error
}

// DeleteUser 按 ID 软删除用户
func (s *userService) DeleteUser(id int64) error {
	return database.DB.Delete(&model.SysUser{}, id).Error
}

// UpdateStatus 启用/禁用用户账号
func (s *userService) UpdateStatus(id int64, status int8) error {
	return database.DB.Model(&model.SysUser{}).Where("id = ?", id).Update("status", status).Error
}

// GetByID 按 ID 查询用户，不返回密码字段
func (s *userService) GetByID(id int64) (*model.SysUser, error) {
	var user model.SysUser
	if err := database.DB.First(&user, id).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, common.ErrNotFound("用户")
		}
		return nil, err
	}
	user.Password = ""
	return &user, nil
}

// GetRoles 查询用户的角色码列表，用于 JWT 生成和权限校验
func (s *userService) GetRoles(userID int64) []string {
	var roles []string
	database.DB.Model(&model.SysRole{}).
		Joins("JOIN sys_user_role ur ON ur.role_id = sys_role.id").
		Where("ur.user_id = ?", userID).
		Pluck("role_code", &roles)
	return roles
}
