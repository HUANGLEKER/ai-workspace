package com.aiworkspace.monitor.dto;

import lombok.Data;

/**
 * 仪表盘统计视图对象
 *
 * 承载首页概览指标，所有计数均按当前登录用户隔离（非全局）。
 *
 * @since 2026
 */
@Data
public class DashboardStatsVO {
    /** 今日新建会话数（chat_session 按 userId + 当日零点过滤） */
    private long todaySessions;
    /** 知识库总数（kb_knowledge_base 按 createBy 过滤） */
    private long kbCount;
    /** 文档总数（kb_document 经用户知识库间接过滤） */
    private long docCount;
    /** 文件总数（file_info 按 uploadBy 过滤） */
    private long fileCount;
}
