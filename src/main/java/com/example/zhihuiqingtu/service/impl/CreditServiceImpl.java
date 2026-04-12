package com.example.zhihuiqingtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.credit.CreditQueryRequest;
import com.example.zhihuiqingtu.dto.credit.ReviewCreditRequest;
import com.example.zhihuiqingtu.entity.CreditRecord;
import com.example.zhihuiqingtu.entity.UserInfo;
import com.example.zhihuiqingtu.enums.CreditTypeEnum;
import com.example.zhihuiqingtu.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.repository.UserInfoRepository;
import com.example.zhihuiqingtu.service.CreditService;
import com.example.zhihuiqingtu.vo.credit.CreditRecordResponse;
import com.example.zhihuiqingtu.vo.credit.CreditStatisticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学分服务实现。
 */
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private final CreditRecordRepository creditRecordRepository;
    private final UserInfoRepository userInfoRepository;

    @Override
    public PageResponse<CreditRecordResponse> queryCredits(CreditQueryRequest request) {
        LambdaQueryWrapper<CreditRecord> queryWrapper = new LambdaQueryWrapper<CreditRecord>()
                .eq(request.getUserId() != null, CreditRecord::getUserId, request.getUserId())
                .eq(request.getVerifyStatus() != null, CreditRecord::getVerifyStatus, request.getVerifyStatus())
                .eq(request.getCreditType() != null, CreditRecord::getCreditType, request.getCreditType())
                .orderByDesc(CreditRecord::getCreateTime);
        Page<CreditRecord> page = creditRecordRepository.selectPage(
                new Page<>(request.getPageNo() + 1L, request.getPageSize()),
                queryWrapper
        );
        Page<CreditRecordResponse> responsePage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        responsePage.setRecords(page.getRecords().stream().map(this::toResponse).toList());
        return PageResponse.from(responsePage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreditRecordResponse reviewCredit(Long recordId, ReviewCreditRequest request) {
        UserInfo verifier = requireUser(request.getVerifyUserId());
        if (!UserRoleEnum.ADMIN.getCode().equals(verifier.getUserRole())
                && !UserRoleEnum.TEACHER.getCode().equals(verifier.getUserRole())) {
            throw new BusinessException(4401, "仅管理员或老师可以审核学分");
        }
        if (!ReviewStatusEnum.APPROVED.getCode().equals(request.getVerifyStatus())
                && !ReviewStatusEnum.REJECTED.getCode().equals(request.getVerifyStatus())) {
            throw new BusinessException(4402, "审核状态仅支持通过或驳回");
        }
        CreditRecord creditRecord = requireCreditRecord(recordId);
        creditRecord.setVerifyUserId(request.getVerifyUserId());
        creditRecord.setVerifyStatus(request.getVerifyStatus());
        creditRecord.setRejectReason(request.getRejectReason());
        creditRecord.setVerifyTime(LocalDateTime.now());
        creditRecordRepository.updateById(creditRecord);
        return toResponse(creditRecord);
    }

    @Override
    public CreditStatisticsResponse getStatistics(String userId) {
        requireUser(userId);
        List<CreditRecord> records = creditRecordRepository.selectList(new LambdaQueryWrapper<CreditRecord>()
                .eq(CreditRecord::getUserId, userId)
                .eq(CreditRecord::getVerifyStatus, ReviewStatusEnum.APPROVED.getCode()));
        BigDecimal total = BigDecimal.ZERO;
        Map<Integer, BigDecimal> creditMap = new HashMap<>();
        for (CreditRecord record : records) {
            total = total.add(record.getCredit());
            creditMap.merge(record.getCreditType(), record.getCredit(), BigDecimal::add);
        }
        return CreditStatisticsResponse.builder()
                .userId(userId)
                .totalCredit(total)
                .innovationEntrepreneurshipCredit(creditMap.getOrDefault(CreditTypeEnum.INNOVATION_ENTREPRENEURSHIP.getCode(), BigDecimal.ZERO))
                .intellectualEducationCredit(creditMap.getOrDefault(CreditTypeEnum.INTELLECTUAL_EDUCATION.getCode(), BigDecimal.ZERO))
                .moralEducationCredit(creditMap.getOrDefault(CreditTypeEnum.MORAL_EDUCATION.getCode(), BigDecimal.ZERO))
                .physicalEducationCredit(creditMap.getOrDefault(CreditTypeEnum.PHYSICAL_EDUCATION.getCode(), BigDecimal.ZERO))
                .aestheticEducationCredit(creditMap.getOrDefault(CreditTypeEnum.AESTHETIC_EDUCATION.getCode(), BigDecimal.ZERO))
                .laborEducationCredit(creditMap.getOrDefault(CreditTypeEnum.LABOR_EDUCATION.getCode(), BigDecimal.ZERO))
                .build();
    }

    private CreditRecordResponse toResponse(CreditRecord creditRecord) {
        return CreditRecordResponse.builder()
                .recordId(creditRecord.getRecordId())
                .userId(creditRecord.getUserId())
                .activityId(creditRecord.getActivityId())
                .credit(creditRecord.getCredit())
                .creditType(creditRecord.getCreditType())
                .verifyStatus(creditRecord.getVerifyStatus())
                .rejectReason(creditRecord.getRejectReason())
                .verifyTime(creditRecord.getVerifyTime())
                .createTime(creditRecord.getCreateTime())
                .build();
    }

    private CreditRecord requireCreditRecord(Long recordId) {
        CreditRecord creditRecord = creditRecordRepository.selectById(recordId);
        if (creditRecord == null) {
            throw new BusinessException(4400, "学分记录不存在");
        }
        return creditRecord;
    }

    private UserInfo requireUser(String userId) {
        UserInfo userInfo = userInfoRepository.selectById(userId);
        if (userInfo == null) {
            throw new BusinessException(4009, "用户不存在");
        }
        return userInfo;
    }
}
