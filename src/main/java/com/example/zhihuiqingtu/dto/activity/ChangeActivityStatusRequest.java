package com.example.zhihuiqingtu.dto.activity;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新活动状态请求。
 * <p>
 * <b>注意这里没有"操作人"字段</b>：操作者身份由 Spring Security 从 JWT 解析后写入
 * {@code SecurityContextHolder}，业务层通过 {@code CurrentUser} 读取，
 * 不接受客户端自报。删掉该字段比"加校验"更彻底——越权在编译期就无法表达。
 */
@Data
public class ChangeActivityStatusRequest {

    /**
     * 目标状态，仅支持已发布/已取消。
     */
    @NotNull(message = "活动状态不能为空")
    private Integer activityStatus;
}
