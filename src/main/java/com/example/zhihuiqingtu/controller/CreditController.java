package com.example.zhihuiqingtu.controller;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.credit.CreditQueryRequest;
import com.example.zhihuiqingtu.dto.credit.ReviewCreditRequest;
import com.example.zhihuiqingtu.service.CreditService;
import com.example.zhihuiqingtu.vo.credit.CreditRecordResponse;
import com.example.zhihuiqingtu.vo.credit.CreditStatisticsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学分域接口控制器。
 */
@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    /**
     * 查询学分记录列表。
     */
    @GetMapping
    public ApiResponse<PageResponse<CreditRecordResponse>> queryCredits(@ModelAttribute CreditQueryRequest request) {
        return ApiResponse.success(creditService.queryCredits(request));
    }

    /**
     * 审核学分记录。
     */
    @PatchMapping("/{recordId}/review")
    public ApiResponse<CreditRecordResponse> reviewCredit(
            @PathVariable Long recordId,
            @Valid @RequestBody ReviewCreditRequest request) {
        return ApiResponse.success("学分审核完成", creditService.reviewCredit(recordId, request));
    }

    /**
     * 查询用户学分统计。
     */
    @GetMapping("/statistics")
    public ApiResponse<CreditStatisticsResponse> getStatistics(@RequestParam String userId) {
        return ApiResponse.success(creditService.getStatistics(userId));
    }
}
