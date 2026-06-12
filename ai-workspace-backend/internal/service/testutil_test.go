package service

import (
	"context"
	"errors"
	"io"
	"testing"
	"time"

	"github.com/glebarez/sqlite"
	"gorm.io/gorm"
	gormLogger "gorm.io/gorm/logger"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
)

// newTestDB 构造 sqlite 内存库并迁移测试涉及的表，无需任何外部基础设施
func newTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: gormLogger.Default.LogMode(gormLogger.Silent),
	})
	if err != nil {
		t.Fatalf("打开 sqlite 内存库失败: %v", err)
	}
	if err := db.AutoMigrate(
		&model.ChatSession{}, &model.ChatMessage{}, &model.ChatModel{},
		&model.KbKnowledgeBase{}, &model.KbDocument{}, &model.KbChunkTask{},
		&model.FileInfo{}, &model.Agent{},
	); err != nil {
		t.Fatalf("迁移测试表失败: %v", err)
	}
	return db
}

// fakeAI 是 EmbeddingCaller 的可编程假实现
type fakeAI struct {
	sendErr   error    // Send 返回的错误（nil 表示成功）
	sendPanic bool     // Send 时直接 panic，验证 recover 兜底
	calls     []string // 记录调用的 path
}

func (f *fakeAI) Send(_ context.Context, path string, _ any, _ time.Duration) error {
	f.calls = append(f.calls, path)
	if f.sendPanic {
		panic("fake panic")
	}
	return f.sendErr
}

func (f *fakeAI) SendMethod(_ context.Context, _, path string, _ any, _ time.Duration) error {
	f.calls = append(f.calls, path)
	return f.sendErr
}

// fakeStore 是 ObjectStore 的可编程假实现
type fakeStore struct {
	uploadErr error
	uploaded  []string
	deleted   []string
}

func (f *fakeStore) Upload(_ context.Context, name string, _ io.Reader, _ int64, _ string) error {
	if f.uploadErr != nil {
		return f.uploadErr
	}
	f.uploaded = append(f.uploaded, name)
	return nil
}

func (f *fakeStore) Delete(_ context.Context, name string) error {
	f.deleted = append(f.deleted, name)
	return nil
}

func (f *fakeStore) PresignedURL(_ context.Context, name string, _ time.Duration) (string, error) {
	return "https://fake/" + name, nil
}

// assertBizCode 断言错误是携带指定 code 的 BusinessError
func assertBizCode(t *testing.T, err error, wantCode int) {
	t.Helper()
	var bizErr *common.BusinessError
	if !errors.As(err, &bizErr) {
		t.Fatalf("期望 BusinessError，实际: %v", err)
	}
	if bizErr.Code != wantCode {
		t.Fatalf("期望错误码 %d，实际 %d（%s）", wantCode, bizErr.Code, bizErr.Message)
	}
}
