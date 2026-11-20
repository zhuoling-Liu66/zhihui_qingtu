package com.example.zhihuiqingtu.admin.award.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 应用 AI 导入结果：把预览里勾选的参与者金额写入发放记录。
 */
@Data
public class ApplyAwardImportRequest {

    @NotEmpty(message = "请选择要保存的行")
    @Valid
    private List<Item> rows;

    @Data
    public static class Item {

        @NotNull(message = "报名记录不能为空")
        private Long enrollId;

        /**
         * 金额：学分活动传学分，志愿活动传小时。
         */
        @NotNull(message = "金额不能为空")
        private BigDecimal value;
    }
}
