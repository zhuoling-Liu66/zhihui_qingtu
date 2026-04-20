package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.activity.ActivityQueryRequest;
import com.example.zhihuiqingtu.dto.activity.ChangeActivitySignInRequest;
import com.example.zhihuiqingtu.dto.activity.ChangeActivityStatusRequest;
import com.example.zhihuiqingtu.dto.activity.CreateActivityRequest;
import com.example.zhihuiqingtu.dto.activity.UpdateActivityRequest;
import com.example.zhihuiqingtu.vo.activity.ActivityCoverUploadResponse;
import com.example.zhihuiqingtu.vo.activity.ActivityResponse;
import com.example.zhihuiqingtu.vo.activity.ActivityTagVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 活动业务服务接口。
 * <p>
 * Service 层负责承接控制器传来的请求，并编排真正的业务逻辑。
 * 接口的存在是为了让“控制器依赖抽象，而不是依赖具体实现类”。
 */
public interface ActivityService {

    /**
     * 上传活动封面。
     *
     * @param file 图片文件
     * @return 封面访问地址
     */
    ActivityCoverUploadResponse uploadActivityCover(MultipartFile file);

    /**
     * 创建活动。
     *
     * @param request 创建活动请求
     * @return 创建后的活动详情
     */
    ActivityResponse createActivity(CreateActivityRequest request);

    /**
     * 更新活动。
     *
     * @param activityId 活动主键
     * @param request    更新请求
     * @return 更新后的活动详情
     */
    ActivityResponse updateActivity(Long activityId, UpdateActivityRequest request);

    /**
     * 修改活动状态。
     *
     * @param activityId 活动主键
     * @param request    状态变更请求
     * @return 修改后的活动详情
     */
    ActivityResponse changeActivityStatus(Long activityId, ChangeActivityStatusRequest request);

    /**
     * 开启或关闭活动签到。
     *
     * @param activityId 活动主键
     * @param request    签到开关请求
     * @return 修改后的活动详情
     */
    ActivityResponse updateSignInSwitch(Long activityId, ChangeActivitySignInRequest request);

    /**
     * 删除活动。
     *
     * @param activityId      活动主键
     * @param operatorUserId  操作者学号/工号
     */
    void deleteActivity(Long activityId, String operatorUserId);

    /**
     * 分页查询活动列表。
     *
     * @param request 查询条件
     * @return 分页活动结果
     */
    PageResponse<ActivityResponse> queryActivities(ActivityQueryRequest request);

    /**
     * 查询活动详情。
     *
     * @param activityId 活动主键
     * @return 活动详情
     */
    ActivityResponse getActivityDetail(Long activityId);

    /**
     * 查询全部活动标签。
     *
     * @return 标签列表
     */
    List<ActivityTagVO> listActivityTags();
}
