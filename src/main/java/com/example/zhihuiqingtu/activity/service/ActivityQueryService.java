package com.example.zhihuiqingtu.activity.service;

import com.example.zhihuiqingtu.activity.dto.ActivityQueryRequest;
import com.example.zhihuiqingtu.activity.vo.ActivityResponse;
import com.example.zhihuiqingtu.activity.vo.ActivityStockResponse;
import com.example.zhihuiqingtu.activity.vo.ActivityTagVO;
import com.example.zhihuiqingtu.common.PageResponse;

import java.util.List;

/**
 * 活动只读查询服务。
 */
public interface ActivityQueryService {

    PageResponse<ActivityResponse> queryActivities(ActivityQueryRequest request);

    ActivityResponse getActivityDetail(Long activityId);

    List<ActivityTagVO> listActivityTags();

    List<ActivityResponse> listHotActivities(int limit);

    ActivityStockResponse getActivityStock(Long activityId);
}
