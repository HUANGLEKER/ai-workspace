package handler

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/middleware"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/service"
	"github.com/aiworkspace/backend/pkg/fastapi"
)

// ─── 知识库 CRUD ─────────────────────────────────────────────────────

// ListKBs GET /api/kb/list
func ListKBs(c *gin.Context) {
	kbs, err := service.KBSvc.ListByUser(middleware.CurrentUserID(c))
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, kbs)
}

// AddKB POST /api/kb/add
func AddKB(c *gin.Context) {
	var kb model.KbKnowledgeBase
	if err := c.ShouldBindJSON(&kb); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.KBSvc.Create(&kb, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, kb)
}

// UpdateKB PUT /api/kb/update
func UpdateKB(c *gin.Context) {
	var kb model.KbKnowledgeBase
	if err := c.ShouldBindJSON(&kb); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.KBSvc.Update(&kb, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "更新成功")
}

// DeleteKB DELETE /api/kb/delete/:id
func DeleteKB(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.KBSvc.Delete(id, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "删除成功")
}

// ─── 文档管理 ────────────────────────────────────────────────────────

// ListDocuments GET /api/document/list?kbId=&pageNum=&pageSize=
func ListDocuments(c *gin.Context) {
	var req struct {
		KbID     int64 `form:"kbId" binding:"required"`
		PageNum  int   `form:"pageNum"`
		PageSize int   `form:"pageSize"`
	}
	if err := c.ShouldBindQuery(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	result, err := service.KBSvc.PageDocuments(req.KbID, middleware.CurrentUserID(c), req.PageNum, req.PageSize)
	if err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, result)
}

// UploadDocument POST /api/document/upload — multipart/form-data，含 kbId 字段
func UploadDocument(c *gin.Context) {
	kbIDStr := c.PostForm("kbId")
	if kbIDStr == "" {
		common.BadRequest(c, "kbId 不能为空")
		return
	}
	var kbID int64
	fmt.Sscanf(kbIDStr, "%d", &kbID)

	file, header, err := c.Request.FormFile("file")
	if err != nil {
		common.BadRequest(c, "获取文件失败: "+err.Error())
		return
	}
	defer file.Close()

	// 读取文件内容（文档通常不超过数十 MB，内存读取可接受）
	data := make([]byte, header.Size)
	if _, err = file.Read(data); err != nil {
		common.ServerError(c, "读取文件失败")
		return
	}

	doc, err := service.KBSvc.UploadDocument(c.Request.Context(), kbID, middleware.CurrentUserID(c),
		header.Filename, data, header.Header.Get("Content-Type"))
	if err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, doc)
}

// DeleteDocument DELETE /api/document/delete/:id
func DeleteDocument(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.KBSvc.DeleteDocument(c.Request.Context(), id, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "删除成功")
}

// ─── RAG ─────────────────────────────────────────────────────────────

// RAGChat POST /api/rag/chat — SSE 流式 RAG 问答，代理 FastAPI /rag/chat
func RAGChat(c *gin.Context) {
	var req struct {
		KbID      int64  `json:"kbId"      binding:"required"`
		Question  string `json:"question"  binding:"required"`
		SessionID string `json:"sessionId"`
		TopK      int    `json:"topK"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}

	// 校验知识库归属，防止越权检索他人知识库
	if _, err := service.KBSvc.GetOwned(req.KbID, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}

	if req.TopK <= 0 {
		req.TopK = 4
	}
	if req.SessionID == "" {
		req.SessionID = "default"
	}

	body := map[string]any{
		"kb_id":      req.KbID,
		"question":   req.Question,
		"session_id": req.SessionID,
		"top_k":      req.TopK,
		"stream":     true,
	}

	c.Header("Content-Type", "text/event-stream")
	c.Header("Cache-Control", "no-cache")
	c.Header("X-Accel-Buffering", "no")

	w := c.Writer
	flusher, canFlush := w.(http.Flusher)
	ctx := c.Request.Context()

	_ = fastapi.Client.Stream(ctx, "/rag/chat", body, func(line string) error {
		if strings.TrimSpace(line) == "" {
			return nil
		}
		// 透传 sources 元数据帧与 content token 帧，前端分别处理
		if strings.HasPrefix(line, "data: ") {
			data := strings.TrimPrefix(line, "data: ")
			var payload map[string]any
			if err := json.Unmarshal([]byte(data), &payload); err == nil {
				// sources 帧：文档引用信息（可折叠展示）
				// content 帧：LLM token
				// 统一透传，由前端的 streamSSE.extract 分拣
				_ = payload
			}
		}
		fmt.Fprintf(w, "%s\n\n", line)
		if canFlush {
			flusher.Flush()
		}
		return nil
	})
}

// RAGRebuild POST /api/rag/rebuild — 对指定知识库全量重建嵌入（切换模型后调用）
func RAGRebuild(c *gin.Context) {
	var req struct {
		KbID int64 `json:"kbId" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.KBSvc.RebuildKB(req.KbID, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "已触发重建，文档将在后台逐一重新嵌入")
}
