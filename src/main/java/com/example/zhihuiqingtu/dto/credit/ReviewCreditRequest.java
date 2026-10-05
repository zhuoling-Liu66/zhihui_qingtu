package com.example.zhihuiqingtu.dto.credit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学分审核请求。
 * <p>
 * <b>安全说明：</b>本请求体原来有一个 {@code verifyUserId} 字段（"审核人学号"），
 * 那是客户端自报的身份 —— 任何人都能填成管理员或老师的学号，从而伪造审核记录
 * （越权：谁审的、什么时候审的都会变成假的，审计追溯也随之失效）。
 * 现已删除该字段：审核人一律从 Spring Security 上下文取当前登录用户
 * （服务层通过 {@code CurrentUser} 读取），请求体只保留"审核结论"这类业务数据。
 */
@Data
public class ReviewCreditRequest {

    /**
     * 审核状态。
     */
    @NotNull(message = "审核状态不能为空")
    private Integer verifyStatus;

    /**
     * 驳回原因。
     */
    @Size(max = 255, message = "驳回原因长度不能超过255")
    private String rejectReason;
}
