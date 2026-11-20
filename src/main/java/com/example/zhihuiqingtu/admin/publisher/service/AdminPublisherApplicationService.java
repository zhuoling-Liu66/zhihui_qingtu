package com.example.zhihuiqingtu.admin.publisher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.admin.support.AdminGuard;
import com.example.zhihuiqingtu.admin.publisher.dto.AdminPublisherApplicationQueryRequest;
import com.example.zhihuiqingtu.admin.publisher.dto.AdminReviewPublisherApplicationRequest;
import com.example.zhihuiqingtu.admin.support.AdminPages;
import com.example.zhihuiqingtu.admin.publisher.vo.AdminPublisherApplicationVO;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.publisher.entity.PublisherPermissionRecord;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.publisher.enums.PublisherRecordStatusEnum;
import com.example.zhihuiqingtu.publisher.enums.PublisherRecordTypeEnum;
import com.example.zhihuiqingtu.user.enums.PublisherStatusEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.publisher.repository.PublisherPermissionRecordRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.example.zhihuiqingtu.admin.user.service.AdminUserService;

/**
 * 管理端发布权限审批服务。
 *
 * <p>审批通过后同时更新 {@code user_info.publisher_status = 1}；
 * 驳回只更新申请记录，不改变用户当前权限。申请记录本身保存审批人、审批时间和驳回原因，
 * 形成可追溯的审批留痕。
 */
@Service
@RequiredArgsConstructor
public class AdminPublisherApplicationService {

    private final AdminGuard adminGuard;
    private final PublisherPermissionRecordRepository recordRepository;
    private final UserInfoRepository userInfoRepository;

    /**
     * 分页查询发布权限申请/变更记录。
     */
    public PageResponse<AdminPublisherApplicationVO> queryApplications(
            AdminPublisherApplicationQueryRequest request) {
        adminGuard.requireAdmin();
        validateFilter(request.getRecordStatus(), request.getRecordType());

        String keyword = StringUtils.hasText(request.getKeyword())
                ? request.getKeyword().trim()
                : null;

        LambdaQueryWrapper<PublisherPermissionRecord> queryWrapper =
                new LambdaQueryWrapper<PublisherPermissionRecord>()
                        .eq(request.getRecordStatus() != null,
                                PublisherPermissionRecord::getRecordStatus, request.getRecordStatus())
                        .eq(request.getRecordType() != null,
                                PublisherPermissionRecord::getRecordType, request.getRecordType());

        if (keyword != null) {
            List<String> matchedStudentNos = userInfoRepository.selectList(
                            new LambdaQueryWrapper<UserInfo>()
                                    .select(UserInfo::getStudentNo)
                                    .like(UserInfo::getUserName, keyword))
                    .stream()
                    .map(UserInfo::getStudentNo)
                    .toList();
            queryWrapper.and(wrapper -> {
                wrapper.like(PublisherPermissionRecord::getStudentNo, keyword)
                        .or().like(PublisherPermissionRecord::getApplyReason, keyword)
                        .or().like(PublisherPermissionRecord::getRejectReason, keyword);
                if (!matchedStudentNos.isEmpty()) {
                    wrapper.or().in(PublisherPermissionRecord::getStudentNo, matchedStudentNos);
                }
            });
        }
        queryWrapper.orderByDesc(PublisherPermissionRecord::getCreateTime)
                .orderByDesc(PublisherPermissionRecord::getRecordId);

        Page<PublisherPermissionRecord> page = recordRepository.selectPage(
                AdminPages.of(request.getPageNo(), request.getPageSize()),
                queryWrapper
        );
        Map<String, String> userNames = loadUserNames(page.getRecords());
        Page<AdminPublisherApplicationVO> responsePage =
                new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        responsePage.setRecords(page.getRecords().stream()
                .map(record -> toVO(record, userNames))
                .toList());
        return PageResponse.from(responsePage);
    }

