package com.example.zhihuiqingtu.config;

import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.activity.vo.ActivityResponse;
import com.example.zhihuiqingtu.activity.vo.ActivityTagVO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.example.zhihuiqingtu.activity.support.ActivityTagDataInitializer;

/**
 * Redis 值序列化器行为测试。
 * <p>
 * 这个类同时充当"为什么不能直接用默认 JDK 序列化、为什么必须开 default typing"
 * 的可执行文档：每个用例对应一条前面 {@link RedisConfig} 里讲过的性质，跑一遍就能看到结论。
 * <p>
 * 注意这里只 new 一个 {@link RedisConfig} 拿序列化器，<b>既不启动 Spring 容器、
 * 也不连接真实 Redis</b> —— 被测对象是纯序列化逻辑，用 {@code @SpringBootTest} 只会让测试变慢，
 * 而且本项目一旦启动完整上下文就会去查 MySQL（{@code ActivityTagDataInitializer} 是
 * {@code CommandLineRunner}），在没装数据库的机器上必然失败。
 */
class RedisSerializerTests {

    private final RedisSerializer<Object> serializer = new RedisConfig().redisValueSerializer();

    /**
     * 构造一个字段尽量齐全的活动返回对象，覆盖本项目实际会缓存的数据形态：
     * 包装类型、String、BigDecimal、LocalDateTime，以及嵌套的 List&lt;VO&gt;。
     */
    private static ActivityResponse sampleActivity() {
        return ActivityResponse.builder()
                .activityId(1001L)
                .publisherUserId("20210001")
                .activityTitle("校园马拉松志愿服务")
                .activityDesc("协助赛道引导与物资发放")
                .activityType(2)
                .activityCredit(new BigDecimal("1.5"))
                .creditType(1)
                .volunteerHours(new BigDecimal("4.0"))
                .coverUrl("/uploads/activity-covers/1001.png")
                .startTime(LocalDateTime.of(2026, 10, 1, 8, 30, 0))
                .endTime(LocalDateTime.of(2026, 10, 1, 12, 0, 0))
                .activityLocation("东校区体育场")
                .maxParticipant(50)
                .currentParticipant(12)
                .activityStatus(1)
                .displayStatus(1)
                .signInEnabled(1)
                .tags(List.of(
                        ActivityTagVO.builder().tagId(1L).tagName("志愿服务").build(),
                        ActivityTagVO.builder().tagId(2L).tagName("体育").build()))
                .build();
    }

    /**
     * 性质一：对象存进去再取出来，类型不变、内容相等。
     * <p>
     * 这是 default typing 存在的意义。如果不开，取回来的是 {@code LinkedHashMap}，
     * 业务代码强转 {@code ActivityResponse} 时抛 {@code ClassCastException}。
     */
    @Test
    void shouldKeepConcreteTypeAfterRoundTrip() {
        ActivityResponse original = sampleActivity();

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertInstanceOf(ActivityResponse.class, restored,
                "取回来的应该是 ActivityResponse，而不是 LinkedHashMap —— 否则说明 default typing 没生效");
        assertEquals(original, restored);
    }

    /**
     * 性质二：{@code LocalDateTime} 能原样往返。
     * <p>
     * 时间字段是最容易出问题的地方：写错配置时它会被序列化成
     * {@code [2026,10,1,8,30]} 这样的数字数组（时间戳风格），或者反序列化时直接抛异常。
     * <p>
     * 本项目的 Redis 用的是自己那个 ObjectMapper，<b>和 Spring MVC 处理 HTTP 响应体的
     * 那个是两套</b>（{@code application.properties} 里的 {@code spring.jackson.*}
     * 只对后者生效）。所以这个性质必须单独验证，不能想当然。
     */
    @Test
    void shouldPreserveLocalDateTime() {
        ActivityResponse original = sampleActivity();

        ActivityResponse restored = (ActivityResponse) serializer.deserialize(serializer.serialize(original));

        assertEquals(LocalDateTime.of(2026, 10, 1, 8, 30, 0), restored.getStartTime());
        assertEquals(LocalDateTime.of(2026, 10, 1, 12, 0, 0), restored.getEndTime());
    }

