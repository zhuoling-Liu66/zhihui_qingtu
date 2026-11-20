package com.example.zhihuiqingtu.admin.publisher.controller;

import com.example.zhihuiqingtu.admin.publisher.dto.AdminPublisherApplicationQueryRequest;
import com.example.zhihuiqingtu.admin.publisher.dto.AdminReviewPublisherApplicationRequest;
import com.example.zhihuiqingtu.admin.publisher.service.AdminPublisherApplicationService;
import com.example.zhihuiqingtu.admin.publisher.vo.AdminPublisherApplicationVO;
import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台发布权限审批接口。
 */
@RestController
@RequestMapping("/api/admin/publisher-applications")
@RequiredArgsConstructor
public class AdminPublisherApplicationController {

    private final AdminPublisherApplicationService adminPublisherApplicationService;

    /**
     * 分页查询发布权限申请/变更记录。
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminPublisherApplicationVO>> queryApplications(
            @ModelAttribute AdminPublisherApplicationQueryRequest request) {
        return ApiResponse.success(
                adminPublisherApplicationService.queryApplications(request));
    }

    /**
     * 审批一条学生申请。
     */
    @PatchMapping("/{recordId}/review")
    public ApiResponse<AdminPublisherApplicationVO> review(
            @PathVariable Long recordId,
            @Valid @RequestBody AdminReviewPublisherApplicationRequest request) {
        return ApiResponse.success("审批完成",
                adminPublisherApplicationService.review(recordId, request));
    }
}
