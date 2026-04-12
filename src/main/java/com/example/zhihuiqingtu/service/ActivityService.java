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
 * 活动服务接口。
 */
public interface ActivityService {

    ActivityCoverUploadResponse uploadActivityCover(MultipartFile file);

    ActivityResponse createActivity(CreateActivityRequest request);

    ActivityResponse updateActivity(Long activityId, UpdateActivityRequest request);

    ActivityResponse changeActivityStatus(Long activityId, ChangeActivityStatusRequest request);

    ActivityResponse updateSignInSwitch(Long activityId, ChangeActivitySignInRequest request);

    void deleteActivity(Long activityId, String operatorUserId);

    PageResponse<ActivityResponse> queryActivities(ActivityQueryRequest request);

    ActivityResponse getActivityDetail(Long activityId);

    List<ActivityTagVO> listActivityTags();
}
