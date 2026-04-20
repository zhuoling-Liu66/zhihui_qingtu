package com.example.zhihuiqingtu.controller;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.dto.user.UpdateInterestTagsRequest;
import com.example.zhihuiqingtu.dto.user.UpdateUserProfileRequest;
import com.example.zhihuiqingtu.service.UserService;
import com.example.zhihuiqingtu.vo.user.UserActivityHistoryVO;
import com.example.zhihuiqingtu.vo.user.UserCreditOverviewResponse;
import com.example.zhihuiqingtu.vo.user.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户模块控制器。
 * <p>
 * 负责用户资料、兴趣标签、活动历史和学分总览等接口。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    /**
     * 用户业务服务。
     */
    private final UserService userService;

    /**
     * 查询用户资料。
     *
     * @param userId 用户学号/工号
     * @return 用户资料
     */
    @GetMapping("/{userId}")
    public ApiResponse<UserProfileResponse> getUserProfile(@PathVariable String userId) {
        return ApiResponse.success(userService.getUserProfile(userId));
    }

    /**
     * 更新用户资料。
     *
     * @param userId  用户学号/工号
     * @param request 更新资料请求体
     * @return 更新后的用户资料
     */
    @PutMapping("/{userId}")
    public ApiResponse<UserProfileResponse> updateUserProfile(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        return ApiResponse.success("更新成功", userService.updateUserProfile(userId, request));
    }

    /**
     * 更新用户兴趣标签。
     *
     * @param userId  用户学号/工号
     * @param request 兴趣标签更新请求体
     * @return 更新后的用户资料
     */
    @PutMapping("/{userId}/interest-tags")
    public ApiResponse<UserProfileResponse> updateInterestTags(
            @PathVariable String userId,
            @Valid @RequestBody UpdateInterestTagsRequest request) {
        return ApiResponse.success("兴趣标签更新成功", userService.updateInterestTags(userId, request));
    }

    /**
     * 查询用户活动历史。
     *
     * @param userId 用户学号/工号
     * @return 活动历史列表
     */
    @GetMapping("/{userId}/histories")
    public ApiResponse<List<UserActivityHistoryVO>> getActivityHistories(@PathVariable String userId) {
        return ApiResponse.success(userService.getActivityHistories(userId));
    }

    /**
     * 查询用户学分总览。
     *
     * @param userId 用户学号/工号
     * @return 学分总览
     */
    @GetMapping("/{userId}/credit-overview")
    public ApiResponse<UserCreditOverviewResponse> getCreditOverview(@PathVariable String userId) {
        return ApiResponse.success(userService.getCreditOverview(userId));
    }
}
