package com.aiworkspace.monitor.service;

import com.aiworkspace.monitor.dto.ServerInfoVO;
import com.aiworkspace.monitor.dto.ServiceHealthVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
public class MonitorService {

    private final RedisConnectionFactory redisConnectionFactory;

    @Value("${fastapi.base-url}")
    private String fastapiBaseUrl;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    public MonitorService(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    /** Sample server runtime metrics. */
    public ServerInfoVO getServerInfo() {
        ServerInfoVO vo = new ServerInfoVO();
        com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        Runtime runtime = Runtime.getRuntime();

        // CPU
        ServerInfoVO.Cpu cpu = vo.getCpu();
        cpu.setCores(osBean.getAvailableProcessors());
        cpu.setSysUsedPercent(toPercent(osBean.getCpuLoad()));
        cpu.setProcUsedPercent(toPercent(osBean.getProcessCpuLoad()));

        // System memory
        ServerInfoVO.Memory mem = vo.getMemory();
        long totalMem = osBean.getTotalMemorySize();
        long freeMem = osBean.getFreeMemorySize();
        long usedMem = totalMem - freeMem;
        mem.setTotal(totalMem);
        mem.setUsed(usedMem);
        mem.setUsedPercent(ratio(usedMem, totalMem));

        // JVM
        ServerInfoVO.Jvm jvm = vo.getJvm();
        jvm.setVersion(System.getProperty("java.version"));
        jvm.setVendor(System.getProperty("java.vendor"));
        jvm.setUptime(runtimeBean.getUptime());
        long jvmMax = runtime.maxMemory();
        long jvmTotal = runtime.totalMemory();
        long jvmUsed = jvmTotal - runtime.freeMemory();
        jvm.setMax(jvmMax);
        jvm.setTotal(jvmTotal);
        jvm.setUsed(jvmUsed);
        jvm.setUsedPercent(ratio(jvmUsed, jvmMax));

        // OS
        ServerInfoVO.Os os = vo.getOs();
        os.setName(osBean.getName());
        os.setArch(osBean.getArch());
        os.setVersion(osBean.getVersion());

        // Disks
        List<ServerInfoVO.Disk> disks = new ArrayList<>();
        for (File root : File.listRoots()) {
            long total = root.getTotalSpace();
            if (total <= 0) {
                continue;
            }
            long usable = root.getUsableSpace();
            long used = total - usable;
            ServerInfoVO.Disk disk = new ServerInfoVO.Disk();
            disk.setPath(root.getAbsolutePath());
            disk.setTotal(total);
            disk.setUsed(used);
            disk.setUsedPercent(ratio(used, total));
            disks.add(disk);
        }
        vo.setDisks(disks);

        return vo;
    }

    /** Probe each dependency the platform relies on. */
    public List<ServiceHealthVO> getServiceHealth() {
        List<ServiceHealthVO> list = new ArrayList<>();
        list.add(checkRedis());
        list.add(checkHttp("FastAPI", fastapiBaseUrl + "/health"));
        list.add(checkHttp("MinIO", minioEndpoint + "/minio/health/live"));
        return list;
    }

    private ServiceHealthVO checkRedis() {
        String target = "Redis";
        long start = System.currentTimeMillis();
        try (RedisConnection conn = redisConnectionFactory.getConnection()) {
            conn.ping();
            return ServiceHealthVO.up(target, target, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ServiceHealthVO.down(target, target, e.getMessage());
        }
    }

    private ServiceHealthVO checkHttp(String name, String url) {
        long start = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int code = conn.getResponseCode();
            long latency = System.currentTimeMillis() - start;
            if (code >= 200 && code < 400) {
                return ServiceHealthVO.up(name, url, latency);
            }
            return ServiceHealthVO.down(name, url, "HTTP " + code);
        } catch (Exception e) {
            return ServiceHealthVO.down(name, url, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static double toPercent(double load) {
        if (load < 0) {
            return -1;
        }
        return round2(load * 100);
    }

    private static double ratio(long used, long total) {
        if (total <= 0) {
            return 0;
        }
        return round2((double) used / total * 100);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
