package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.activity.ActivityQueryRequest;
import com.example.zhihuiqingtu.dto.activity.ChangeActivitySignInRequest;
import com.example.zhihuiqingtu.dto.activity.ChangeActivityStatusRequest;
import com.example.zhihuiqingtu.dto.activity.CreateActivityRequest;
import com.example.zhihuiqingtu.dto.activity.UpdateActivityRequest;
import com.example.zhihuiqingtu.vo.activity.ActivityCoverUploadResponse;
import com.example.zhihuiqingtu.vo.activity.ActivityResponse;
import com.example.zhihuiqingtu.vo.activity.ActivityStockResponse;
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
     * <p>
     * 操作者取自当前登录身份（Spring Security 上下文），不接受参数传入 ——
     * 否则调用方可以传任意学号来越权删除。
     *
     * @param activityId 活动主键
     */
    void deleteActivity(Long activityId);

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

    /**
     * 查询热门活动榜（按累计报名人数降序）。
     * <p>
     * 数据源是 Redis ZSet {@code activity:hot:enroll}，只存 activityId，需要回表，补详情。
     * 榜单为空时返回空列表，<b>不报错</b> —— "还没有热门活动"是正常状态。
     *
     * @param limit 返回条数，1~50
     * @return 活动列表（已按热度降序）
     */
    List<ActivityResponse> listHotActivities(int limit);

    /**
     * 查询活动剩余名额。
     *
     * @param activityId 活动主键
     * @return 剩余名额信息
     */
    ActivityStockResponse getActivityStock(Long activityId);
}
