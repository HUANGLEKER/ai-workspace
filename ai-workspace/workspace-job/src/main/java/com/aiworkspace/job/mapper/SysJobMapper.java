package com.aiworkspace.job.mapper;

import com.aiworkspace.job.entity.SysJob;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务 Mapper
 *
 * 基于 MyBatis Plus BaseMapper，提供 sys_job 表的基础 CRUD 能力。
 */
@Mapper
public interface SysJobMapper extends BaseMapper<SysJob> {
}
