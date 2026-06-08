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

/**
 * 系统监控服务
 *
 * 采集服务器运行时指标并探测依赖服务健康状态，无任何 DB 表依赖。
 *
 * 主要职责：
 * 1. 通过 JDK MXBean 采集 CPU / 内存 / JVM / 磁盘 / OS 运行时指标
 * 2. 探测 Redis、FastAPI、MinIO 的连通性与延迟
 *
 * @since 2026
 */
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

    /**
     * 采集服务器运行时指标
     *
     * <p>所有指标均取自 JDK MXBean / Runtime，按请求实时采样，不做持久化。
     *
     * @return CPU、系统内存、JVM、OS、磁盘等运行时指标快照
     */
    public ServerInfoVO getServerInfo() {
        ServerInfoVO vo = new ServerInfoVO();
        // 取自 JDK OperatingSystemMXBean：提供 CPU 负载与物理内存等系统级指标
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

    /**
     * 探测平台依赖服务的健康状态
     *
     * @return Redis、FastAPI、MinIO 各自的健康检查结果（含连通性与延迟）
     */
    public List<ServiceHealthVO> getServiceHealth() {
        List<ServiceHealthVO> list = new ArrayList<>();
        list.add(checkRedis());
        list.add(checkHttp("FastAPI", fastapiBaseUrl + "/health"));
        list.add(checkHttp("MinIO", minioEndpoint + "/minio/health/live"));
        return list;
    }

    /** Redis 健康探针：用 PING 命令验证连通性并测量往返延迟 */
    private ServiceHealthVO checkRedis() {
        String target = "Redis";
        long start = System.currentTimeMillis();
        // try-with-resources 确保连接归还连接池，避免探活泄漏连接
        try (RedisConnection conn = redisConnectionFactory.getConnection()) {
            conn.ping();
            return ServiceHealthVO.up(target, target, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ServiceHealthVO.down(target, target, e.getMessage());
        }
    }

    /** HTTP 健康探针：GET 健康端点，2xx/3xx 视为 UP；设置短超时避免探活阻塞 */
    private ServiceHealthVO checkHttp(String name, String url) {
        long start = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            // 限定 2s 连接/读取超时，防止依赖不可用时拖垮监控接口响应
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

    /** 将 MXBean 返回的 0~1 负载折算为百分比；MXBean 不可用时会返回负值，此处透传 -1 表示无数据 */
    private static double toPercent(double load) {
        if (load < 0) {
            return -1;
        }
        return round2(load * 100);
    }

    /** 计算占用百分比；total 非正时按 0 处理，避免除零 */
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
