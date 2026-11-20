package com.example.zhihuiqingtu.publisher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.publisher.dto.ApplyPublisherRequest;
import com.example.zhihuiqingtu.publisher.entity.PublisherPermissionRecord;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.publisher.enums.PublisherRecordStatusEnum;
import com.example.zhihuiqingtu.publisher.enums.PublisherRecordTypeEnum;
import com.example.zhihuiqingtu.user.enums.PublisherStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.publisher.repository.PublisherPermissionRecordRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.publisher.service.PublisherApplicationService;
import com.example.zhihuiqingtu.publisher.vo.PublisherApplicationStatusResponse;
import com.example.zhihuiqingtu.publisher.vo.PublisherPermissionRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.example.zhihuiqingtu.admin.user.service.AdminUserService;

/**
 * 学生端发布权限申请服务实现。
 *
 * <p>资格状态本身仍存在 {@code user_info.publisher_status}，本表只负责“谁申请、谁审批、
 * 什么时候通过/驳回”的留痕。管理员直接开通/收回也会写入同一张表，见
 * {@code AdminUserService}。
 */
@Service
@RequiredArgsConstructor
public class PublisherApplicationServiceImpl implements PublisherApplicationService {

    private final CurrentUser currentUser;
    private final UserInfoRepository userInfoRepository;
    private final PublisherPermissionRecordRepository recordRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublisherPermissionRecordVO apply(ApplyPublisherRequest request) {
        String userId = currentUser.requireUserId();
        UserInfo userInfo = requireUser(userId);

        if (isPrivileged(userInfo)) {
            throw new BusinessException(ErrorCode.CONFLICT, "管理员和老师默认具备发布权限，无需申请");
        }
        if (PublisherStatusEnum.ENABLED.getCode().equals(userInfo.getPublisherStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "发布权限已开通，无需重复申请");
        }

        long pendingCount = recordRepository.selectCount(new LambdaQueryWrapper<PublisherPermissionRecord>()
                .eq(PublisherPermissionRecord::getStudentNo, userId)
                .eq(PublisherPermissionRecord::getRecordType, PublisherRecordTypeEnum.APPLY.getCode())
                .eq(PublisherPermissionRecord::getRecordStatus, PublisherRecordStatusEnum.PENDING.getCode()));
        if (pendingCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "已有待审批的申请，请等待管理员处理");
        }

        String applyReason = request.getApplyReason() == null
                ? null
                : request.getApplyReason().trim();
        if (!StringUtils.hasText(applyReason)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请填写申请理由");
        }

        PublisherPermissionRecord record = PublisherPermissionRecord.builder()
                .studentNo(userId)
                .recordType(PublisherRecordTypeEnum.APPLY.getCode())
                .recordStatus(PublisherRecordStatusEnum.PENDING.getCode())
                .applyReason(applyReason)
                .build();
        try {
            recordRepository.insert(record);
        } catch (DuplicateKeyException exception) {
            // 数据库中的 pending_key 唯一索引兜底并发重复提交：
            // 两个请求同时通过上面的 count 检查时，只有一条能插入成功。
            throw new BusinessException(ErrorCode.CONFLICT, "已有待审批的申请，请等待管理员处理");
        }
        return toVO(record, userInfo.getUserName(), null);
    }

    @Override
    public PublisherApplicationStatusResponse getMyStatus() {
        String userId = currentUser.requireUserId();
        UserInfo userInfo = requireUser(userId);

        PublisherPermissionRecord latest = recordRepository.selectOne(
                new LambdaQueryWrapper<PublisherPermissionRecord>()
                        .eq(PublisherPermissionRecord::getStudentNo, userId)
                        .eq(PublisherPermissionRecord::getRecordType, PublisherRecordTypeEnum.APPLY.getCode())
                        .orderByDesc(PublisherPermissionRecord::getCreateTime)
                        .orderByDesc(PublisherPermissionRecord::getRecordId)
                        .last("LIMIT 1"));

        boolean pending = latest != null
                && PublisherRecordStatusEnum.PENDING.getCode().equals(latest.getRecordStatus());
        boolean enabled = PublisherStatusEnum.ENABLED.getCode().equals(userInfo.getPublisherStatus());
        boolean canApply = !isPrivileged(userInfo) && !enabled && !pending;

        String operatorName = null;
        if (latest != null && StringUtils.hasText(latest.getOperatorUserId())) {
            UserInfo operator = userInfoRepository.selectById(latest.getOperatorUserId());
            operatorName = operator == null ? null : operator.getUserName();
        }

        return PublisherApplicationStatusResponse.builder()
                .publisherStatus(userInfo.getPublisherStatus())
                .canApply(canApply)
                .pending(pending)
                .latestApplication(latest == null ? null : toVO(latest, userInfo.getUserName(), operatorName))
                .build();
    }

    private boolean isPrivileged(UserInfo userInfo) {
        return UserRoleEnum.ADMIN.getCode().equals(userInfo.getUserRole())
                || UserRoleEnum.TEACHER.getCode().equals(userInfo.getUserRole());
    }

    private UserInfo requireUser(String userId) {
        UserInfo userInfo = userInfoRepository.selectById(userId);
        if (userInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return userInfo;
    }

    private PublisherPermissionRecordVO toVO(PublisherPermissionRecord record,
                                             String studentName,
                                             String operatorName) {
        return PublisherPermissionRecordVO.builder()
                .recordId(record.getRecordId())
                .studentNo(record.getStudentNo())
                .studentName(studentName)
                .recordType(record.getRecordType())
                .recordStatus(record.getRecordStatus())
                .applyReason(record.getApplyReason())
                .operatorUserId(record.getOperatorUserId())
                .operatorName(operatorName)
                .operateTime(record.getOperateTime())
                .rejectReason(record.getRejectReason())
                .createTime(record.getCreateTime())
                .build();
    }
}
