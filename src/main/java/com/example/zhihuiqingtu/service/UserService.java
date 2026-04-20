package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.dto.user.UpdateInterestTagsRequest;
import com.example.zhihuiqingtu.dto.user.UpdateUserProfileRequest;
import com.example.zhihuiqingtu.vo.user.UserActivityHistoryVO;
import com.example.zhihuiqingtu.vo.user.UserCreditOverviewResponse;
import com.example.zhihuiqingtu.vo.user.UserProfileResponse;

import java.util.List;

/**
 * 用户业务服务接口。
 */
public interface UserService {

    /**
     * 查询用户资料。
     *
     * @param userId 用户学号/工号
     * @return 用户资料
     */
    UserProfileResponse getUserProfile(String userId);

    /**
     * 更新用户资料。
     *
     * @param userId  用户学号/工号
     * @param request 更新请求参数
     * @return 更新后的用户资料
     */
    UserProfileResponse updateUserProfile(String userId, UpdateUserProfileRequest request);

    /**
     * 更新用户兴趣标签。
     *
     * @param userId  用户学号/工号
     * @param request 兴趣标签更新请求参数
     * @return 更新后的用户资料
     */
    UserProfileResponse updateInterestTags(String userId, UpdateInterestTagsRequest request);

    /**
     * 查询用户活动历史。
     *
     * @param userId 用户学号/工号
     * @return 活动历史列表
     */
    List<UserActivityHistoryVO> getActivityHistories(String userId);

    /**
     * 查询用户学分总览。
     *
     * @param userId 用户学号/工号
     * @return 学分总览
     */
    UserCreditOverviewResponse getCreditOverview(String userId);
}
