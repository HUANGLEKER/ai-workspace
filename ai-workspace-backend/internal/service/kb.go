package service

import (
	"context"
	"errors"
	"fmt"
	"path/filepath"
	"strings"
	"time"

	"go.uber.org/zap"
	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/pkg/database"
	"github.com/aiworkspace/backend/pkg/fastapi"
	minioPkg "github.com/aiworkspace/backend/pkg/minio"
)

// KBSvc 是知识库服务全局单例
var KBSvc = &kbService{}

type kbService struct{}

// ListByUser 查询当前用户拥有的所有知识库
func (s *kbService) ListByUser(userID int64) ([]model.KbKnowledgeBase, error) {
	var kbs []model.KbKnowledgeBase
	err := database.DB.Where("create_by = ?", userID).Order("create_time DESC").Find(&kbs).Error
	return kbs, err
}

// Create 新建知识库，强制 CreateBy 为当前用户
func (s *kbService) Create(kb *model.KbKnowledgeBase, userID int64) error {
	if kb.KbName == "" {
		return common.NewBizError(common.CodeBadRequest, "知识库名称不能为空")
	}
	kb.ID = 0
	kb.CreateBy = userID
	return database.DB.Create(kb).Error
}

// Update 更新知识库，回填 CreateBy 防止归属被篡改
func (s *kbService) Update(kb *model.KbKnowledgeBase, userID int64) error {
	existing, err := s.GetOwned(kb.ID, userID)
	if err != nil {
		return err
	}
	kb.CreateBy = existing.CreateBy
	return database.DB.Save(kb).Error
}

// Delete 校验归属后删除知识库
func (s *kbService) Delete(id, userID int64) error {
	if _, err := s.GetOwned(id, userID); err != nil {
		return err
	}
	return database.DB.Delete(&model.KbKnowledgeBase{}, id).Error
}

// GetOwned 校验知识库归属，防止 IDOR 越权
func (s *kbService) GetOwned(id, userID int64) (*model.KbKnowledgeBase, error) {
	var kb model.KbKnowledgeBase
	err := database.DB.First(&kb, id).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, common.ErrNotFound("知识库")
	}
	if err != nil {
		return nil, err
	}
	if kb.CreateBy != userID {
		return nil, common.ErrForbidden()
	}
	return &kb, nil
}

// PageDocuments 分页查询知识库下的文档，先验证调用者对该知识库的归属权
func (s *kbService) PageDocuments(kbID, userID int64, pageNum, pageSize int) (common.PageResult[model.KbDocument], error) {
	if _, err := s.GetOwned(kbID, userID); err != nil {
		return common.PageResult[model.KbDocument]{}, err
	}
	var docs []model.KbDocument
	var total int64
	q := database.DB.Model(&model.KbDocument{}).Where("kb_id = ?", kbID)
	if err := q.Count(&total).Error; err != nil {
		return common.PageResult[model.KbDocument]{}, err
	}
	pg := common.PageQuery{PageNum: pageNum, PageSize: pageSize}
	pg.Normalize()
	if err := q.Offset(pg.Offset()).Limit(pg.PageSize).Order("create_time DESC").Find(&docs).Error; err != nil {
		return common.PageResult[model.KbDocument]{}, err
	}
	return common.PageResult[model.KbDocument]{Total: total, PageNum: pg.PageNum, PageSize: pg.PageSize, List: docs}, nil
}

