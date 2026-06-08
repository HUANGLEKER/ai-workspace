package com.aiworkspace.job.mapper;

import com.aiworkspace.job.entity.SysJobLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务执行日志 Mapper
 *
 * 基于 MyBatis Plus BaseMapper 操作 sys_job_log 表；该表为只追加模型，
 * 清空日志为物理删除（无软删除列）。
 */
@Mapper
public interface SysJobLogMapper extends BaseMapper<SysJobLog> {
}
