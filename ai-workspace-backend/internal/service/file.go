package service

import (
	"context"
	"fmt"
	"path/filepath"
	"strings"
	"time"

	"go.uber.org/zap"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/pkg/database"
	minioPkg "github.com/aiworkspace/backend/pkg/minio"
)

// FileSvc 是文件服务全局单例
var FileSvc = &fileService{}

type fileService struct{}

// Upload 上传文件至 MinIO 并在 file_info 表中记录元数据
// 对象路径格式：files/{userID}/{timestamp}.{ext}，按用户分目录存储
func (s *fileService) Upload(ctx context.Context, userID int64, fileName string, data []byte, contentType string) (*model.FileInfo, error) {
	ext := filepath.Ext(fileName)
	if ext == "" {
		ext = guessExt(contentType)
	}
	objectName := fmt.Sprintf("files/%d/%d%s", userID, time.Now().UnixNano(), ext)

	if err := minioPkg.Upload(ctx, objectName, strings.NewReader(string(data)), int64(len(data)), contentType); err != nil {
		return nil, err
	}

	info := &model.FileInfo{
		FileName: fileName,
		FilePath: objectName,
		FileSize: int64(len(data)),
		FileType: contentType,
		UploadBy: userID,
	}
	if err := database.DB.Create(info).Error; err != nil {
		// DB 写入失败时尝试回滚 MinIO 对象，失败则仅记录日志（幂等清理）
		if delErr := minioPkg.Delete(ctx, objectName); delErr != nil {
			zap.L().Warn("回滚 MinIO 对象失败", zap.String("path", objectName), zap.Error(delErr))
		}
		return nil, err
	}
	return info, nil
}

// Delete 校验 uploadBy 归属后删除文件（MinIO 对象 + DB 记录）
// 注意归属列为 upload_by，区别于 KB 的 create_by，混用会导致越权或 IDOR
func (s *fileService) Delete(ctx context.Context, id, userID int64) error {
	info, err := s.getOwned(id, userID)
	if err != nil {
		return err
	}
	if err = minioPkg.Delete(ctx, info.FilePath); err != nil {
		zap.L().Warn("删除 MinIO 对象失败", zap.String("path", info.FilePath), zap.Error(err))
	}
	return database.DB.Delete(&model.FileInfo{}, id).Error
}

// GetPresignedURL 生成临时预签名下载 URL（有效期 1 小时）
// 校验 upload_by + file_path 双重条件，防止通过构造 filePath 枚举他人文件
func (s *fileService) GetPresignedURL(ctx context.Context, fileID, userID int64) (string, error) {
	info, err := s.getOwned(fileID, userID)
	if err != nil {
		return "", err
	}
	return minioPkg.PresignedURL(ctx, info.FilePath, time.Hour)
}

// PageList 分页查询当前用户的文件列表，按 upload_by 隔离
func (s *fileService) PageList(userID int64, pageNum, pageSize int, fileName string) (common.PageResult[model.FileInfo], error) {
	var files []model.FileInfo
	var total int64
	q := database.DB.Model(&model.FileInfo{}).Scopes(ownedScope[model.FileInfo](userID))
	if fileName != "" {
		q = q.Where("file_name LIKE ?", "%"+fileName+"%")
	}
	if err := q.Count(&total).Error; err != nil {
		return common.PageResult[model.FileInfo]{}, err
	}
	pg := common.PageQuery{PageNum: pageNum, PageSize: pageSize}
	pg.Normalize()
	if err := q.Offset(pg.Offset()).Limit(pg.PageSize).Order("create_time DESC").Find(&files).Error; err != nil {
		return common.PageResult[model.FileInfo]{}, err
	}
	return common.PageResult[model.FileInfo]{Total: total, PageNum: pg.PageNum, PageSize: pg.PageSize, List: files}, nil
}

// getOwned 按 upload_by 校验文件归属，防止 IDOR
func (s *fileService) getOwned(id, userID int64) (*model.FileInfo, error) {
	return getOwnedResource[model.FileInfo](database.DB, id, userID, "文件")
}

// guessExt 根据 MIME 类型猜测文件扩展名（兜底逻辑）
func guessExt(mime string) string {
	m := map[string]string{
		"image/jpeg":      ".jpg",
		"image/png":       ".png",
		"image/gif":       ".gif",
		"application/pdf": ".pdf",
		"text/plain":      ".txt",
	}
	if ext, ok := m[mime]; ok {
		return ext
	}
	return ""
}
