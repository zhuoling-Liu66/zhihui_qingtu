package com.example.zhihuiqingtu.user.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 更新用户兴趣标签请求。
 */
@Data
public class UpdateInterestTagsRequest {

    /**
     * 标签ID列表。
     */
    @NotEmpty(message = "兴趣标签不能为空")
    private List<Long> tagIds;
}
