package com.example.zhihuiqingtu.admin.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理端用户列表项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserVO {

    private String studentNo;

    private String userName;

    private String userPhone;

    private String userEmail;

    private Integer userRole;

    private Integer publisherStatus;

    /** 账号状态：0 正常 / 1 禁用。 */
    private Integer accountStatus;

    private String major;

    private String grade;

    private LocalDateTime createTime;
}
