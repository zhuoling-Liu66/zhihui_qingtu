package com.example.zhihuiqingtu.publisher.controller;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.publisher.dto.ApplyPublisherRequest;
import com.example.zhihuiqingtu.publisher.service.PublisherApplicationService;
import com.example.zhihuiqingtu.publisher.vo.PublisherApplicationStatusResponse;
import com.example.zhihuiqingtu.publisher.vo.PublisherPermissionRecordVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端发布权限申请接口。
 *
 * <p>身份一律从 JWT 读取，不接受请求体传入学号，避免替他人提交申请。
 */
@RestController
@RequestMapping("/api/publisher-applications")
@RequiredArgsConstructor
public class PublisherApplicationController {

    private final PublisherApplicationService publisherApplicationService;

    /**
     * 提交开通活动发布权限的申请。
     */
    @PostMapping
    public ApiResponse<PublisherPermissionRecordVO> apply(
            @Valid @RequestBody ApplyPublisherRequest request) {
        return ApiResponse.success("申请已提交，等待管理员审批",
                publisherApplicationService.apply(request));
    }

    /**
     * 查询当前用户的发布权限与最近一次申请。
     */
    @GetMapping("/me")
    public ApiResponse<PublisherApplicationStatusResponse> myStatus() {
        return ApiResponse.success(publisherApplicationService.getMyStatus());
    }
}
