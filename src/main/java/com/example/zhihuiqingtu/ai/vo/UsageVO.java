package com.example.zhihuiqingtu.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * token 用量。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageVO {

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
}
