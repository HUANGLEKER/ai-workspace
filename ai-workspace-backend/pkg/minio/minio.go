// Package minio 封装 MinIO 对象存储客户端，提供文件上传、删除与预签名 URL 生成能力。
// 对应 Spring Boot 的 workspace-file/workspace-kb 中的 MinioConfig + MinIO SDK 调用。
package minio

import (
	"context"
	"fmt"
	"io"
	"net/url"
	"time"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"

	"github.com/aiworkspace/backend/internal/config"
)

// Client 是全局 MinIO 客户端单例，pkg 初始化后即可直接使用
var Client *minio.Client

// bucket 存储当前配置的桶名，避免每次调用都读 config
var bucket string

// Init 初始化 MinIO 客户端并确保目标桶已存在。
// 首次部署时若桶不存在会自动创建（MakeBucket），已存在则跳过。
func Init(cfg config.MinIOConfig) error {
	c, err := minio.New(cfg.Endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(cfg.AccessKey, cfg.SecretKey, ""),
		Secure: cfg.UseSSL,
	})
	if err != nil {
		return fmt.Errorf("创建 MinIO 客户端失败: %w", err)
	}

	ctx := context.Background()
	exists, err := c.BucketExists(ctx, cfg.Bucket)
	if err != nil {
		return fmt.Errorf("检查桶是否存在失败: %w", err)
	}
	if !exists {
		if err = c.MakeBucket(ctx, cfg.Bucket, minio.MakeBucketOptions{}); err != nil {
			return fmt.Errorf("创建桶 %s 失败: %w", cfg.Bucket, err)
		}
	}

	Client = c
	bucket = cfg.Bucket
	return nil
}

// Upload 将文件流上传到 MinIO 的指定对象路径。
// objectName 为 MinIO 内的完整路径，如 "files/uuid.pdf" 或 "kb/1/uuid.pdf"。
func Upload(ctx context.Context, objectName string, reader io.Reader, size int64, contentType string) error {
	_, err := Client.PutObject(ctx, bucket, objectName, reader, size, minio.PutObjectOptions{
		ContentType: contentType,
	})
	if err != nil {
		return fmt.Errorf("上传对象 %s 失败: %w", objectName, err)
	}
	return nil
}

// Delete 从 MinIO 删除指定对象。
// 对象不存在时 MinIO SDK 不会返回错误，符合幂等删除的预期行为。
func Delete(ctx context.Context, objectName string) error {
	err := Client.RemoveObject(ctx, bucket, objectName, minio.RemoveObjectOptions{})
	if err != nil {
		return fmt.Errorf("删除对象 %s 失败: %w", objectName, err)
	}
	return nil
}

// PresignedURL 生成具有时效性的预签名下载 URL，默认有效期 1 小时。
// 持有 URL 的任何人无需额外鉴权即可下载，调用方需在业务层校验归属后再生成。
func PresignedURL(ctx context.Context, objectName string, expiry time.Duration) (string, error) {
	u, err := Client.PresignedGetObject(ctx, bucket, objectName, expiry, url.Values{})
	if err != nil {
		return "", fmt.Errorf("生成预签名 URL 失败: %w", err)
	}
	return u.String(), nil
}
