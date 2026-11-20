package com.example.zhihuiqingtu.admin.enrollment.controller;

import com.example.zhihuiqingtu.admin.enrollment.dto.AdminEnrollmentQueryRequest;
import com.example.zhihuiqingtu.admin.enrollment.service.AdminEnrollmentService;
import com.example.zhihuiqingtu.admin.enrollment.vo.AdminEnrollmentVO;
import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台报名查询接口。
 */
@RestController
@RequestMapping("/api/admin/enrollments")
@RequiredArgsConstructor
public class AdminEnrollmentController {

    private final AdminEnrollmentService adminEnrollmentService;

    /**
     * 分页查询全部报名记录。
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminEnrollmentVO>> queryEnrollments(
            @ModelAttribute AdminEnrollmentQueryRequest request) {
        return ApiResponse.success(adminEnrollmentService.queryEnrollments(request));
    }
}
