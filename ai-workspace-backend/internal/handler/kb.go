package handler

import (
	"io"
	"strconv"

	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/config"
	"github.com/aiworkspace/backend/internal/middleware"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/service"
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
	kbID, err := strconv.ParseInt(c.Query("kbId"), 10, 64)
	if err != nil || kbID <= 0 {
		common.BadRequest(c, "kbId 无效")
		return
	}
	pg := common.ParsePage(c)
	result, err := service.KBSvc.PageDocuments(kbID, middleware.CurrentUserID(c), pg.PageNum, pg.PageSize)
	if err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, result)
}

// UploadDocument POST /api/document/upload — multipart/form-data，含 kbId 字段
func UploadDocument(c *gin.Context) {
	kbID, err := strconv.ParseInt(c.PostForm("kbId"), 10, 64)
	if err != nil || kbID <= 0 {
		common.BadRequest(c, "kbId 无效")
		return
	}

	file, header, err := c.Request.FormFile("file")
	if err != nil {
		common.BadRequest(c, "获取文件失败: "+err.Error())
		return
	}
	defer file.Close()

	// 读取文件内容（文档通常不超过数十 MB，内存读取可接受）
	data, err := io.ReadAll(file)
	if err != nil {
		common.ServerError(c, "读取文件失败")
		return
	}

	// 加固：大小 + 文档扩展名白名单校验；存储用嗅探出的真实 MIME
	cfg := config.Global.Upload
	if err := common.ValidateUpload(header.Filename, int64(len(data)), cfg.DocExts, cfg.MaxSizeMB); err != nil {
		handleBizError(c, err)
		return
	}
	doc, err := service.KBSvc.UploadDocument(c.Request.Context(), kbID, middleware.CurrentUserID(c),
		header.Filename, data, common.SniffContentType(data))
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
//
// RAGChat（SSE 流式问答）及问答会话 CRUD 已迁至 handler/rag.go，
// 以承载会话历史持久化、多轮上下文与引用来源落库。

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
