package com.example.zhihuiqingtu.admin.award.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 导入保存结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwardImportApplyResultVO {

    private int total;

    private int updated;

    private String message;
}
