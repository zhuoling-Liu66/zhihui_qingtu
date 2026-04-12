package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.dto.user.UpdateInterestTagsRequest;
import com.example.zhihuiqingtu.dto.user.UpdateUserProfileRequest;
import com.example.zhihuiqingtu.vo.user.UserActivityHistoryVO;
import com.example.zhihuiqingtu.vo.user.UserCreditOverviewResponse;
import com.example.zhihuiqingtu.vo.user.UserProfileResponse;

import java.util.List;

/**
 * 用户服务接口。
 */
public interface UserService {

    UserProfileResponse getUserProfile(String userId);

    UserProfileResponse updateUserProfile(String userId, UpdateUserProfileRequest request);

    UserProfileResponse updateInterestTags(String userId, UpdateInterestTagsRequest request);

    List<UserActivityHistoryVO> getActivityHistories(String userId);

    UserCreditOverviewResponse getCreditOverview(String userId);
}
