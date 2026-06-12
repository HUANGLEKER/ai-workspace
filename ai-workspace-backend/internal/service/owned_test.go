package service

import (
	"testing"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
)

// 三套归属列名必须由模型正确声明——列名映射错误曾是真实 IDOR 漏洞的根因
func TestOwnerColumnMapping(t *testing.T) {
	cases := []struct {
		m    model.Owned
		want string
	}{
		{model.Agent{}, "create_by"},
		{model.KbKnowledgeBase{}, "create_by"},
		{model.Prompt{}, "create_by"},
		{model.Tool{}, "create_by"},
		{model.McpServer{}, "create_by"},
		{model.Workflow{}, "create_by"},
		{model.ChatSession{}, "user_id"},
		{model.FileInfo{}, "upload_by"},
	}
	for _, c := range cases {
		if got := c.m.OwnerColumn(); got != c.want {
			t.Errorf("%T 归属列应为 %s，实际 %s", c.m, c.want, got)
		}
	}
}

// getOwnedResource 的三态语义：本人 OK / 他人 403 / 不存在 404，
// 对非 create_by 列（FileInfo.upload_by）同样成立
func TestGetOwnedResourceGeneric(t *testing.T) {
	db := newTestDB(t)
	f := &model.FileInfo{FileName: "a.txt", FilePath: "f/x", UploadBy: userA}
	if err := db.Create(f).Error; err != nil {
		t.Fatalf("建文件失败: %v", err)
	}

	got, err := getOwnedResource[model.FileInfo](db, f.ID, userA, "文件")
	if err != nil || got.FileName != "a.txt" {
		t.Fatalf("本人访问应成功: %v", err)
	}
	_, err = getOwnedResource[model.FileInfo](db, f.ID, userB, "文件")
	assertBizCode(t, err, common.CodeForbidden)
	_, err = getOwnedResource[model.FileInfo](db, 999, userA, "文件")
	assertBizCode(t, err, common.CodeNotFound)
}

// ownedScope 列表过滤：只返回本人记录，列名取自模型声明
func TestOwnedScopeFilters(t *testing.T) {
	db := newTestDB(t)
	mk := func(uid int64, name string) {
		a := &model.Agent{Name: name}
		a.CreateBy = uid
		if err := db.Create(a).Error; err != nil {
			t.Fatalf("建 Agent 失败: %v", err)
		}
	}
	mk(userA, "a1")
	mk(userA, "a2")
	mk(userB, "b1")

	var mine []model.Agent
	if err := db.Scopes(ownedScope[model.Agent](userA)).Find(&mine).Error; err != nil {
		t.Fatalf("查询失败: %v", err)
	}
	if len(mine) != 2 {
		t.Fatalf("应只返回本人 2 条，实际 %d", len(mine))
	}
	for _, a := range mine {
		if a.CreateBy != userA {
			t.Fatalf("混入他人记录: %+v", a)
		}
	}
}