    /**
     * 性质三（本项目最容易踩的坑）：{@code PageResponse<ActivityResponse>} 这种泛型嵌套，
     * 往返后 {@code records} 里的元素仍然是 {@code ActivityResponse}，而不是 {@code LinkedHashMap}。
     * <p>
     * 分页是"活动列表""团队列表"等接口的通用返回结构，缓存它几乎是必然需求。
     * 而 Java 泛型在运行时会被擦除，Jackson 拿不到 {@code List<ActivityResponse>} 里的
     * 元素类型信息 —— 这正是需要靠 {@code @class} 才能救回来的场景。
     * 一旦这里退化成 {@code LinkedHashMap}，前端会收到一堆字段名对但取不出值的空对象。
     */
    @Test
    void shouldPreserveGenericElementTypeInPageResponse() {
        PageResponse<ActivityResponse> original = PageResponse.<ActivityResponse>builder()
                .pageNo(0)
                .pageSize(10)
                .totalElements(1L)
                .totalPages(1)
                .records(List.of(sampleActivity()))
                .build();

        Object restoredRaw = serializer.deserialize(serializer.serialize(original));

        PageResponse<?> restored = assertInstanceOf(PageResponse.class, restoredRaw);
        assertEquals(1, restored.getRecords().size());
        assertInstanceOf(ActivityResponse.class, restored.getRecords().get(0),
                "records 里的元素应该是 ActivityResponse，而不是 LinkedHashMap —— 泛型元素类型丢了");
        assertEquals(sampleActivity(), restored.getRecords().get(0));
    }

    /**
     * 性质四：写进 Redis 的是可读 JSON，且带 {@code @class} 类型标记。
     * <p>
     * 这条性质直接决定排障体验：用 {@code redis-cli get <key>} 就能看懂缓存里到底是什么。
     * 若换成 Spring Boot 默认的 JDK 序列化，这里看到的会是
     * {@code \xac\xed\x00\x05sr\x00...} 二进制乱码。
     */
    @Test
    void shouldWriteReadableJsonWithClassName() {
        String json = new String(serializer.serialize(sampleActivity()), StandardCharsets.UTF_8);

        assertTrue(json.startsWith("{"), "应该是 JSON 对象，实际为：" + json);
        assertTrue(json.contains("\"@class\""), "缺少 @class 类型标记，说明 default typing 没生效");
        assertTrue(json.contains("com.example.zhihuiqingtu.activity.vo.ActivityResponse"),
                "JSON 里应该能直接看到具体类名");
        assertTrue(json.contains("校园马拉松志愿服务"), "中文字段值应该可读，而不是被转义成乱码");
    }

    /**
     * 性质五（安全）：白名单之外的类一律拒绝反序列化。
     * <p>
     * default typing 的本质是"照着 JSON 里写的类名去加载类"。如果不加白名单，
     * 攻击者只要能往 Redis 里塞一个 key（例如通过某个没做好校验的缓存接口），
     * 就能让服务端加载任意类，触发经典的反序列化 gadget 链，最终执行任意代码。
     * <p>
     * 这里用 {@code java.lang.Runtime} 当靶子 —— 它是历史上最著名的
     * gadget 链入口之一，且明确不在 {@link RedisConfig} 的白名单里。
     */
    @Test
    void shouldRejectTypeOutsideAllowlist() {
        String malicious = "{\"@class\":\"java.lang.Runtime\"}";

        SerializationException ex = assertThrows(SerializationException.class,
                () -> serializer.deserialize(malicious.getBytes(StandardCharsets.UTF_8)));

        assertTrue(ex.getMessage() != null && ex.getMessage().contains("java.lang.Runtime"),
                "异常信息里应该点明是哪个类被拒绝，便于排查，实际为：" + ex.getMessage());
    }
}
