package com.example.zhihuiqingtu.activity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 活动封面上传返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityCoverUploadResponse {

    /**
     * 封面访问地址。
     */
    private String coverUrl;
}
