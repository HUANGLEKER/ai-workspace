package service

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"time"

	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/pkg/database"
)

// MCPSvc 是 MCP 服务器服务全局单例
var MCPSvc = &mcpService{}

type mcpService struct{}

// ListByUser 查询当前用户注册的所有 MCP 服务器
func (s *mcpService) ListByUser(userID int64) ([]model.McpServer, error) {
	var servers []model.McpServer
	err := database.DB.Where("create_by = ?", userID).Order("create_time DESC").Find(&servers).Error
	return servers, err
}

// GetOwned 查询并校验 MCP 服务器归属，防止 IDOR
func (s *mcpService) GetOwned(id, userID int64) (*model.McpServer, error) {
	var srv model.McpServer
	err := database.DB.First(&srv, id).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, common.ErrNotFound("MCP服务器")
	}
	if err != nil {
		return nil, err
	}
	if srv.CreateBy != userID {
		return nil, common.ErrForbidden()
	}
	return &srv, nil
}

// Create 新建 MCP 服务器，强制 CreateBy 为当前用户
func (s *mcpService) Create(srv *model.McpServer, userID int64) error {
	if srv.Name == "" {
		return common.NewBizError(common.CodeBadRequest, "MCP服务器名称不能为空")
	}
	srv.ID = 0
	srv.CreateBy = userID
	if srv.Enabled == 0 {
		srv.Enabled = 1
	}
	return database.DB.Create(srv).Error
}

// Update 更新 MCP 服务器，回填 CreateBy 防止归属被篡改
func (s *mcpService) Update(srv *model.McpServer, userID int64) error {
	existing, err := s.GetOwned(srv.ID, userID)
	if err != nil {
		return err
	}
	srv.CreateBy = existing.CreateBy
	return database.DB.Save(srv).Error
}

// Delete 校验归属后删除 MCP 服务器
func (s *mcpService) Delete(id, userID int64) error {
	if _, err := s.GetOwned(id, userID); err != nil {
		return err
	}
	return database.DB.Delete(&model.McpServer{}, id).Error
}

// TestConnectivity 对 SSE 类型 MCP 服务器发起 HTTP 可达性探测
// stdio 类型不支持远程探测，直接返回说明信息
func (s *mcpService) TestConnectivity(id, userID int64) (bool, int64, string) {
	srv, err := s.GetOwned(id, userID)
	if err != nil {
		return false, 0, err.Error()
	}
	if srv.Transport != "sse" {
		return false, 0, "stdio 传输类型不支持远程连通性探测"
	}
	if srv.URL == "" {
		return false, 0, "SSE URL 为空"
	}

	start := time.Now()
	client := &http.Client{Timeout: 5 * time.Second}
	resp, err := client.Get(srv.URL)
	latency := time.Since(start).Milliseconds()
	if err != nil {
		return false, latency, err.Error()
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 200 && resp.StatusCode < 400 {
		return true, latency, fmt.Sprintf("HTTP %d", resp.StatusCode)
	}
	return false, latency, fmt.Sprintf("HTTP %d", resp.StatusCode)
}

// ResolveForAgent 按服务器名列表（JSON 数组字符串）解析用户名下已启用的 SSE MCP 服务器配置
// 仅返回 transport=sse 且 enabled=1 的服务器，供 FastAPI /agent/run 使用
func (s *mcpService) ResolveForAgent(userID int64, serverNamesJSON string) ([]map[string]any, error) {
	var names []string
	if serverNamesJSON != "" && serverNamesJSON != "[]" {
		if err := json.Unmarshal([]byte(serverNamesJSON), &names); err != nil {
			return nil, err
		}
	}
	if len(names) == 0 {
		return []map[string]any{}, nil
	}

	var servers []model.McpServer
	err := database.DB.Where("create_by = ? AND name IN ? AND transport = 'sse' AND enabled = 1", userID, names).Find(&servers).Error
	if err != nil {
		return nil, err
	}

	result := make([]map[string]any, 0, len(servers))
	for _, srv := range servers {
		spec := map[string]any{
			"name":   srv.Name,
			"url":    srv.URL,
			"config": srv.Config,
		}
		result = append(result, spec)
	}
	return result, nil
}
