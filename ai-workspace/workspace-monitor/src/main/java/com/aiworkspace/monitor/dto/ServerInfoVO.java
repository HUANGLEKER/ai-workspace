package com.aiworkspace.monitor.dto;

import lombok.Data;

import java.util.List;

/**
 * 服务器运行时指标视图对象
 *
 * 承载 CPU、物理内存、JVM 堆、磁盘、OS 等运行时快照数据。
 * 所有指标均通过 JDK MXBean 按请求实时采样，不做持久化。
 *
 * @since 2026
 */
@Data
public class ServerInfoVO {

    private Cpu cpu = new Cpu();
    private Memory memory = new Memory();
    private Jvm jvm = new Jvm();
    private Os os = new Os();
    private List<Disk> disks;

    /** CPU 指标（来自 JDK OperatingSystemMXBean） */
    @Data
    public static class Cpu {
        /** 逻辑处理器核数 */
        private int cores;
        /** 系统级 CPU 使用率（百分比 0-100），MXBean 不可用时为 -1 */
        private double sysUsedPercent;
        /** 当前 JVM 进程 CPU 使用率（百分比 0-100），MXBean 不可用时为 -1 */
        private double procUsedPercent;
    }

    /** 物理内存指标（来自 JDK OperatingSystemMXBean） */
    @Data
    public static class Memory {
        /** 物理内存总量（字节） */
        private long total;
        /** 已用物理内存（字节） */
        private long used;
        /** 内存使用率（百分比） */
        private double usedPercent;
    }

    /** JVM 堆指标（来自 Runtime / RuntimeMXBean） */
    @Data
    public static class Jvm {
        /** Java 版本（java.version 系统属性） */
        private String version;
        /** JVM 供应商（java.vendor 系统属性） */
        private String vendor;
        /** JVM 已运行时长（毫秒，来自 RuntimeMXBean.getUptime） */
        private long uptime;
        /** -Xmx 最大堆大小（字节） */
        private long max;
        /** JVM 当前已申请堆大小（字节） */
        private long total;
        /** JVM 已用堆大小（字节） */
        private long used;
        /** 堆使用率（已用/最大，百分比） */
        private double usedPercent;
    }

    /** 操作系统基础信息（来自 OperatingSystemMXBean） */
    @Data
    public static class Os {
        private String name;
        private String arch;
        private String version;
    }

    /** 磁盘分区指标（来自 File.listRoots） */
    @Data
    public static class Disk {
        /** 挂载路径 */
        private String path;
        /** 分区总容量（字节） */
        private long total;
        /** 已用容量（字节） */
        private long used;
        /** 磁盘使用率（百分比） */
        private double usedPercent;
    }
}
