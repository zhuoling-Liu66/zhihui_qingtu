package com.example.zhihuiqingtu.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * AI 助手配置（前缀 {@code campus.ai}）。
 *
 * <p>放在独立命名空间而不是 {@code spring.ai.*}：后者是框架的配置，
 * 前者是本项目的业务约束（历史窗口、工具上限、配额），两者的生命周期和负责人不同。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.ai")
public class AiProperties {

    /** 总开关；关闭后聊天接口返回 503，推荐位仍可用。 */
    private boolean enabled = true;

    /** 未配置真实 API Key 时使用的占位符，用于判断"AI 是否真的可用"。 */
    private String apiKeyPlaceholder = "local-placeholder";

    private Chat chat = new Chat();
    private Tool tool = new Tool();
    private Quota quota = new Quota();
    private RateLimit ratelimit = new RateLimit();
    private Action action = new Action();

    @Data
    public static class Chat {
        /** SSE 整体超时。 */
        private Duration timeout = Duration.ofSeconds(90);
        /** 首 token 超时：超过就认为模型无响应。 */
        private Duration firstTokenTimeout = Duration.ofSeconds(30);
        /** 回灌给模型的历史消息条数。 */
        private int historyWindow = 10;
        /** 单轮最多几轮工具调用。 */
        private int maxToolRounds = 4;
        /** 单轮最多给模型多少条候选活动。 */
        private int maxCandidates = 20;
        /** 首页推荐位默认条数。 */
        private int recommendLimit = 6;
    }

    @Data
    public static class Tool {
        /** 单个工具执行超时。 */
        private Duration timeout = Duration.ofSeconds(3);
        /** 单个工具返回内容的最大字符数（防止撑爆 prompt）。 */
        private int maxResultChars = 4000;
    }

    @Data
    public static class Quota {
        /** 每人每日 token 上限。 */
        private long dailyTokens = 100_000L;
    }

    @Data
    public static class RateLimit {
        /** 每人每分钟对话次数上限。 */
        private int perMinute = 10;
    }

    @Data
    public static class Action {
        /** 待确认动作的有效期。 */
        private Duration ttl = Duration.ofMinutes(5);
    }
}
