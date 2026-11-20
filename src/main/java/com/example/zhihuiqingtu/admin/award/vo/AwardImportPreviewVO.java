package com.example.zhihuiqingtu.admin.award.vo;

import com.example.zhihuiqingtu.activity.vo.CreditTierVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * AI 批量导入的预览结果（不写库）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwardImportPreviewVO {

    private Long activityId;

    private String activityTitle;

    private Integer activityType;

    /**
     * 本次导入的值类型：hours / credit。
     */
    private String valueKind;

    /**
     * 是否使用了模型识别（false 表示规则识别）。
     */
    private boolean aiUsed;

    private String aiNotes;

    /**
     * 识别到的表头行（1 基，展示用）。
     */
    private Integer headerRow;

    /**
     * 列映射：studentNo/name/hours/credit -> 表头文字。
     */
    private Map<String, String> mapping;

    /**
     * hour / min（分钟会在预览中换算成小时）。
     */
    private String hoursUnit;

    /**
     * 组合学分档位及占用情况（仅组合模式）。
     */
    private List<CreditTierVO> creditTiers;

    private Summary summary;

    private List<Row> rows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int total;
        private int matched;
        private int unmatched;
        private int invalid;
        private int skippedApproved;
        private int noRecord;
        private int duplicate;
        private int tierFull;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        /**
         * Excel 行号（1 基）。
         */
        private Integer rowNumber;
        private String name;
        private String studentNo;
        /**
         * 从表格解析出的金额（已做分钟换算）。
         */
        private BigDecimal rawValue;
        private Long enrollId;
        private String userId;
        private String matchedName;
        private BigDecimal currentValue;
        private Integer currentStatus;
        /**
         * UPDATE / SKIP_APPROVED / UNMATCHED / AMBIGUOUS / NO_RECORD / INVALID / DUPLICATE / TIER_FULL
         */
        private String action;
        private String reason;
        /**
         * 预览默认勾选（只有 UPDATE 行为 true）。
         */
        private Boolean selected;
    }
}
