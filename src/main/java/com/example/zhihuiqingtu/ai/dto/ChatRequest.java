package com.example.zhihuiqingtu.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 对话请求。
 */
@Data
public class ChatRequest {

    /** 会话ID；不传则自动建会话。 */
    private Long conversationId;

    /** 用户消息。 */
    @NotBlank(message = "消息不能为空")
    @Size(max = 2000, message = "消息不能超过 2000 字")
    private String message;

    /** 页面上下文（从活动/队伍详情页进入时带上）。 */
    @Valid
    private ChatContext context;
}
