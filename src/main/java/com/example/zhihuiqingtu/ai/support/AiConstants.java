package com.example.zhihuiqingtu.ai.support;

/**
 * AI 模块常量。
 *
 * <p>集中定义字符串取值，避免各处手写 {@code "PENDING"} 时拼错——拼错的后果很隐蔽：
 * 状态机判断永远不成立，表现为"确认按钮点了没反应"。
 */
public final class AiConstants {

    private AiConstants() {
    }

    /** 消息角色。 */
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";
    public static final String ROLE_SYSTEM = "system";

    /** 待确认动作状态。 */
    public static final String ACTION_PENDING = "PENDING";
    public static final String ACTION_EXECUTED = "EXECUTED";
    public static final String ACTION_CANCELLED = "CANCELLED";
    public static final String ACTION_EXPIRED = "EXPIRED";
    public static final String ACTION_FAILED = "FAILED";

    /** 动作类型。 */
    public static final String TYPE_ENROLL_ACTIVITY = "ENROLL_ACTIVITY";
    public static final String TYPE_JOIN_TEAM = "JOIN_TEAM";

    /** 推荐场景。 */
    public static final String SCENE_HOME = "HOME";
    public static final String SCENE_CHAT = "CHAT";
    public static final String SCENE_FILTER = "FILTER";
    public static final String SCENE_PERSONALIZED = "PERSONALIZED";

    /** 推荐目标类型。 */
    public static final String TARGET_ACTIVITY = "ACTIVITY";
    public static final String TARGET_TEAM = "TEAM";

    /** 默认会话标题。 */
    public static final String DEFAULT_TITLE = "新对话";

    /** 会话状态。 */
    public static final int CONVERSATION_ACTIVE = 1;

    /** 动作ID前缀。 */
    public static final String ACTION_ID_PREFIX = "act_";
}