    /**
     * 审批一条学生申请：通过或驳回。
     *
     * <p>记录状态更新带“仍是待审批”的数据库条件，避免两个管理员同时处理时重复生效；
     * 通过时在同一事务内把用户发布资格置为已开通。
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminPublisherApplicationVO review(Long recordId,
                                              AdminReviewPublisherApplicationRequest request) {
        String operatorId = adminGuard.requireAdmin();
        PublisherRecordStatusEnum targetStatus =
                PublisherRecordStatusEnum.fromCode(request.getRecordStatus());
        if (targetStatus != PublisherRecordStatusEnum.APPROVED
                && targetStatus != PublisherRecordStatusEnum.REJECTED) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "审批结果仅支持通过或驳回");
        }

        PublisherPermissionRecord record = recordRepository.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "申请记录不存在");
        }
        if (!PublisherRecordTypeEnum.APPLY.getCode().equals(record.getRecordType())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有学生申请需要审批");
        }
        if (!PublisherRecordStatusEnum.PENDING.getCode().equals(record.getRecordStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该申请已被处理，请刷新后查看");
        }

        String rejectReason = StringUtils.hasText(request.getRejectReason())
                ? request.getRejectReason().trim()
                : null;
        if (targetStatus == PublisherRecordStatusEnum.REJECTED && rejectReason == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "驳回时必须填写驳回原因");
        }

        UserInfo student = requireUser(record.getStudentNo());
        LocalDateTime operateTime = LocalDateTime.now();

        // 先更新用户、再更新申请记录，与 AdminUserService 的直接调整保持同样的加锁顺序
        // （都是先 user_info 后 publisher_permission_record），降低并发审批/直接调整时死锁的概率。
        // 若下面的条件更新失败，事务回滚，已经写入的用户权限也会一起撤销。
        if (targetStatus == PublisherRecordStatusEnum.APPROVED) {
            userInfoRepository.update(null, new LambdaUpdateWrapper<UserInfo>()
                    .eq(UserInfo::getStudentNo, student.getStudentNo())
                    .set(UserInfo::getPublisherStatus, PublisherStatusEnum.ENABLED.getCode()));
        }

        int updated = recordRepository.update(null,
                new LambdaUpdateWrapper<PublisherPermissionRecord>()
                        .eq(PublisherPermissionRecord::getRecordId, recordId)
                        .eq(PublisherPermissionRecord::getRecordStatus,
                                PublisherRecordStatusEnum.PENDING.getCode())
                        .set(PublisherPermissionRecord::getRecordStatus, targetStatus.getCode())
                        .set(PublisherPermissionRecord::getOperatorUserId, operatorId)
                        .set(PublisherPermissionRecord::getOperateTime, operateTime)
                        .set(PublisherPermissionRecord::getRejectReason,
                                targetStatus == PublisherRecordStatusEnum.REJECTED ? rejectReason : null));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该申请已被处理，请刷新后查看");
        }

        record.setRecordStatus(targetStatus.getCode());
        record.setOperatorUserId(operatorId);
        record.setOperateTime(operateTime);
        record.setRejectReason(targetStatus == PublisherRecordStatusEnum.REJECTED ? rejectReason : null);

        UserInfo operator = userInfoRepository.selectById(operatorId);
        return AdminPublisherApplicationVO.builder()
                .recordId(record.getRecordId())
                .studentNo(record.getStudentNo())
                .studentName(student.getUserName())
                .recordType(record.getRecordType())
                .recordStatus(record.getRecordStatus())
                .applyReason(record.getApplyReason())
                .operatorUserId(operatorId)
                .operatorName(operator == null ? null : operator.getUserName())
                .operateTime(operateTime)
                .rejectReason(record.getRejectReason())
                .createTime(record.getCreateTime())
                .build();
    }

    private void validateFilter(Integer recordStatus, Integer recordType) {
        if (recordStatus != null && PublisherRecordStatusEnum.fromCode(recordStatus) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "记录状态仅支持待审批、已通过、已驳回或已关闭");
        }
        if (recordType != null && PublisherRecordTypeEnum.fromCode(recordType) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "记录类型仅支持学生申请、管理员开通或管理员收回");
        }
    }

    private UserInfo requireUser(String studentNo) {
        UserInfo userInfo = userInfoRepository.selectById(studentNo);
        if (userInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return userInfo;
    }

    private Map<String, String> loadUserNames(Collection<PublisherPermissionRecord> records) {
        Set<String> ids = new HashSet<>();
        for (PublisherPermissionRecord record : records) {
            if (StringUtils.hasText(record.getStudentNo())) {
                ids.add(record.getStudentNo());
            }
            if (StringUtils.hasText(record.getOperatorUserId())) {
                ids.add(record.getOperatorUserId());
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userInfoRepository.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(UserInfo::getStudentNo, UserInfo::getUserName, (a, b) -> a));
    }

    private AdminPublisherApplicationVO toVO(PublisherPermissionRecord record,
                                             Map<String, String> userNames) {
        return AdminPublisherApplicationVO.builder()
                .recordId(record.getRecordId())
                .studentNo(record.getStudentNo())
                .studentName(userNames.get(record.getStudentNo()))
                .recordType(record.getRecordType())
                .recordStatus(record.getRecordStatus())
                .applyReason(record.getApplyReason())
                .operatorUserId(record.getOperatorUserId())
                .operatorName(record.getOperatorUserId() == null
                        ? null
                        : userNames.get(record.getOperatorUserId()))
                .operateTime(record.getOperateTime())
                .rejectReason(record.getRejectReason())
                .createTime(record.getCreateTime())
                .build();
    }
}
