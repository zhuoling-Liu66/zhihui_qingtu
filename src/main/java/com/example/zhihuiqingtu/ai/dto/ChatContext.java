package com.example.zhihuiqingtu.ai.dto;

import lombok.Data;

/**
 * 对话的页面上下文。
 *
 * <p>只放 ID，不放标题/状态：标题和状态必须由服务端回表取，
 * 否则客户端传什么模型就信什么，等于把"事实来源"交给了不可信输入。
 */
@Data
public class ChatContext {

    /** 当前正在浏览的活动。 */
    private Long activityId;

    /** 当前正在浏览的队伍。 */
    private Long teamId;
}
