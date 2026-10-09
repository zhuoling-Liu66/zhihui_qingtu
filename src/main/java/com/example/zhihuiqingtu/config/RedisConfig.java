package com.example.zhihuiqingtu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Redis 配置。
 *
 * <p>本类的唯一职责是替换默认的序列化方式。Spring Boot 自动配置的
 * {@code RedisTemplate<Object, Object>} 使用 JDK 原生序列化，存在两个问题：
 * 存入的内容为二进制，无法通过 {@code redis-cli} 阅读；
 * 且要求目标类实现 {@code Serializable}，类结构变更后旧数据无法反序列化。
 * 改用 JSON 后缓存内容可读，且跨语言、跨版本可解析。
 *
 * <p><b>本项目使用 Jackson 3</b>（Spring Boot 4 默认，包名 {@code tools.jackson}），
 * 因此必须使用 {@link GenericJacksonJsonRedisSerializer}，
 * 而非 Jackson 2 时代的 {@code GenericJackson2JsonRedisSerializer}。
 */
@Configuration
public class RedisConfig {

    /**
     * 值序列化器：JSON 格式并携带类型信息。
     *
     * <p>单独声明为 Bean 是为了让序列化测试可直接获取它进行往返验证，
     * 无需启动容器或连接真实 Redis。
     *
     * <p><b>必须启用 default typing</b>：JSON 本身不携带类型信息，
     * 反序列化时无法还原为原类型（会退化为 {@code LinkedHashMap}，强转时抛出
     * {@code ClassCastException}）。启用后输出的 JSON 含 {@code "@class"} 字段。
     * 代价是 JSON 体积增大，且类改名或调整包路径后旧缓存将无法反序列化
     * （升级时需清理 Redis 或为 key 加版本前缀）。
     *
     * <p><b>类型白名单</b>：default typing 会按 JSON 中的类名加载类，
     * 不加限制时可被构造为反序列化攻击入口。下方白名单仅放行本项目类型及
     * 序列化分页、时间、金额所必需的 JDK 类型，其余一律拒绝。
     *
     * @return JSON 值序列化器
     */
    @Bean
    public RedisSerializer<Object> redisValueSerializer() {
        /*
         * 白名单逐条说明：
         *   com.example.zhihuiqingtu.  —— 项目 VO / 实体，缓存的主体
         *   java.util.                 —— List / Map，PageResponse.records 等泛型字段需要
         *   java.time.                 —— LocalDateTime，活动时间字段需要
         *   java.math.                 —— BigDecimal，活动学分需要
         *
         * 不使用 enableUnsafeDefaultTyping()：它放行任意类型，仅适合本地实验。
         */
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.zhihuiqingtu.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .build();

        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();
    }

    /**
     * 业务代码使用的 Redis 操作入口。
     *
     * <p>泛型为 {@code <String, Object>}：key 恒为字符串，value 可为任意对象。
     *
     * <p><b>Bean 名必须为 {@code redisTemplate}</b>：Spring Boot 的自动配置以
     * {@code @ConditionalOnMissingBean(name = "redisTemplate")} 为条件，
     * 换名会导致容器中存在两个 RedisTemplate，按类型注入时抛
     * {@code NoUniqueBeanDefinitionException}。
     *
     * <p>四个序列化器全部显式设置（key / value / hashKey / hashValue），
     * 避免出现仅部分数据类型序列化方式不正确的状态。
     *
     * @param connectionFactory 由 Spring Boot 依据 {@code spring.data.redis.*} 创建
     * @return 使用 JSON 值序列化的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        RedisSerializer<Object> valueSerializer = redisValueSerializer();
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        // 无需手动调用 afterPropertiesSet()：容器会在 Bean 初始化阶段自动回调
        return template;
    }
}
