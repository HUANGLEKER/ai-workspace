package common

import (
	"strconv"

	"github.com/gin-gonic/gin"
)

type PageResult[T any] struct {
	Total    int64 `json:"total"`
	PageNum  int   `json:"pageNum"`
	PageSize int   `json:"pageSize"`
	List     []T   `json:"list"`
}

type PageQuery struct {
	PageNum  int `form:"pageNum"`
	PageSize int `form:"pageSize"`
}

func (p *PageQuery) Normalize() {
	if p.PageNum <= 0 {
		p.PageNum = 1
	}
	if p.PageSize <= 0 || p.PageSize > 100 {
		p.PageSize = 10
	}
}

func (p *PageQuery) Offset() int {
	return (p.PageNum - 1) * p.PageSize
}

// ParsePage 从查询参数中解析分页，兼容 ?pageNum=1&pageSize=10
func ParsePage(c *gin.Context) PageQuery {
	pageNum, _ := strconv.Atoi(c.DefaultQuery("pageNum", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "10"))
	q := PageQuery{PageNum: pageNum, PageSize: pageSize}
	q.Normalize()
	return q
}
