package com.example.zhihuiqingtu.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.example.zhihuiqingtu.common.entity.BaseEntity;
import com.example.zhihuiqingtu.user.enums.AccountStatusEnum;

/**
 * 用户信息实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_info")
@EqualsAndHashCode(callSuper = true)
public class UserInfo extends BaseEntity {

    /**
     * 学号或工号，作为用户主键。
     */
    @TableId(value = "student_no", type = IdType.INPUT)
    private String studentNo;

    /**
     * 姓名。
     */
    @TableField("user_name")
    private String userName;

    /**
     * 手机号。
     */
    @TableField("user_phone")
    private String userPhone;

    /**
     * 邮箱。
     */
    @TableField("user_email")
    private String userEmail;

    /**
     * 基础角色。
     */
    @TableField("user_role")
    private Integer userRole;

    /**
     * 发布权限状态。
     */
    @TableField("publisher_status")
    private Integer publisherStatus;

    /**
     * 账号状态：0-正常 1-禁用，见 {@code enums/AccountStatusEnum}。
     *
     * <p>与 {@code publisherStatus}（能不能发布活动）是两回事：
     * 禁用是账号级封禁，登录与已签发令牌都会立即失效。
     * 注销（软删除）走 {@code BaseEntity.deleteFlag}，不使用本字段。
     */
    @TableField("account_status")
    private Integer accountStatus;

    /**
     * 专业。
     */
    @TableField("major")
    private String major;

    /**
     * 年级。
     */
    @TableField("grade")
    private String grade;

    /**
     * 密码摘要。
     */
    @TableField("password_hash")
    private String passwordHash;
}
