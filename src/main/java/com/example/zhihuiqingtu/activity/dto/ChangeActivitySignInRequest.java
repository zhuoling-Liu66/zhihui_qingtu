package com.example.zhihuiqingtu.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.example.zhihuiqingtu.security.CurrentUser;

/**
 * 更新活动签到开关请求。
 * <p>
 * 注意：没有"操作人"字段。操作者由服务端从 JWT 取当前登录用户
 * （见 {@code CurrentUser}），不接受客户端自报。
 */
@Data
public class ChangeActivitySignInRequest {

    /**
     * 是否开启签到，0-关闭 1-开启。
     */
    @NotNull(message = "签到开关不能为空")
    private Integer signInEnabled;
}
