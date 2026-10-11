package com.example.zhihuiqingtu.common;

/**
 * 业务错误码。
 *
 * <p>取值范围与 HTTP 状态码对齐，同一个语义只有一种表达，前端与网关可按 HTTP 语义统一处理：
 * <ul>
 *   <li>{@link #PARAM_ERROR} 参数不合法、状态字段取值非法；</li>
 *   <li>{@link #UNAUTHORIZED} 凭证缺失或无效，应引导重新登录；</li>
 *   <li>{@link #FORBIDDEN} 身份有效但无权执行该操作，重新登录无效；</li>
 *   <li>{@link #NOT_FOUND} 目标资源不存在；</li>
 *   <li>{@link #CONFLICT} 目标资源当前状态不允许该操作（满员、重复、已结束等）；</li>
 *   <li>{@link #PAYLOAD_TOO_LARGE} 上传内容超出限制；</li>
 *   <li>{@link #TOO_MANY_REQUESTS} 触发频率限制；</li>
 *   <li>{@link #SYSTEM_ERROR} 未预期的服务端异常。</li>
 * </ul>
 *
 * <p><b>设计取舍</b>：HTTP 状态码数量有限，因此多个具体原因会归入同一码，
 * 例如"活动不存在""报名记录不存在""队伍不存在"同为 {@link #NOT_FOUND}。
 * 区分具体原因由 {@code message} 承担，客户端不应依赖提示文案做逻辑分支。
 * 若某场景需要精确区分，应新增独立接口或返回体字段，而不是重新引入自定义细粒度错误码。
 */
public final class ErrorCode {

    /** 参数不合法：字段取值非法、状态取值非法、时间区间非法等。 */
    public static final int PARAM_ERROR = 400;

    /** 凭证缺失或无效，需要重新登录。 */
    public static final int UNAUTHORIZED = 401;

    /** 身份有效但无权执行该操作。 */
    public static final int FORBIDDEN = 403;

    /** 目标资源不存在。 */
    public static final int NOT_FOUND = 404;

    /** 目标资源当前状态与该操作冲突：满员、重复、已结束、已取消等。 */
    public static final int CONFLICT = 409;

    /** 上传内容超出大小限制。 */
    public static final int PAYLOAD_TOO_LARGE = 413;

    /** 触发频率限制（如登录失败次数过多）。 */
    public static final int TOO_MANY_REQUESTS = 429;

    /** 未预期的服务端异常。 */
    public static final int SYSTEM_ERROR = 500;

    /** 成功。 */
    public static final int SUCCESS = 0;

    private ErrorCode() {
    }
}
