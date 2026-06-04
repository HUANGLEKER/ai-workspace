package com.aiworkspace.common.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    private long total;
    private List<T> records;

    public PageResult() {}

    public PageResult(long total, List<T> records) {
        this.total = total;
        this.records = records;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getRecords());
    }
}
