package common

import (
	"strconv"

	"github.com/gin-gonic/gin"
)

type PageResult[T any] struct {
	Total    int64 `json:"total"`
	PageNum  int   `json:"pageNum"`
	PageSize int   `json:"pageSize"`
	// 字段名 records 与前端 PageResult<T> 类型（沿用 MyBatis Plus 分页结构）对齐
	List []T `json:"records"`
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

// ParsePage 从查询参数中解析分页。
// 前端各列表页统一发送 ?page=&size=，同时兼容 ?pageNum=&pageSize= 旧参数名。
func ParsePage(c *gin.Context) PageQuery {
	pageNum, _ := strconv.Atoi(c.DefaultQuery("page", c.DefaultQuery("pageNum", "1")))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("size", c.DefaultQuery("pageSize", "10")))
	q := PageQuery{PageNum: pageNum, PageSize: pageSize}
	q.Normalize()
	return q
}
