package com.example.zhihuiqingtu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 活动标签实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("activity_tag")
@EqualsAndHashCode(callSuper = true)
public class ActivityTag extends BaseEntity {

    /**
     * 标签主键。
     */
    @TableId(value = "tag_id", type = IdType.AUTO)
    private Long tagId;

    /**
     * 标签名称。
     */
    @TableField("tag_name")
    private String tagName;

    /**
     * 标签描述。
     */
    @TableField("tag_desc")
    private String tagDesc;
}
