package com.example.zhihuiqingtu.publisher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import com.example.zhihuiqingtu.common.entity.BaseEntity;

/**
 * 发布权限申请/变更记录实体。
 *
 * <p>一条记录代表一次“学生申请”或“管理员直接开通/收回”。
 * 学生申请待管理员处理后，会写入审批人、审批时间与驳回原因；
 * 管理员在用户管理里的开关也会落一条记录，避免权限变更没有留痕。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("publisher_permission_record")
@EqualsAndHashCode(callSuper = true)
public class PublisherPermissionRecord extends BaseEntity {

    /**
     * 记录主键。
     */
    @TableId(value = "record_id", type = IdType.AUTO)
    private Long recordId;

    /**
     * 目标用户学号或工号。
     */
    @TableField("student_no")
    private String studentNo;

    /**
     * 记录类型：1 学生申请 / 2 管理员开通 / 3 管理员收回。
     */
    @TableField("record_type")
    private Integer recordType;

    /**
     * 记录状态：0 待审批 / 1 已通过 / 2 已驳回 / 3 已关闭。
     */
    @TableField("record_status")
    private Integer recordStatus;

    /**
     * 申请理由；管理员直接调整时作为操作说明。
     */
    @TableField("apply_reason")
    private String applyReason;

    /**
     * 审批人或操作管理员学号。
     */
    @TableField("operator_user_id")
    private String operatorUserId;

    /**
     * 审批/操作时间。
     */
    @TableField("operate_time")
    private LocalDateTime operateTime;

    /**
     * 驳回或关闭原因。
     */
    @TableField("reject_reason")
    private String rejectReason;
}
