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
     * <p>
     * 可见范围由服务端按角色判定，客户端伪造不了：
     * 管理员/老师可见全部并按学号筛选；学生发布者只能看自己发布的活动产生的记录；
     * 普通学生被强制改写成"当前登录用户"，只会返回自己的学分记录。
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
     * <p>
     * 请求体里已不再有 {@code verifyUserId}：审核人 = 当前登录用户，
     * 由服务层从 {@code CurrentUser} 读取，客户端无法指定。
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
     * 查询学分统计信息。
     * <p>
     * {@code userId} 是<b>可选的目标用户</b>，不是身份：管理员/老师传它可查指定学生，
     * 不传则查自己；普通用户传了也会被服务端忽略（强制查自己）。
     * 也就是说这个参数只是"缩小范围"的输入，可见范围始终由 JWT 里的角色决定。
     *
     * @param userId 目标用户学号/工号（可选；仅管理员/老师生效）
     * @return 学分统计结果
     */
    @GetMapping("/statistics")
    public ApiResponse<CreditStatisticsResponse> getStatistics(
            @RequestParam(required = false) String userId) {
        return ApiResponse.success(creditService.getStatistics(userId));
    }
}
