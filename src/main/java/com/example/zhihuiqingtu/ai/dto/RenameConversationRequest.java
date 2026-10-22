package com.example.zhihuiqingtu.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重命名会话请求。
 */
@Data
public class RenameConversationRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 50, message = "标题不能超过 50 字")
    private String title;
}
