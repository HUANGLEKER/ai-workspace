package com.aiworkspace.common.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 分页结果包装
 *
 * 对外暴露统一的分页结构（总数 + 当前页记录），屏蔽 MyBatis Plus 的 IPage 实现细节，
 * 避免分页内部分页器、当前页号等字段泄露给前端。
 *
 * @param <T> 记录数据类型
 * @author
 * @since 2026
 */
@Data
public class PageResult<T> {

    /** 总记录数 */
    private long total;
    /** 当前页记录列表 */
    private List<T> records;

    public PageResult() {}

    public PageResult(long total, List<T> records) {
        this.total = total;
        this.records = records;
    }

    /**
     * 由 MyBatis Plus 的 IPage 转换为统一分页结果
     *
     * @param page MyBatis Plus 分页对象
     * @return 标准分页结果
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getRecords());
    }
}
