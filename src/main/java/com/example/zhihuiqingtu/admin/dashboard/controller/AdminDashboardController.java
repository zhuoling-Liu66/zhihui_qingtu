package com.example.zhihuiqingtu.admin.dashboard.controller;

import com.example.zhihuiqingtu.admin.dashboard.service.AdminDashboardService;
import com.example.zhihuiqingtu.admin.dashboard.vo.AdminDashboardVO;
import com.example.zhihuiqingtu.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台概览接口。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /**
     * 查询管理后台概览统计。
     */
    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardVO> overview() {
        return ApiResponse.success(adminDashboardService.overview());
    }
}
