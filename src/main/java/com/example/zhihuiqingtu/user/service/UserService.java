package com.example.zhihuiqingtu.user.service;

import com.example.zhihuiqingtu.user.dto.UpdateInterestTagsRequest;
import com.example.zhihuiqingtu.user.dto.UpdateUserProfileRequest;
import com.example.zhihuiqingtu.user.vo.UserActivityHistoryVO;
import com.example.zhihuiqingtu.user.vo.UserCreditOverviewResponse;
import com.example.zhihuiqingtu.user.vo.UserProfileResponse;

import java.util.List;

/**
 * 用户业务服务接口。
 * <p>
 * <b>身份来源说明（安全迁移）：</b>本接口各方法的 {@code userId} 参数含义不同，不能一刀切：
 * <ul>
 *   <li>{@link #getUserProfile(String)} —— {@code userId} 是<b>查询目标</b>（看别人的公开资料），保留；</li>
 *   <li>{@link #updateUserProfile(String, UpdateUserProfileRequest)}、
 *       {@link #updateInterestTags(String, UpdateInterestTagsRequest)} —— {@code userId} 是<b>归属</b>，
 *       只允许等于当前登录用户（4030），管理员也不能代改；</li>
 *   <li>{@link #getActivityHistories(String)}、{@link #getCreditOverview(String)} —— {@code userId}
 *       是<b>查询目标但属个人数据</b>：管理员/老师可查他人，普通用户只能查自己（4030）。</li>
 * </ul>
 * 之所以这几处保留 {@code userId} 参数而不是直接删掉：路由是
 * {@code /api/users/{userId}/...}，与 {@code /api/users/{userId}} 共用前缀，
 * 删掉路径段会出现 {@code /api/users/histories} 这类与 {@code /{userId}} 语义冲突的路由。
 * 真正的约束不在"参数存不存在"，而在服务端按角色判定归属。
 */
public interface UserService {

    /**
     * 查询用户资料（公开信息，可查任意用户）。
     * <p>
     * 保留 {@code userId} 参数：校园场景下"看某个活动发布者的公开资料"是合理需求。
     * 注意：返回的 {@code UserProfileResponse} 目前包含手机号、邮箱等敏感字段，
     * 这属于"公开接口泄露个人信息"的已知问题（本次迁移未改字段，仅记录）。
     *
     * @param userId 用户学号/工号
     * @return 用户资料
     */
    UserProfileResponse getUserProfile(String userId);

    /**
     * 更新用户资料（<b>只能改自己的</b>，管理员也不行）。
     * <p>
     * 原来 {@code userId} 直接用来定位要改的账号，客户端填别人的学号就能改别人的资料
     * （水平越权）。实现类现在会校验 {@code userId} 等于当前登录用户。
     *
     * @param userId  用户学号/工号（必须是自己）
     * @param request 更新请求参数
     * @return 更新后的用户资料
     */
    UserProfileResponse updateUserProfile(String userId, UpdateUserProfileRequest request);

    /**
     * 更新用户兴趣标签（<b>只能改自己的</b>，管理员也不行）。
     *
     * @param userId  用户学号/工号（必须是自己）
     * @param request 兴趣标签更新请求参数
     * @return 更新后的用户资料
     */
    UserProfileResponse updateInterestTags(String userId, UpdateInterestTagsRequest request);

    /**
     * 查询用户活动历史（个人数据：管理员/老师可查他人，普通用户只能查自己）。
     *
     * @param userId 目标用户学号/工号
     * @return 活动历史列表
     */
    List<UserActivityHistoryVO> getActivityHistories(String userId);

    /**
     * 查询用户学分总览（个人数据：管理员/老师可查他人，普通用户只能查自己）。
     *
     * @param userId 目标用户学号/工号
     * @return 学分总览
     */
    UserCreditOverviewResponse getCreditOverview(String userId);
}
