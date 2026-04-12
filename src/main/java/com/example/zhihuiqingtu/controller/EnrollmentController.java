package com.example.zhihuiqingtu.controller;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.enrollment.CreateEnrollmentRequest;
import com.example.zhihuiqingtu.dto.enrollment.EnrollmentQueryRequest;
import com.example.zhihuiqingtu.dto.enrollment.FeedbackEnrollmentRequest;
import com.example.zhihuiqingtu.service.EnrollmentService;
import com.example.zhihuiqingtu.vo.enrollment.EnrollmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 参与域接口控制器。
 */
@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    /**
     * 活动报名。
     */
    @PostMapping
    public ApiResponse<EnrollmentResponse> createEnrollment(@Valid @RequestBody CreateEnrollmentRequest request) {
        return ApiResponse.success("报名成功", enrollmentService.createEnrollment(request));
    }

    /**
     * 取消报名。
     */
    @DeleteMapping("/{enrollId}")
    public ApiResponse<EnrollmentResponse> cancelEnrollment(
            @PathVariable Long enrollId,
            @RequestParam String userId) {
        return ApiResponse.success("取消报名成功", enrollmentService.cancelEnrollment(enrollId, userId));
    }

    /**
     * 参与者自助签到。
     */
    @PatchMapping("/{enrollId}/sign-in")
    public ApiResponse<EnrollmentResponse> signIn(
            @PathVariable Long enrollId,
            @RequestParam String userId) {
        return ApiResponse.success("签到成功", enrollmentService.signIn(enrollId, userId));
    }

    /**
     * 提交活动评价。
     */
    @PatchMapping("/{enrollId}/feedback")
    public ApiResponse<EnrollmentResponse> feedback(
            @PathVariable Long enrollId,
            @Valid @RequestBody FeedbackEnrollmentRequest request) {
        return ApiResponse.success("评价提交成功", enrollmentService.feedback(enrollId, request));
    }

    /**
     * 查询报名记录。
     */
    @GetMapping
    public ApiResponse<PageResponse<EnrollmentResponse>> queryEnrollments(@ModelAttribute EnrollmentQueryRequest request) {
        return ApiResponse.success(enrollmentService.queryEnrollments(request));
    }
}
