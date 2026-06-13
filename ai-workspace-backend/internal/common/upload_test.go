package common

import (
	"errors"
	"testing"
)

func TestValidateUpload(t *testing.T) {
	exts := []string{".pdf", ".txt", ".docx"}

	cases := []struct {
		name     string
		fileName string
		size     int64
		exts     []string
		maxMB    int
		wantErr  bool
	}{
		{"正常 pdf", "a.pdf", 1024, exts, 50, false},
		{"大写扩展名不敏感", "A.PDF", 1024, exts, 50, false},
		{"不在白名单", "a.exe", 1024, exts, 50, true},
		{"无扩展名", "README", 1024, exts, 50, true},
		{"超过大小上限", "a.pdf", 60 * 1024 * 1024, exts, 50, true},
		{"空文件", "a.pdf", 0, exts, 50, true},
		{"不限类型（空白名单）", "a.exe", 1024, nil, 50, false},
		{"不限大小（maxMB=0）", "a.pdf", 999 * 1024 * 1024, exts, 0, false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			err := ValidateUpload(tc.fileName, tc.size, tc.exts, tc.maxMB)
			if tc.wantErr != (err != nil) {
				t.Fatalf("期望 wantErr=%v，实际 err=%v", tc.wantErr, err)
			}
			if err != nil {
				var biz *BusinessError
				if !errors.As(err, &biz) || biz.Code != CodeBadRequest {
					t.Fatalf("应为 400 业务错误，实际 %v", err)
				}
			}
		})
	}
}

func TestSniffContentType(t *testing.T) {
	// PNG 魔数
	png := []byte{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
	if got := SniffContentType(png); got != "image/png" {
		t.Fatalf("PNG 嗅探错误: %s", got)
	}
	// 纯文本
	if got := SniffContentType([]byte("hello world")); got[:5] != "text/" {
		t.Fatalf("文本嗅探错误: %s", got)
	}
	// 空内容兜底
	if got := SniffContentType(nil); got != "application/octet-stream" {
		t.Fatalf("空内容应兜底 octet-stream，实际 %s", got)
	}
}
