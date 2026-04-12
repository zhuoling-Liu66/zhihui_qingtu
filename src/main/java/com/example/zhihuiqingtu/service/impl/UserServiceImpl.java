package com.example.zhihuiqingtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zhihuiqingtu.dto.user.UpdateInterestTagsRequest;
import com.example.zhihuiqingtu.dto.user.UpdateUserProfileRequest;
import com.example.zhihuiqingtu.entity.ActivityEnroll;
import com.example.zhihuiqingtu.entity.ActivityMain;
import com.example.zhihuiqingtu.entity.ActivityTag;
import com.example.zhihuiqingtu.entity.CreditRecord;
import com.example.zhihuiqingtu.entity.UserInfo;
import com.example.zhihuiqingtu.entity.UserInterestRel;
import com.example.zhihuiqingtu.enums.CreditTypeEnum;
import com.example.zhihuiqingtu.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.repository.ActivityTagRepository;
import com.example.zhihuiqingtu.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.repository.UserInfoRepository;
import com.example.zhihuiqingtu.repository.UserInterestRelRepository;
import com.example.zhihuiqingtu.service.UserService;
import com.example.zhihuiqingtu.vo.activity.ActivityTagVO;
import com.example.zhihuiqingtu.vo.user.UserActivityHistoryVO;
import com.example.zhihuiqingtu.vo.user.UserCreditOverviewResponse;
import com.example.zhihuiqingtu.vo.user.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户服务实现。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserInfoRepository userInfoRepository;
    private final UserInterestRelRepository userInterestRelRepository;
    private final ActivityTagRepository activityTagRepository;
    private final ActivityEnrollRepository activityEnrollRepository;
    private final ActivityMainRepository activityMainRepository;
    private final CreditRecordRepository creditRecordRepository;

    @Override
    public UserProfileResponse getUserProfile(String userId) {
        return buildUserProfile(requireUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateUserProfile(String userId, UpdateUserProfileRequest request) {
        UserInfo userInfo = requireUser(userId);
        validatePhoneAndEmail(userInfo, request);
        userInfo.setUserName(request.getUserName());
        userInfo.setUserPhone(request.getUserPhone());
        userInfo.setUserEmail(request.getUserEmail());
        userInfo.setMajor(request.getMajor());
        userInfo.setGrade(request.getGrade());
        userInfoRepository.updateById(userInfo);
        return buildUserProfile(userInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateInterestTags(String userId, UpdateInterestTagsRequest request) {
        UserInfo userInfo = requireUser(userId);
        List<Long> tagIds = new ArrayList<>(new LinkedHashSet<>(request.getTagIds()));
        validateInterestTags(tagIds);

        List<UserInterestRel> oldRelations = userInterestRelRepository.selectList(new LambdaQueryWrapper<UserInterestRel>()
                .eq(UserInterestRel::getUserId, userId));
        for (UserInterestRel relation : oldRelations) {
            userInterestRelRepository.deleteById(relation.getRelId());
        }

        for (Long tagId : tagIds) {
            userInterestRelRepository.insert(UserInterestRel.builder()
                    .userId(userId)
                    .tagId(tagId)
                    .build());
        }
        return buildUserProfile(userInfo);
    }

    @Override
    public List<UserActivityHistoryVO> getActivityHistories(String userId) {
        requireUser(userId);
        List<ActivityEnroll> enrollments = activityEnrollRepository.selectList(new LambdaQueryWrapper<ActivityEnroll>()
                .eq(ActivityEnroll::getUserId, userId)
                .orderByDesc(ActivityEnroll::getCreateTime));
        if (enrollments.isEmpty()) {
            return Collections.emptyList();
        }

        var activityIds = enrollments.stream().map(ActivityEnroll::getActivityId).collect(Collectors.toSet());
        Map<Long, ActivityMain> activityMap = activityMainRepository.selectBatchIds(activityIds).stream()
                .collect(Collectors.toMap(ActivityMain::getActivityId, Function.identity()));

        return enrollments.stream()
                .map(enrollment -> {
                    ActivityMain activity = activityMap.get(enrollment.getActivityId());
                    return UserActivityHistoryVO.builder()
                            .enrollId(enrollment.getEnrollId())
                            .activityId(enrollment.getActivityId())
                            .activityTitle(activity == null ? null : activity.getActivityTitle())
                            .activityType(activity == null ? null : activity.getActivityType())
                            .enrollStatus(enrollment.getEnrollStatus())
                            .startTime(activity == null ? null : activity.getStartTime())
                            .endTime(activity == null ? null : activity.getEndTime())
                            .build();
                })
                .toList();
    }

    @Override
    public UserCreditOverviewResponse getCreditOverview(String userId) {
        requireUser(userId);
        List<CreditRecord> creditRecords = creditRecordRepository.selectList(new LambdaQueryWrapper<CreditRecord>()
                .eq(CreditRecord::getUserId, userId)
                .eq(CreditRecord::getVerifyStatus, ReviewStatusEnum.APPROVED.getCode()));
        Map<Integer, BigDecimal> creditMap = new HashMap<>();
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (CreditRecord creditRecord : creditRecords) {
            totalCredit = totalCredit.add(creditRecord.getCredit());
            creditMap.merge(creditRecord.getCreditType(), creditRecord.getCredit(), BigDecimal::add);
        }
        return UserCreditOverviewResponse.builder()
                .userId(userId)
                .totalCredit(totalCredit)
                .innovationEntrepreneurshipCredit(creditMap.getOrDefault(CreditTypeEnum.INNOVATION_ENTREPRENEURSHIP.getCode(), BigDecimal.ZERO))
                .intellectualEducationCredit(creditMap.getOrDefault(CreditTypeEnum.INTELLECTUAL_EDUCATION.getCode(), BigDecimal.ZERO))
                .moralEducationCredit(creditMap.getOrDefault(CreditTypeEnum.MORAL_EDUCATION.getCode(), BigDecimal.ZERO))
                .physicalEducationCredit(creditMap.getOrDefault(CreditTypeEnum.PHYSICAL_EDUCATION.getCode(), BigDecimal.ZERO))
                .aestheticEducationCredit(creditMap.getOrDefault(CreditTypeEnum.AESTHETIC_EDUCATION.getCode(), BigDecimal.ZERO))
                .laborEducationCredit(creditMap.getOrDefault(CreditTypeEnum.LABOR_EDUCATION.getCode(), BigDecimal.ZERO))
                .build();
    }

    private void validateInterestTags(List<Long> tagIds) {
        if (tagIds.isEmpty()) {
            return;
        }
        if (activityTagRepository.selectBatchIds(tagIds).size() != tagIds.size()) {
            throw new BusinessException(4010, "存在无效的兴趣标签");
        }
    }

    private UserProfileResponse buildUserProfile(UserInfo userInfo) {
        List<UserInterestRel> relations = userInterestRelRepository.selectList(new LambdaQueryWrapper<UserInterestRel>()
                .eq(UserInterestRel::getUserId, userInfo.getStudentNo()));
        List<Long> tagIds = relations.stream().map(UserInterestRel::getTagId).toList();
        Map<Long, ActivityTag> tagMap = tagIds.isEmpty()
                ? Collections.emptyMap()
                : activityTagRepository.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(ActivityTag::getTagId, Function.identity()));

        List<ActivityTagVO> interestTags = relations.stream()
                .map(relation -> tagMap.get(relation.getTagId()))
                .filter(tag -> tag != null)
                .map(tag -> ActivityTagVO.builder().tagId(tag.getTagId()).tagName(tag.getTagName()).build())
                .toList();

        return UserProfileResponse.builder()
                .userId(userInfo.getStudentNo())
                .studentNo(userInfo.getStudentNo())
                .userName(userInfo.getUserName())
                .userPhone(userInfo.getUserPhone())
                .userEmail(userInfo.getUserEmail())
                .userRole(userInfo.getUserRole())
                .publisherStatus(userInfo.getPublisherStatus())
                .major(userInfo.getMajor())
                .grade(userInfo.getGrade())
                .interestTags(interestTags)
                .build();
    }

    private void validatePhoneAndEmail(UserInfo userInfo, UpdateUserProfileRequest request) {
        if (request.getUserPhone() != null && !request.getUserPhone().isBlank()) {
            UserInfo existingPhoneUser = userInfoRepository.selectOne(new LambdaQueryWrapper<UserInfo>()
                    .eq(UserInfo::getUserPhone, request.getUserPhone())
                    .last("LIMIT 1"));
            if (existingPhoneUser != null && !existingPhoneUser.getStudentNo().equals(userInfo.getStudentNo())) {
                throw new BusinessException(4011, "手机号已被其他用户使用");
            }
        }
        if (request.getUserEmail() != null && !request.getUserEmail().isBlank()) {
            UserInfo existingEmailUser = userInfoRepository.selectOne(new LambdaQueryWrapper<UserInfo>()
                    .eq(UserInfo::getUserEmail, request.getUserEmail())
                    .last("LIMIT 1"));
            if (existingEmailUser != null && !existingEmailUser.getStudentNo().equals(userInfo.getStudentNo())) {
                throw new BusinessException(4012, "邮箱已被其他用户使用");
            }
        }
    }

    private UserInfo requireUser(String userId) {
        UserInfo userInfo = userInfoRepository.selectById(userId);
        if (userInfo == null) {
            throw new BusinessException(4009, "用户不存在");
        }
        return userInfo;
    }
}
