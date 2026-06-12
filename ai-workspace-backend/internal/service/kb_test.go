package service

import (
	"context"
	"errors"
	"testing"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
)

func newKBSvc(t *testing.T) (*KBService, *fakeAI, *fakeStore) {
	ai := &fakeAI{}
	store := &fakeStore{}
	return NewKBService(newTestDB(t), ai, store), ai, store
}

func mustCreateKB(t *testing.T, s *KBService, userID int64) *model.KbKnowledgeBase {
	t.Helper()
	kb := &model.KbKnowledgeBase{KbName: "测试库"}
	if err := s.Create(kb, userID); err != nil {
		t.Fatalf("创建知识库失败: %v", err)
	}
	return kb
}

func mustCreateDoc(t *testing.T, s *KBService, kbID int64, status string) *model.KbDocument {
	t.Helper()
	doc := &model.KbDocument{KbID: kbID, FileName: "a.txt", FilePath: "kb/x", Status: status}
	if err := s.db.Create(doc).Error; err != nil {
		t.Fatalf("创建文档失败: %v", err)
	}
	return doc
}

// 知识库归属校验（IDOR 防线）：他人 403、不存在 404，Update 不得篡改归属
func TestKBOwnership(t *testing.T) {
	s, _, _ := newKBSvc(t)
	kb := mustCreateKB(t, s, userA)

	_, err := s.GetOwned(kb.ID, userB)
	assertBizCode(t, err, common.CodeForbidden)
	_, err = s.GetOwned(99999, userA)
	assertBizCode(t, err, common.CodeNotFound)

	// Update 即使请求体伪造归属，也必须回填为原 owner
	kb.CreateBy = userB
	if err := s.Update(kb, userA); err != nil {
		t.Fatalf("本人更新应成功: %v", err)
	}
	got, _ := s.GetOwned(kb.ID, userA)
	if got.CreateBy != userA {
		t.Fatalf("归属被篡改为 %d", got.CreateBy)
	}
}

// 嵌入成功路径：task RUNNING→SUCCESS，doc →DONE，调用了 /embedding/build
func TestBuildEmbeddingSuccess(t *testing.T) {
	s, ai, _ := newKBSvc(t)
	kb := mustCreateKB(t, s, userA)
	doc := mustCreateDoc(t, s, kb.ID, model.DocStatusPending)

	s.buildEmbedding(doc)

	if len(ai.calls) != 1 || ai.calls[0] != "/embedding/build" {
		t.Fatalf("FastAPI 调用错误: %v", ai.calls)
	}
	var gotDoc model.KbDocument
	s.db.First(&gotDoc, doc.ID)
	if gotDoc.Status != model.DocStatusDone {
		t.Fatalf("文档状态应为 DONE，实际 %s", gotDoc.Status)
	}
	var task model.KbChunkTask
	s.db.Where("document_id = ?", doc.ID).First(&task)
	if task.TaskStatus != model.TaskStatusSuccess {
		t.Fatalf("任务状态应为 SUCCESS，实际 %s", task.TaskStatus)
	}
}

// 嵌入失败路径：doc →FAILED，task →FAILED 且记录 error_msg
func TestBuildEmbeddingFailure(t *testing.T) {
	s, ai, _ := newKBSvc(t)
	ai.sendErr = errors.New("fastapi down")
	kb := mustCreateKB(t, s, userA)
	doc := mustCreateDoc(t, s, kb.ID, model.DocStatusPending)

	s.buildEmbedding(doc)

	var gotDoc model.KbDocument
	s.db.First(&gotDoc, doc.ID)
	if gotDoc.Status != model.DocStatusFailed {
		t.Fatalf("文档状态应为 FAILED，实际 %s", gotDoc.Status)
	}
	var task model.KbChunkTask
	s.db.Where("document_id = ?", doc.ID).First(&task)
	if task.TaskStatus != model.TaskStatusFailed || task.ErrorMsg == "" {
		t.Fatalf("任务应为 FAILED 且带 error_msg，实际 %s / %q", task.TaskStatus, task.ErrorMsg)
	}
}

// panic 兜底：不得击穿（杀死测试进程即未兜底），状态收敛到 FAILED
func TestBuildEmbeddingPanicRecovered(t *testing.T) {
	s, ai, _ := newKBSvc(t)
	ai.sendPanic = true
	kb := mustCreateKB(t, s, userA)
	doc := mustCreateDoc(t, s, kb.ID, model.DocStatusPending)

	s.buildEmbedding(doc) // panic 应被 recover，函数正常返回

	var task model.KbChunkTask
	s.db.Where("document_id = ?", doc.ID).First(&task)
	if task.TaskStatus != model.TaskStatusFailed {
		t.Fatalf("panic 后任务应为 FAILED，实际 %s", task.TaskStatus)
	}
	var gotDoc model.KbDocument
	s.db.First(&gotDoc, doc.ID)
	if gotDoc.Status != model.DocStatusFailed {
		t.Fatalf("panic 后文档应为 FAILED，实际 %s", gotDoc.Status)
	}
}

// 启动恢复：RUNNING 任务与 PROCESSING 文档重置为 FAILED，终态记录不受影响
func TestRecoverInterruptedTasks(t *testing.T) {
	s, _, _ := newKBSvc(t)
	kb := mustCreateKB(t, s, userA)
	docRunning := mustCreateDoc(t, s, kb.ID, model.DocStatusProcessing)
	docDone := mustCreateDoc(t, s, kb.ID, model.DocStatusDone)
	s.db.Create(&model.KbChunkTask{DocumentID: docRunning.ID, TaskStatus: model.TaskStatusRunning})
	s.db.Create(&model.KbChunkTask{DocumentID: docDone.ID, TaskStatus: model.TaskStatusSuccess})

	if err := s.RecoverInterruptedTasks(); err != nil {
		t.Fatalf("恢复失败: %v", err)
	}

	var tasks []model.KbChunkTask
	s.db.Order("id ASC").Find(&tasks)
	if tasks[0].TaskStatus != model.TaskStatusFailed || tasks[1].TaskStatus != model.TaskStatusSuccess {
		t.Fatalf("任务重置错误: %s / %s", tasks[0].TaskStatus, tasks[1].TaskStatus)
	}
	var docs []model.KbDocument
	s.db.Order("id ASC").Find(&docs)
	if docs[0].Status != model.DocStatusFailed || docs[1].Status != model.DocStatusDone {
		t.Fatalf("文档重置错误: %s / %s", docs[0].Status, docs[1].Status)
	}
}

// 上传失败（对象存储不可用）不得留下孤儿文档记录
func TestUploadDocumentStoreFailure(t *testing.T) {
	s, _, store := newKBSvc(t)
	store.uploadErr = errors.New("minio down")
	kb := mustCreateKB(t, s, userA)

	_, err := s.UploadDocument(context.Background(), kb.ID, userA, "a.txt", []byte("x"), "text/plain")
	if err == nil {
		t.Fatal("存储失败应返回错误")
	}
	var count int64
	s.db.Model(&model.KbDocument{}).Count(&count)
	if count != 0 {
		t.Fatalf("不应留下孤儿文档记录，实际 %d 条", count)
	}
}
