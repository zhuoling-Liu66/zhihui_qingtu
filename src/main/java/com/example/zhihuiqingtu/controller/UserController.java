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
 * <p>
 * <b>安全迁移说明：</b>本控制器的 {@code userId} 是路径参数，含义按接口区分：
 * <ul>
 *   <li>{@code GET /{userId}} —— 查询目标（公开资料），保留；</li>
 *   <li>{@code PUT /{userId}}、{@code PUT /{userId}/interest-tags} —— 身份/归属，
 *       <b>服务层强制只能操作自己</b>（不一致抛 4030，管理员也不能代改）；</li>
 *   <li>{@code GET /{userId}/histories}、{@code GET /{userId}/credit-overview} ——
 *       个人数据：<b>管理员/老师可查他人，普通用户只能查自己</b>（服务层按角色判定）。</li>
 * </ul>
 * 这里不在控制器里判断权限，是为了让"归属校验"和"数据访问"待在同一个方法内，
 * 避免以后新增调用入口（定时任务、其他 Service）绕过校验。
 * 之所以不把这些路径段删掉：删掉后 PUT {@code /api/users/interest-tags} 会与
 * PUT {@code /api/users/{userId}} 路径语义冲突，GET {@code /api/users/histories}
 * 同理与 GET {@code /api/users/{userId}} 冲突，路由会变得难以预测。
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
     * 查询用户资料（公开信息，可查任意用户）。
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
     * <p>
     * 路径里的 {@code userId} 还是要传（路由需要），但服务层会校验它必须是自己：
     * 原来客户端把这里换成别人的学号就能改别人的资料，是可被伪造的越权点。
     *
     * @param userId  用户学号/工号（必须与当前登录用户一致）
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
     * <p>
     * 同 {@link #updateUserProfile(String, UpdateUserProfileRequest)}：服务层校验归属，只能改自己的。
     *
     * @param userId  用户学号/工号（必须与当前登录用户一致）
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
     * <p>
     * 报名历史属个人数据：服务层按角色判定 —— 管理员/老师可查他人，普通用户只能查自己。
     *
     * @param userId 目标用户学号/工号
     * @return 活动历史列表
     */
    @GetMapping("/{userId}/histories")
    public ApiResponse<List<UserActivityHistoryVO>> getActivityHistories(@PathVariable String userId) {
        return ApiResponse.success(userService.getActivityHistories(userId));
    }

    /**
     * 查询用户学分总览。
     * <p>
     * 学分属个人数据：服务层按角色判定 —— 管理员/老师可查他人，普通用户只能查自己。
     *
     * @param userId 目标用户学号/工号
     * @return 学分总览
     */
    @GetMapping("/{userId}/credit-overview")
    public ApiResponse<UserCreditOverviewResponse> getCreditOverview(@PathVariable String userId) {
        return ApiResponse.success(userService.getCreditOverview(userId));
    }
}
