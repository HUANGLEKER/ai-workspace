package handler

import (
	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/middleware"
	"github.com/aiworkspace/backend/internal/service"
)

// ListFiles GET /api/file/list?pageNum=&pageSize=&fileName=
func ListFiles(c *gin.Context) {
	pg := common.ParsePage(c)
	fileName := c.Query("fileName")
	result, err := service.FileSvc.PageList(middleware.CurrentUserID(c), pg.PageNum, pg.PageSize, fileName)
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, result)
}

// UploadFile POST /api/file/upload — multipart/form-data
func UploadFile(c *gin.Context) {
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		common.BadRequest(c, "获取文件失败: "+err.Error())
		return
	}
	defer file.Close()

	data := make([]byte, header.Size)
	if _, err = file.Read(data); err != nil {
		common.ServerError(c, "读取文件失败")
		return
	}

	info, err := service.FileSvc.Upload(c.Request.Context(), middleware.CurrentUserID(c),
		header.Filename, data, header.Header.Get("Content-Type"))
	if err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, info)
}

// DeleteFile DELETE /api/file/delete/:id
func DeleteFile(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.FileSvc.Delete(c.Request.Context(), id, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "删除成功")
}

// PresignFile GET /api/file/presign/:id — 生成临时预签名下载 URL
func PresignFile(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	url, err := service.FileSvc.GetPresignedURL(c.Request.Context(), id, middleware.CurrentUserID(c))
	if err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, gin.H{"url": url})
}