// UploadDocument 上传文档至 MinIO 并触发异步嵌入管道
// 对象路径格式：kb/{kbId}/{timestamp}.{ext}，与 Spring Boot 侧保持一致
func (s *kbService) UploadDocument(ctx context.Context, kbID, userID int64, fileName string, data []byte, contentType string) (*model.KbDocument, error) {
	if _, err := s.GetOwned(kbID, userID); err != nil {
		return nil, err
	}

	ext := filepath.Ext(fileName)
	objectName := fmt.Sprintf("kb/%d/%d%s", kbID, time.Now().UnixNano(), ext)

	if err := minioPkg.Upload(ctx, objectName, strings.NewReader(string(data)), int64(len(data)), contentType); err != nil {
		return nil, err
	}

	doc := &model.KbDocument{
		KbID:     kbID,
		FileName: fileName,
		FilePath: objectName,
		FileSize: int64(len(data)),
		FileType: contentType,
		Status:   model.DocStatusPending,
	}
	if err := database.DB.Create(doc).Error; err != nil {
		return nil, err
	}

	// 异步触发嵌入，不阻塞上传响应
	go s.buildEmbedding(doc)
	return doc, nil
}

// DeleteDocument 删除文档：校验归属 → MinIO 清理 → ChromaDB 向量删除 → DB 软删除
func (s *kbService) DeleteDocument(ctx context.Context, docID, userID int64) error {
	var doc model.KbDocument
	if err := database.DB.First(&doc, docID).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return common.ErrNotFound("文档")
		}
		return err
	}
	if _, err := s.GetOwned(doc.KbID, userID); err != nil {
		return err
	}

	if err := minioPkg.Delete(ctx, doc.FilePath); err != nil {
		zap.L().Warn("删除 MinIO 对象失败", zap.String("path", doc.FilePath), zap.Error(err))
	}

	body := map[string]any{"document_id": docID, "kb_id": doc.KbID}
	if err := fastapi.Client.Send(ctx, "/embedding/delete", body, 30*time.Second); err != nil {
		zap.L().Warn("删除向量数据失败", zap.Int64("docId", docID), zap.Error(err))
	}

	return database.DB.Delete(&model.KbDocument{}, docID).Error
}

// RebuildKB 对知识库下所有文档重新触发嵌入，切换嵌入模型后调用
func (s *kbService) RebuildKB(kbID, userID int64) error {
	if _, err := s.GetOwned(kbID, userID); err != nil {
		return err
	}
	var docs []model.KbDocument
	if err := database.DB.Where("kb_id = ?", kbID).Find(&docs).Error; err != nil {
		return err
	}
	for _, doc := range docs {
		d := doc
		go s.buildEmbedding(&d)
	}
	return nil
}

// buildEmbedding 执行单文档嵌入流程：写任务审计记录 → 置 PROCESSING → POST FastAPI → 更新状态
// 对应 Spring Boot EmbeddingServiceImpl.buildAsync()，此处以 goroutine 调用实现等价的异步行为
func (s *kbService) buildEmbedding(doc *model.KbDocument) {
	task := &model.KbChunkTask{
		DocumentID: doc.ID,
		TaskStatus: model.TaskStatusRunning,
	}
	database.DB.Create(task)

	database.DB.Model(&model.KbDocument{}).Where("id = ?", doc.ID).Update("status", model.DocStatusProcessing)

	body := map[string]any{
		"document_id": doc.ID,
		"kb_id":       doc.KbID,
		"file_path":   doc.FilePath,
		"file_name":   doc.FileName,
	}

	// 嵌入操作可能耗时较长，给 5 分钟超时
	err := fastapi.Client.Send(context.Background(), "/embedding/build", body, 5*time.Minute)
	if err != nil {
		zap.L().Error("嵌入失败", zap.Int64("docId", doc.ID), zap.Error(err))
		database.DB.Model(&model.KbDocument{}).Where("id = ?", doc.ID).Update("status", model.DocStatusFailed)
		database.DB.Model(&model.KbChunkTask{}).Where("id = ?", task.ID).
			Updates(map[string]any{"task_status": model.TaskStatusFailed, "error_msg": err.Error()})
		return
	}

	database.DB.Model(&model.KbDocument{}).Where("id = ?", doc.ID).Update("status", model.DocStatusDone)
	database.DB.Model(&model.KbChunkTask{}).Where("id = ?", task.ID).Update("task_status", model.TaskStatusSuccess)
	zap.L().Info("嵌入完成", zap.Int64("docId", doc.ID))
}
