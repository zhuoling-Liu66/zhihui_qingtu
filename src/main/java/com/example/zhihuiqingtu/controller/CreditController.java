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
 * 学分模块控制器。
 * <p>
 * 负责学分记录查询、审核和统计相关接口。
 */
@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
public class CreditController {

    /**
     * 学分业务服务。
     */
    private final CreditService creditService;

    /**
     * 分页查询学分记录。
     *
     * @param request 查询条件
     * @return 学分记录分页结果
     */
    @GetMapping
    public ApiResponse<PageResponse<CreditRecordResponse>> queryCredits(@ModelAttribute CreditQueryRequest request) {
        return ApiResponse.success(creditService.queryCredits(request));
    }

    /**
     * 审核某一条学分记录。
     *
     * @param recordId 学分记录主键
     * @param request  审核请求体
     * @return 审核后的记录详情
     */
    @PatchMapping("/{recordId}/review")
    public ApiResponse<CreditRecordResponse> reviewCredit(
            @PathVariable Long recordId,
            @Valid @RequestBody ReviewCreditRequest request) {
        return ApiResponse.success("学分审核完成", creditService.reviewCredit(recordId, request));
    }

    /**
     * 查询某个用户的学分统计信息。
     *
     * @param userId 用户学号/工号
     * @return 学分统计结果
     */
    @GetMapping("/statistics")
    public ApiResponse<CreditStatisticsResponse> getStatistics(@RequestParam String userId) {
        return ApiResponse.success(creditService.getStatistics(userId));
    }
}
