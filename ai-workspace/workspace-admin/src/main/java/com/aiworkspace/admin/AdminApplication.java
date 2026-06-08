package com.aiworkspace.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI Workspace 应用启动入口
 *
 * 聚合所有 workspace-* 业务模块的 Spring Boot 主类。
 *
 * 设计说明：
 * 1. {@code scanBasePackages="com.aiworkspace"} —— 全局扫描各兄弟模块的组件
 * 2. {@code @MapperScan("com.aiworkspace.**.mapper")} —— 统一注册各模块 MyBatis Mapper
 * 3. {@code @EnableScheduling} —— 开启定时任务调度，支撑 workspace-job 的动态 cron 调度器
 *
 * @since 2026
 */
@SpringBootApplication(scanBasePackages = "com.aiworkspace")
@MapperScan("com.aiworkspace.**.mapper")
@EnableScheduling
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
