package com.example.zhihuiqingtu.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建会话请求。
 */
@Data
public class CreateConversationRequest {

    /** 标题；不传则使用默认标题。 */
    @Size(max = 50, message = "标题不能超过 50 字")
    private String title;
}
