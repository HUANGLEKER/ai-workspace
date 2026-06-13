package common

import (
	"fmt"
	"net/http"
	"path/filepath"
	"strings"
)

// ValidateUpload 校验上传文件的大小与扩展名（P2-6 文件上传加固）。
//   - maxSizeMB <= 0 时不限大小；allowedExts 为空时不限类型
//   - allowedExts 元素为小写含点扩展名（如 ".pdf"），匹配大小写不敏感
//
// 返回业务错误（400），由调用方经 handleBizError 透出。
func ValidateUpload(fileName string, size int64, allowedExts []string, maxSizeMB int) error {
	if maxSizeMB > 0 {
		limit := int64(maxSizeMB) * 1024 * 1024
		if size > limit {
			return NewBizError(CodeBadRequest, fmt.Sprintf("文件超过大小上限 %dMB", maxSizeMB))
		}
	}
	if size == 0 {
		return NewBizError(CodeBadRequest, "文件内容为空")
	}
	if len(allowedExts) > 0 {
		ext := strings.ToLower(filepath.Ext(fileName))
		for _, e := range allowedExts {
			if ext == strings.ToLower(e) {
				return nil
			}
		}
		return NewBizError(CodeBadRequest, fmt.Sprintf("不支持的文件类型 %q，仅允许：%s", ext, strings.Join(allowedExts, " ")))
	}
	return nil
}

// SniffContentType 用文件内容前 512 字节嗅探真实 MIME 类型（http.DetectContentType），
// 不信任客户端传入的 Content-Type（可伪造）。无法识别时返回 application/octet-stream。
func SniffContentType(data []byte) string {
	if len(data) == 0 {
		return "application/octet-stream"
	}
	n := 512
	if len(data) < n {
		n = len(data)
	}
	return http.DetectContentType(data[:n])
}
