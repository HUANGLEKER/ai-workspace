package service

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"go.uber.org/zap"
	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
)

// KBSvc 是知识库服务单例，由 service.Init() 在基础设施就绪后组装
var KBSvc *KBService

// KBService 知识库业务逻辑；db/FastAPI/对象存储均经构造函数注入，便于单测替换
type KBService struct {
	db    *gorm.DB
	ai    EmbeddingCaller
	store ObjectStore
}

// NewKBService 构造知识库服务
func NewKBService(db *gorm.DB, ai EmbeddingCaller, store ObjectStore) *KBService {
	return &KBService{db: db, ai: ai, store: store}
}

// ListByUser 查询当前用户拥有的所有知识库
func (s *KBService) ListByUser(userID int64) ([]model.KbKnowledgeBase, error) {
	var kbs []model.KbKnowledgeBase
	err := s.db.Scopes(ownedScope[model.KbKnowledgeBase](userID)).Order("create_time DESC").Find(&kbs).Error
	return kbs, err
}

// Create 新建知识库，强制 CreateBy 为当前用户
func (s *KBService) Create(kb *model.KbKnowledgeBase, userID int64) error {
	if kb.KbName == "" {
		return common.NewBizError(common.CodeBadRequest, "知识库名称不能为空")
	}
	kb.ID = 0
	kb.CreateBy = userID
	return s.db.Create(kb).Error
}

// Update 更新知识库，回填 CreateBy 防止归属被篡改
func (s *KBService) Update(kb *model.KbKnowledgeBase, userID int64) error {
	existing, err := s.GetOwned(kb.ID, userID)
	if err != nil {
		return err
	}
	kb.CreateBy = existing.CreateBy
	// Save 全字段覆盖，回填创建时间防止 create_time 被写成零值
	kb.CreatedAt = existing.CreatedAt
	return s.db.Save(kb).Error
}

// Delete 校验归属后删除知识库
func (s *KBService) Delete(id, userID int64) error {
	if _, err := s.GetOwned(id, userID); err != nil {
		return err
	}
	return s.db.Delete(&model.KbKnowledgeBase{}, id).Error
}

// GetOwned 校验知识库归属，防止 IDOR 越权
func (s *KBService) GetOwned(id, userID int64) (*model.KbKnowledgeBase, error) {
	return getOwnedResource[model.KbKnowledgeBase](s.db, id, userID, "知识库")
}

// PageDocuments 分页查询知识库下的文档，先验证调用者对该知识库的归属权
func (s *KBService) PageDocuments(kbID, userID int64, pageNum, pageSize int) (common.PageResult[model.KbDocument], error) {
	if _, err := s.GetOwned(kbID, userID); err != nil {
		return common.PageResult[model.KbDocument]{}, err
	}
	var docs []model.KbDocument
	var total int64
	q := s.db.Model(&model.KbDocument{}).Where("kb_id = ?", kbID)
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
func (s *KBService) UploadDocument(ctx context.Context, kbID, userID int64, fileName string, data []byte, contentType string) (*model.KbDocument, error) {
	if _, err := s.GetOwned(kbID, userID); err != nil {
		return nil, err
	}

	ext := filepath.Ext(fileName)
	objectName := fmt.Sprintf("kb/%d/%d%s", kbID, time.Now().UnixNano(), ext)

	if err := s.store.Upload(ctx, objectName, strings.NewReader(string(data)), int64(len(data)), contentType); err != nil {
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
	if err := s.db.Create(doc).Error; err != nil {
		return nil, err
	}

	// 异步触发嵌入，不阻塞上传响应
	go s.buildEmbedding(doc)
	return doc, nil
}

// DeleteDocument 删除文档：校验归属 → MinIO 清理 → ChromaDB 向量删除 → DB 软删除
func (s *KBService) DeleteDocument(ctx context.Context, docID, userID int64) error {
	var doc model.KbDocument
	if err := s.db.First(&doc, docID).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return common.ErrNotFound("文档")
		}
		return err
	}
	if _, err := s.GetOwned(doc.KbID, userID); err != nil {
		return err
	}

	if err := s.store.Delete(ctx, doc.FilePath); err != nil {
		zap.L().Warn("删除 MinIO 对象失败", zap.String("path", doc.FilePath), zap.Error(err))
	}

	// FastAPI 侧 Pydantic 模型要求 ID 为字符串，且 /embedding/delete 注册为 DELETE 路由
	body := map[string]any{
		"document_id": strconv.FormatInt(docID, 10),
		"kb_id":       strconv.FormatInt(doc.KbID, 10),
	}
	if err := s.ai.SendMethod(ctx, http.MethodDelete, "/embedding/delete", body, 30*time.Second); err != nil {
		zap.L().Warn("删除向量数据失败", zap.Int64("docId", docID), zap.Error(err))
	}

	return s.db.Delete(&model.KbDocument{}, docID).Error
}

