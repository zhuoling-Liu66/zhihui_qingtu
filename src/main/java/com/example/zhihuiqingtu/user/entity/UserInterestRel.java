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

/**
 * 用户兴趣标签关联实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_interest_rel")
@EqualsAndHashCode(callSuper = true)
public class UserInterestRel extends BaseEntity {

    /**
     * 关联主键。
     */
    @TableId(value = "rel_id", type = IdType.AUTO)
    private Long relId;

    /**
     * 用户学号或工号。
     */
    @TableField("user_id")
    private String userId;

    /**
     * 标签ID。
     */
    @TableField("tag_id")
    private Long tagId;
}
