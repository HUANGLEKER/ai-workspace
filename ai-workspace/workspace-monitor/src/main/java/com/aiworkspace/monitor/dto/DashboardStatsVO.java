package com.aiworkspace.monitor.dto;

import lombok.Data;

@Data
public class DashboardStatsVO {
    private long todaySessions;
    private long kbCount;
    private long docCount;
    private long fileCount;
}
