package com.aiworkspace.monitor.dto;

import lombok.Data;

import java.util.List;

/**
 * Server runtime metrics: CPU, memory (system + JVM), disks, JVM and OS info.
 * All values are sampled on request; nothing is persisted.
 */
@Data
public class ServerInfoVO {

    private Cpu cpu = new Cpu();
    private Memory memory = new Memory();
    private Jvm jvm = new Jvm();
    private Os os = new Os();
    private List<Disk> disks;

    @Data
    public static class Cpu {
        /** number of logical processors */
        private int cores;
        /** system-wide CPU load, percent (0-100), -1 if unavailable */
        private double sysUsedPercent;
        /** this JVM process CPU load, percent (0-100), -1 if unavailable */
        private double procUsedPercent;
    }

    @Data
    public static class Memory {
        /** total physical memory, bytes */
        private long total;
        /** used physical memory, bytes */
        private long used;
        private double usedPercent;
    }

    @Data
    public static class Jvm {
        private String version;
        private String vendor;
        /** uptime in milliseconds */
        private long uptime;
        /** -Xmx, bytes */
        private long max;
        /** currently reserved by the JVM, bytes */
        private long total;
        /** in-use heap, bytes */
        private long used;
        private double usedPercent;
    }

    @Data
    public static class Os {
        private String name;
        private String arch;
        private String version;
    }

    @Data
    public static class Disk {
        private String path;
        private long total;
        private long used;
        private double usedPercent;
    }
}
