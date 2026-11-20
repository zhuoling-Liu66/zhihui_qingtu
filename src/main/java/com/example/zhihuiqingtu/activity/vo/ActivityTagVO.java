package com.example.zhihuiqingtu.activity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标签信息视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityTagVO {

    /**
     * 标签ID。
     */
    private Long tagId;

    /**
     * 标签名称。
     */
    private String tagName;
}