// RebuildKB 对知识库下所有文档重新触发嵌入，切换嵌入模型后调用
func (s *KBService) RebuildKB(kbID, userID int64) error {
	if _, err := s.GetOwned(kbID, userID); err != nil {
		return err
	}
	var docs []model.KbDocument
	if err := s.db.Where("kb_id = ?", kbID).Find(&docs).Error; err != nil {
		return err
	}
	for _, doc := range docs {
		d := doc
		go s.buildEmbedding(&d)
	}
	return nil
}

// RecoverInterruptedTasks 启动时调用：把上次进程退出时遗留的 RUNNING 任务与
// PROCESSING 文档统一置为 FAILED。嵌入是裸 goroutine 异步执行，进程重启即丢失，
// 不重置的话这些记录会永久卡在进行中状态；置 FAILED 后用户可通过"重建"入口自助重跑。
func (s *KBService) RecoverInterruptedTasks() error {
	res := s.db.Model(&model.KbChunkTask{}).
		Where("task_status = ?", model.TaskStatusRunning).
		Updates(map[string]any{"task_status": model.TaskStatusFailed, "error_msg": "服务重启，任务中断"})
	if res.Error != nil {
		return res.Error
	}
	docRes := s.db.Model(&model.KbDocument{}).
		Where("status = ?", model.DocStatusProcessing).
		Update("status", model.DocStatusFailed)
	if docRes.Error != nil {
		return docRes.Error
	}
	if res.RowsAffected > 0 || docRes.RowsAffected > 0 {
		zap.L().Warn("已重置上次中断的嵌入任务",
			zap.Int64("tasks", res.RowsAffected), zap.Int64("docs", docRes.RowsAffected))
	}
	return nil
}

// ReconcileStuck 周期性对账：把停留在 PROCESSING 超过 maxAge 的文档与对应 RUNNING
// 任务收敛为 FAILED。区别于 RecoverInterruptedTasks（仅进程启动时跑一次）——本方法
// 覆盖进程未重启、但嵌入 goroutine 因超时/夭折/与 ChromaDB 写入失联而永久卡死的场景，
// 避免文档与向量库状态长期漂移。判定依据为 update_time（GORM autoUpdateTime 在置
// PROCESSING 时刷新），早于 now-maxAge 即视为卡死。由 cron JobHandler 调度，
// 收敛后用户可经「重建」入口自助重跑。返回收敛的文档数。
func (s *KBService) ReconcileStuck(maxAge time.Duration) (int64, error) {
	cutoff := time.Now().Add(-maxAge)
	docRes := s.db.Model(&model.KbDocument{}).
		Where("status = ? AND update_time < ?", model.DocStatusProcessing, cutoff).
		Update("status", model.DocStatusFailed)
	if docRes.Error != nil {
		return 0, docRes.Error
	}
	taskRes := s.db.Model(&model.KbChunkTask{}).
		Where("task_status = ? AND update_time < ?", model.TaskStatusRunning, cutoff).
		Updates(map[string]any{"task_status": model.TaskStatusFailed, "error_msg": "任务超时，对账自愈置失败"})
	if taskRes.Error != nil {
		return docRes.RowsAffected, taskRes.Error
	}
	if docRes.RowsAffected > 0 || taskRes.RowsAffected > 0 {
		zap.L().Warn("对账自愈：已收敛卡死的嵌入任务",
			zap.Int64("docs", docRes.RowsAffected), zap.Int64("tasks", taskRes.RowsAffected))
	}
	return docRes.RowsAffected, nil
}

