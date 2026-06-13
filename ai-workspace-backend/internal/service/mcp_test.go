package service

import (
	"testing"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
)

// stdio 注册仅管理员可（P3-5）：非管理员 403、管理员需带 command、sse 不受限
func TestMCPStdioAdminGuard(t *testing.T) {
	s := NewMCPService(newTestDB(t))

	// 非管理员注册 stdio → 403
	stdio := &model.McpServer{Name: "fs", Transport: "stdio", Command: "npx"}
	assertBizCode(t, s.Create(stdio, userA, false), common.CodeForbidden)

	// 管理员注册 stdio 但缺 command → 400
	bad := &model.McpServer{Name: "fs2", Transport: "stdio"}
	assertBizCode(t, s.Create(bad, userA, true), common.CodeBadRequest)

	// 管理员注册 stdio 带 command → 成功
	ok := &model.McpServer{Name: "fs3", Transport: "stdio", Command: "npx"}
	if err := s.Create(ok, userA, true); err != nil {
		t.Fatalf("管理员注册 stdio 应成功: %v", err)
	}

	// sse 不受 admin 限制
	sse := &model.McpServer{Name: "r", Transport: "sse", URL: "http://x/sse"}
	if err := s.Create(sse, userA, false); err != nil {
		t.Fatalf("非管理员注册 sse 应成功: %v", err)
	}
}

// ResolveForAgent：sse 始终纳入；stdio 仅在 isAdmin 时纳入，且携带 command/args/env
func TestMCPResolveForAgentStdioGate(t *testing.T) {
	s := NewMCPService(newTestDB(t))
	_ = s.Create(&model.McpServer{Name: "r", Transport: "sse", URL: "http://x/sse"}, userA, true)
	_ = s.Create(&model.McpServer{
		Name: "fs", Transport: "stdio", Command: "npx",
		Config: `{"args":["-y","srv"],"env":{"K":"v"}}`,
	}, userA, true)

	names := `["r","fs"]`

	// 非管理员运行：只解析出 sse，stdio 被 gate 掉
	nonAdmin, err := s.ResolveForAgent(userA, names, false)
	if err != nil {
		t.Fatalf("解析失败: %v", err)
	}
	if len(nonAdmin) != 1 || nonAdmin[0]["transport"] != "sse" {
		t.Fatalf("非管理员应只得 sse，实际 %v", nonAdmin)
	}

	// 管理员运行：sse + stdio 都在，stdio 带 command/args/env
	admin, err := s.ResolveForAgent(userA, names, true)
	if err != nil {
		t.Fatalf("解析失败: %v", err)
	}
	if len(admin) != 2 {
		t.Fatalf("管理员应得 2 个，实际 %d", len(admin))
	}
	var stdio map[string]any
	for _, m := range admin {
		if m["transport"] == "stdio" {
			stdio = m
		}
	}
	if stdio == nil || stdio["command"] != "npx" {
		t.Fatalf("stdio spec 缺失或 command 错误: %v", stdio)
	}
	args, _ := stdio["args"].([]string)
	env, _ := stdio["env"].(map[string]string)
	if len(args) != 2 || env["K"] != "v" {
		t.Fatalf("stdio args/env 解析错误: args=%v env=%v", stdio["args"], stdio["env"])
	}
}
