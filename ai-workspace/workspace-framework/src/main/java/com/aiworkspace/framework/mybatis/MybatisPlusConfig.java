package com.aiworkspace.framework.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus 配置
 *
 * 注册 MyBatis Plus 核心插件链，当前启用分页拦截器。
 *
 * @author
 * @since 2026
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 配置 MyBatis Plus 拦截器
     *
     * 设计说明：注册 MySQL 方言的分页内部拦截器，使 IPage 查询自动改写为
     * 带 LIMIT 的物理分页，避免内存分页导致的全表扫描与大结果集占用。
     *
     * @return MyBatis Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 指定 MySQL 方言，确保分页 SQL 改写正确（不同数据库分页语法不同）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
