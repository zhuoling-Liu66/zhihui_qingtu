package com.example.zhihuiqingtu.award.controller;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.award.dto.CreditQueryRequest;
import com.example.zhihuiqingtu.award.service.CreditService;
import com.example.zhihuiqingtu.award.vo.CreditRecordResponse;
import com.example.zhihuiqingtu.award.vo.CreditStatisticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
