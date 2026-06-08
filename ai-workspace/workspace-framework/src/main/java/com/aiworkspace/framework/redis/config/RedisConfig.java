package com.aiworkspace.framework.redis.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置
 *
 * 自定义 RedisTemplate 的序列化策略，统一项目内 Redis 读写的数据格式。
 *
 * 设计说明：key 采用 String 序列化保证可读性，value 采用 JSON 序列化，
 * 便于跨语言/跨服务查看与调试，避免 JDK 原生序列化产生不可读的二进制内容。
 * 注意 Spring Boot 默认使用 DB 0（与 FastAPI 使用的 DB 1 隔离）。
 *
 * @author
 * @since 2026
 */
@Configuration
public class RedisConfig {

    /**
     * 自定义 RedisTemplate，配置 key/value 序列化器
     *
     * @param factory Redis 连接工厂，由 Spring Data Redis 自动注入
     * @return 配置完成的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        ObjectMapper mapper = new ObjectMapper();
        // 允许序列化所有字段（含 private），避免因缺少 getter 导致字段丢失
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 开启默认类型信息，反序列化时可还原具体类型；使用 LaissezFaire 校验器放宽多态校验
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        // 注册 JavaTimeModule 以正确序列化 LocalDateTime 等 JSR-310 时间类型
        mapper.registerModule(new JavaTimeModule());

        Jackson2JsonRedisSerializer<Object> jsonSerializer = new Jackson2JsonRedisSerializer<>(mapper, Object.class);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // key/hashKey 用 String 序列化保证 Redis 中键可读；value/hashValue 用 JSON 序列化便于调试
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