// buildEmbedding 执行单文档嵌入流程：写任务审计记录 → 置 PROCESSING → POST FastAPI → 更新状态
// 对应 Spring Boot EmbeddingServiceImpl.buildAsync()，此处以 goroutine 调用实现等价的异步行为
func (s *KBService) buildEmbedding(doc *model.KbDocument) {
	task := &model.KbChunkTask{
		DocumentID: doc.ID,
		TaskStatus: model.TaskStatusRunning,
	}
	s.db.Create(task)

	// goroutine 内 panic 会击穿 Gin 的 Recovery 直接杀死进程，且任务会卡死在 RUNNING；
	// 此处兜底落 FAILED，与嵌入失败走同一条状态收敛路径
	defer func() {
		if r := recover(); r != nil {
			zap.L().Error("嵌入 goroutine panic", zap.Int64("docId", doc.ID), zap.Any("panic", r))
			s.db.Model(&model.KbDocument{}).Where("id = ?", doc.ID).Update("status", model.DocStatusFailed)
			s.db.Model(&model.KbChunkTask{}).Where("id = ?", task.ID).
				Updates(map[string]any{"task_status": model.TaskStatusFailed, "error_msg": fmt.Sprintf("内部错误: %v", r)})
		}
	}()

	s.db.Model(&model.KbDocument{}).Where("id = ?", doc.ID).Update("status", model.DocStatusProcessing)

	// FastAPI 侧 Pydantic 模型要求 ID 为字符串
	body := map[string]any{
		"document_id": strconv.FormatInt(doc.ID, 10),
		"kb_id":       strconv.FormatInt(doc.KbID, 10),
		"file_path":   doc.FilePath,
		"file_name":   doc.FileName,
	}

	// 嵌入操作可能耗时较长，给 5 分钟超时
	err := s.ai.Send(context.Background(), "/embedding/build", body, 5*time.Minute)
	if err != nil {
		zap.L().Error("嵌入失败", zap.Int64("docId", doc.ID), zap.Error(err))
		s.db.Model(&model.KbDocument{}).Where("id = ?", doc.ID).Update("status", model.DocStatusFailed)
		s.db.Model(&model.KbChunkTask{}).Where("id = ?", task.ID).
			Updates(map[string]any{"task_status": model.TaskStatusFailed, "error_msg": err.Error()})
		return
	}

	s.db.Model(&model.KbDocument{}).Where("id = ?", doc.ID).Update("status", model.DocStatusDone)
	s.db.Model(&model.KbChunkTask{}).Where("id = ?", task.ID).Update("task_status", model.TaskStatusSuccess)
	zap.L().Info("嵌入完成", zap.Int64("docId", doc.ID))
}

// reconcileStuckMaxAge 是对账判定卡死的阈值；略大于 buildEmbedding 的 5 分钟超时，
// 给正常长耗时嵌入留出余量，避免误杀仍在进行的任务。
const reconcileStuckMaxAge = 10 * time.Minute

// EmbeddingReconcileHandler 适配 scheduler.JobHandler 的嵌入对账自愈任务
// （invoke_target: embeddingReconcileJob）。建议配置为每 10 分钟执行的 cron。
type EmbeddingReconcileHandler struct{}

func (h *EmbeddingReconcileHandler) Execute(params string) error {
	if KBSvc == nil {
		return fmt.Errorf("KBSvc 未初始化")
	}
	_, err := KBSvc.ReconcileStuck(reconcileStuckMaxAge)
	return err
}
