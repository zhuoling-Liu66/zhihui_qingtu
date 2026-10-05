package com.example.zhihuiqingtu.controller;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.activity.ActivityQueryRequest;
import com.example.zhihuiqingtu.dto.activity.ChangeActivitySignInRequest;
import com.example.zhihuiqingtu.dto.activity.ChangeActivityStatusRequest;
import com.example.zhihuiqingtu.dto.activity.CreateActivityRequest;
import com.example.zhihuiqingtu.dto.activity.UpdateActivityRequest;
import com.example.zhihuiqingtu.service.ActivityService;
import com.example.zhihuiqingtu.vo.activity.ActivityCoverUploadResponse;
import com.example.zhihuiqingtu.vo.activity.ActivityResponse;
import com.example.zhihuiqingtu.vo.activity.ActivityStockResponse;
import com.example.zhihuiqingtu.vo.activity.ActivityTagVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 活动模块控制器。
 * <p>
 * 这一层负责“接收前端请求”和“把请求转交给 Service”。
 * 它自己通常不写复杂业务逻辑，而是做参数接收、接口映射和结果返回。
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    /**
     * 活动业务服务。
     */
    private final ActivityService activityService;

    /**
     * 上传活动封面。
     *
     * @param file 前端上传的图片文件
     * @return 封面访问地址
     */
    @PostMapping("/covers")
    public ApiResponse<ActivityCoverUploadResponse> uploadActivityCover(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success("活动封面上传成功", activityService.uploadActivityCover(file));
    }

    /**
     * 创建活动。
     *
     * @param request 创建活动请求体
     * @return 创建后的活动详情
     */
    @PostMapping
    public ApiResponse<ActivityResponse> createActivity(@Valid @RequestBody CreateActivityRequest request) {
        return ApiResponse.success("活动创建成功", activityService.createActivity(request));
    }

    /**
     * 更新活动信息。
     *
     * @param activityId 活动主键
     * @param request    更新请求体
     * @return 更新后的活动详情
     */
    @PutMapping("/{activityId}")
    public ApiResponse<ActivityResponse> updateActivity(
            @PathVariable Long activityId,
            @Valid @RequestBody UpdateActivityRequest request) {
        return ApiResponse.success("活动更新成功", activityService.updateActivity(activityId, request));
    }

    /**
     * 修改活动状态。
     *
     * @param activityId 活动主键
     * @param request    状态变更请求体
     * @return 修改后的活动详情
     */
    @PatchMapping("/{activityId}/status")
    public ApiResponse<ActivityResponse> changeActivityStatus(
            @PathVariable Long activityId,
            @Valid @RequestBody ChangeActivityStatusRequest request) {
        return ApiResponse.success("活动状态更新成功",
                activityService.changeActivityStatus(activityId, request));
    }

    /**
     * 开启或关闭签到开关。
     *
     * @param activityId 活动主键
     * @param request    签到开关请求体
     * @return 修改后的活动详情
     */
    @PatchMapping("/{activityId}/sign-in-switch")
    public ApiResponse<ActivityResponse> updateSignInSwitch(
            @PathVariable Long activityId,
            @Valid @RequestBody ChangeActivitySignInRequest request) {
        return ApiResponse.success("签到开关更新成功", activityService.updateSignInSwitch(activityId, request));
    }

    /**
     * 删除活动。
     * <p>
     * 不再需要 {@code operatorUserId} 查询参数：操作者身份由 Spring Security 从 JWT 解析，
     * 服务层通过 {@code CurrentUser} 读取。少一个可伪造的入参，就少一处越权面。
     *
     * @param activityId 活动主键
     * @return 空响应体
     */
    @DeleteMapping("/{activityId}")
    public ApiResponse<Void> deleteActivity(@PathVariable Long activityId) {
        activityService.deleteActivity(activityId);
        return ApiResponse.success("活动删除成功", null);
    }

    /**
     * 按条件分页查询活动列表。
     *
     * @param request 查询条件
     * @return 分页活动列表
     */
    @GetMapping
    public ApiResponse<PageResponse<ActivityResponse>> queryActivities(@ModelAttribute ActivityQueryRequest request) {
        return ApiResponse.success(activityService.queryActivities(request));
    }

    /**
     * 查询活动详情。
     *
     * @param activityId 活动主键
     * @return 活动详情
     */
    @GetMapping("/{activityId}")
    public ApiResponse<ActivityResponse> getActivityDetail(@PathVariable Long activityId) {
        return ApiResponse.success(activityService.getActivityDetail(activityId));

    }

    /**
     * 查询所有可选活动标签。
     *
     * @return 标签列表
     */
    @GetMapping("/tags")
    public ApiResponse<List<ActivityTagVO>> listActivityTags() {
        return ApiResponse.success(activityService.listActivityTags());
    }

    /**
     * 热门活动榜（按累计报名人数降序）。
     * <p>
     * 数据来自 Redis ZSet {@code activity:hot:enroll}。
     * <p>
     * <b>注意路径不能和 {@code /{activityId}} 冲突</b>：Spring MVC 的路径匹配里，
     * 字面量段 {@code /hot} 的优先级高于变量段 {@code /{activityId}}，
     * 所以 {@code GET /api/activities/hot} 会进这个方法，而不是被当成 {@code activityId="hot"}。
     * 但 {@code /hot} 必须能转成 Long 才安全 —— 这里靠的是字面量优先，不是靠转换失败。
     *
     * @param limit 返回条数，默认 10，取值 1~50
     * @return 活动列表
     */
    @GetMapping("/hot")
    public ApiResponse<List<ActivityResponse>> listHotActivities(
            @RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        // 上限 50 是为了防止一次拉太多导致回表查询过重（limit 直接决定 IN (...) 里的元素个数）
        int safeLimit = limit == null || limit < 1 ? 10 : Math.min(limit, 50);
        return ApiResponse.success(activityService.listHotActivities(safeLimit));
    }

    /**
     * 查询活动剩余名额（前端展示"仅剩 N 个名额"的紧迫感提示）。
     *
     * @param activityId 活动主键
     * @return 剩余名额信息
     */
    @GetMapping("/{activityId}/stock")
    public ApiResponse<ActivityStockResponse> getActivityStock(@PathVariable Long activityId) {
        return ApiResponse.success(activityService.getActivityStock(activityId));
    }
}