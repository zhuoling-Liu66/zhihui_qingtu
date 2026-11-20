package com.example.zhihuiqingtu.admin.award.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zhihuiqingtu.admin.award.dto.ApplyAwardImportRequest;
import com.example.zhihuiqingtu.admin.award.support.ExcelTableReader;
import com.example.zhihuiqingtu.admin.award.vo.AwardImportApplyResultVO;
import com.example.zhihuiqingtu.admin.award.vo.AwardImportPreviewVO;
import com.example.zhihuiqingtu.ai.service.AiTableMappingService;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.activity.entity.ActivityCreditTier;
import com.example.zhihuiqingtu.enrollment.entity.ActivityEnroll;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.award.entity.CreditRecord;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.activity.enums.ActivityTypeEnum;
import com.example.zhihuiqingtu.award.enums.AwardStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.activity.repository.ActivityCreditTierRepository;
import com.example.zhihuiqingtu.enrollment.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.award.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.activity.vo.CreditTierVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端 AI 批量导入：解析表格 → 识别列 → 匹配参与者 → 预览 → 写库（待审核）。
 *
 * <p>AI 只参与列识别；金额、匹配、名额、权限全部由服务端确定性校验。
 */
@Service
@RequiredArgsConstructor
public class AdminAwardImportService {

    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_SKIP_APPROVED = "SKIP_APPROVED";
    private static final String ACTION_UNMATCHED = "UNMATCHED";
    private static final String ACTION_AMBIGUOUS = "AMBIGUOUS";
    private static final String ACTION_NO_RECORD = "NO_RECORD";
    private static final String ACTION_INVALID = "INVALID";
    private static final String ACTION_DUPLICATE = "DUPLICATE";
    private static final String ACTION_TIER_FULL = "TIER_FULL";

    private final ActivityMainRepository activityMainRepository;
    private final ActivityEnrollRepository activityEnrollRepository;
    private final ActivityCreditTierRepository activityCreditTierRepository;
    private final CreditRecordRepository creditRecordRepository;
    private final UserInfoRepository userInfoRepository;
    private final CurrentUser currentUser;
    private final ExcelTableReader excelTableReader;
    private final AiTableMappingService tableMappingService;

    // ══════════════════ 预览 ══════════════════

    public AwardImportPreviewVO preview(Long activityId, MultipartFile file, String instruction) {
        ActivityMain activity = requireManageableActivity(activityId);
        boolean creditKind = requireAwardActivity(activity);
        String valueKind = creditKind ? "credit" : "hours";
        List<List<String>> rows = excelTableReader.read(file);
        AiTableMappingService.TableMapping mapping = tableMappingService.analyze(rows, instruction, valueKind);

        Integer valueCol = creditKind ? mapping.creditCol() : mapping.hoursCol();
        if (valueCol == null) {
            Integer otherCol = creditKind ? mapping.hoursCol() : mapping.creditCol();
            if (otherCol != null) {
                String otherLabel = creditKind
                        ? mapping.columnLabels(rows).get("hours")
                        : mapping.columnLabels(rows).get("credit");
                throw new BusinessException(ErrorCode.PARAM_ERROR, creditKind
                        ? "这个表格看起来是志愿时长表（识别到“" + labelOr(otherLabel, "时长") + "”列），但当前活动是学分活动，无法按学分导入"
                        : "这个表格看起来是学分表（识别到“" + labelOr(otherLabel, "学分") + "”列），但当前活动是志愿活动，无法按时长导入");
            }
            throw new BusinessException(ErrorCode.PARAM_ERROR, creditKind
                    ? "未识别到学分列，请确认表格中是否包含“学分”列"
                    : "未识别到志愿时长列，请确认表格中是否包含“时长/小时”列");
        }
        int headerRow = mapping.headerRow();
        if (headerRow < 0 || headerRow >= rows.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未找到表头行，请补充说明表格结构");
        }

        List<ActivityEnroll> enrollments = activityEnrollRepository.selectList(
                new LambdaQueryWrapper<ActivityEnroll>().eq(ActivityEnroll::getActivityId, activityId));
        Map<String, String> userNames = loadUserNames(enrollments.stream().map(ActivityEnroll::getUserId).toList());
        Map<String, ActivityEnroll> byStudentNo = new HashMap<>();
        Map<String, List<ActivityEnroll>> byName = new HashMap<>();
        for (ActivityEnroll enrollment : enrollments) {
            byStudentNo.put(normalizeNo(enrollment.getUserId()), enrollment);
            String name = userNames.get(enrollment.getUserId());
            if (name != null && !name.isBlank()) {
                byName.computeIfAbsent(normalizeName(name), key -> new ArrayList<>()).add(enrollment);
            }
        }
        Map<String, CreditRecord> awardByUser = creditRecordRepository.selectList(
                        new LambdaQueryWrapper<CreditRecord>().eq(CreditRecord::getActivityId, activityId))
                .stream()
                .collect(Collectors.toMap(CreditRecord::getUserId, record -> record, (a, b) -> a));
        List<ActivityCreditTier> tiers = loadTiers(activity);

        List<AwardImportPreviewVO.Row> errorRows = new ArrayList<>();
        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        int total = 0;
        int unmatched = 0;
        int invalid = 0;
        int skippedApproved = 0;
        int noRecord = 0;

        for (int i = headerRow + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String studentNo = cell(row, mapping.studentNoCol());
            String name = cell(row, mapping.nameCol());
            String rawText = cell(row, valueCol);
            if (studentNo.isBlank() && name.isBlank() && rawText.isBlank()) {
                continue;
            }
            total++;
            int rowNumber = i + 1;
            BigDecimal value;
            try {
                value = parseValue(rawText, creditKind, mapping.hoursUnit());
            } catch (NumberFormatException exception) {
                value = null;
            }
            if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                invalid++;
                errorRows.add(errorRow(rowNumber, name, studentNo, null,
                        ACTION_INVALID, "未填写或无法识别金额：" + rawText));
                continue;
            }
            if (creditKind && !isValidCreditUnit(value)) {
                invalid++;
                errorRows.add(errorRow(rowNumber, name, studentNo, value,
                        ACTION_INVALID, "学分必须以0.05为单位"));
                continue;
            }

            ActivityEnroll matched = null;
            boolean ambiguous = false;
            if (!studentNo.isBlank()) {
                matched = byStudentNo.get(normalizeNo(studentNo));
            }
            if (matched == null && !name.isBlank()) {
                List<ActivityEnroll> sameName = byName.get(normalizeName(name));
                if (sameName != null && sameName.size() == 1) {
                    matched = sameName.get(0);
                } else if (sameName != null && sameName.size() > 1) {
                    ambiguous = true;
                }
            }
            if (ambiguous) {
                unmatched++;
                errorRows.add(errorRow(rowNumber, name, studentNo, value,
                        ACTION_AMBIGUOUS, "活动中有多名同名学生，请用学号区分"));
                continue;
            }
            if (matched == null) {
                unmatched++;
                errorRows.add(errorRow(rowNumber, name, studentNo, value,
                        ACTION_UNMATCHED, "学号/姓名未匹配到已报名参与者"));
                continue;
            }
            CreditRecord award = awardByUser.get(matched.getUserId());
            if (award == null) {
                noRecord++;
                errorRows.add(errorRow(rowNumber, name, studentNo, value,
                        ACTION_NO_RECORD, "该参与者还没有发放记录，请先完成签退"));
                continue;
            }
            if (AwardStatusEnum.APPROVED.getCode().equals(award.getVerifyStatus())) {
                skippedApproved++;
                errorRows.add(errorRow(rowNumber, name, studentNo, value,
                        ACTION_SKIP_APPROVED, "已发放，不能覆盖"));
                continue;
            }
            String key = matched.getUserId();
            Aggregate aggregate = aggregates.get(key);
            if (aggregate == null) {
                aggregate = new Aggregate(rowNumber, matched, name, studentNo, award,
                        userNames.get(matched.getUserId()), creditKind);
                aggregates.put(key, aggregate);
            }
            aggregate.add(value);
        }

        // 组合学分：计算档位占用（排除本次将被覆盖的记录）
        Set<Long> targetRecordIds = aggregates.values().stream()
                .map(aggregate -> aggregate.award.getRecordId())
                .collect(Collectors.toSet());
        Map<BigDecimal, Integer> existingUsage = new HashMap<>();
        if (creditKind && Integer.valueOf(2).equals(activity.getCreditMode())) {
            for (CreditRecord record : awardByUser.values()) {
                if (AwardStatusEnum.REJECTED.getCode().equals(record.getVerifyStatus())
                        || targetRecordIds.contains(record.getRecordId())
                        || record.getCredit() == null) {
                    continue;
                }
                existingUsage.merge(normalizeCredit(record.getCredit()), 1, Integer::sum);
            }
        }
        Map<BigDecimal, Integer> plannedUsage = new HashMap<>();
        List<AwardImportPreviewVO.Row> rowsOut = new ArrayList<>();
        int tierFull = 0;
        int duplicate = 0;
        int updateCount = 0;

        for (Aggregate aggregate : aggregates.values()) {
            if (creditKind && aggregate.creditValues.size() > 1) {
                duplicate++;
                rowsOut.add(aggregate.toRow(ACTION_DUPLICATE, "同一人多行且学分不一致，请核对后重试", null));
                continue;
            }
            BigDecimal value = aggregate.value();
            if (creditKind) {
                BigDecimal normalized = normalizeCredit(value);
                if (Integer.valueOf(2).equals(activity.getCreditMode())) {
                    ActivityCreditTier tier = tiers.stream()
                            .filter(item -> item.getCredit().compareTo(value) == 0)
                            .findFirst().orElse(null);
                    if (tier == null) {
                        invalid++;
                        rowsOut.add(aggregate.toRow(ACTION_INVALID, "学分不在活动配置的档位内", value));
                        continue;
                    }
                    int used = existingUsage.getOrDefault(normalized, 0);
                    int planned = plannedUsage.getOrDefault(normalized, 0);
                    if (used + planned >= tier.getQuota()) {
                        tierFull++;
                        rowsOut.add(aggregate.toRow(ACTION_TIER_FULL,
                                "该档位名额已满（" + tier.getQuota() + " 名）", value));
                        continue;
                    }
                    plannedUsage.merge(normalized, 1, Integer::sum);
                }
                updateCount++;
                rowsOut.add(aggregate.toRow(ACTION_UPDATE, aggregate.note(), value));
            } else {
                updateCount++;
                rowsOut.add(aggregate.toRow(ACTION_UPDATE, aggregate.note(), value.setScale(2, RoundingMode.HALF_UP)));
            }
        }
        rowsOut.addAll(errorRows);
        rowsOut.sort(Comparator.comparing(AwardImportPreviewVO.Row::getRowNumber,
                Comparator.nullsLast(Integer::compareTo)));

        List<CreditTierVO> tierVos = tiers.stream()
                .map(tier -> CreditTierVO.builder()
                        .tierId(tier.getTierId())
                        .credit(tier.getCredit())
                        .quota(tier.getQuota())
                        .used(existingUsage.getOrDefault(normalizeCredit(tier.getCredit()), 0))
                        .selected(plannedUsage.getOrDefault(normalizeCredit(tier.getCredit()), 0))
                        .build())
                .toList();

        return AwardImportPreviewVO.builder()
                .activityId(activityId)
                .activityTitle(activity.getActivityTitle())
                .activityType(activity.getActivityType())
                .valueKind(valueKind)
                .aiUsed(mapping.aiUsed())
                .aiNotes(mapping.notes())
                .headerRow(headerRow + 1)
                .mapping(mapping.columnLabels(rows))
                .hoursUnit(mapping.hoursUnit())
                .creditTiers(tierVos)
                .summary(AwardImportPreviewVO.Summary.builder()
                        .total(total)
                        .matched(updateCount + skippedApproved + noRecord)
                        .unmatched(unmatched)
                        .invalid(invalid)
                        .skippedApproved(skippedApproved)
                        .noRecord(noRecord)
                        .duplicate(duplicate)
                        .tierFull(tierFull)
                        .build())
                .rows(rowsOut)
                .build();
    }

    // ══════════════════ 保存 ══════════════════

    @Transactional(rollbackFor = Exception.class)
    public AwardImportApplyResultVO apply(Long activityId, ApplyAwardImportRequest request) {
        ActivityMain activity = requireManageableActivity(activityId);
        boolean creditKind = requireAwardActivity(activity);
        if (request.getRows() == null || request.getRows().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要保存的行");
        }

        Map<Long, ActivityEnroll> enrollments = activityEnrollRepository.selectList(
                        new LambdaQueryWrapper<ActivityEnroll>().eq(ActivityEnroll::getActivityId, activityId))
                .stream()
                .collect(Collectors.toMap(ActivityEnroll::getEnrollId, item -> item, (a, b) -> a));
        Map<String, CreditRecord> awardByUser = creditRecordRepository.selectList(
                        new LambdaQueryWrapper<CreditRecord>().eq(CreditRecord::getActivityId, activityId))
                .stream()
                .collect(Collectors.toMap(CreditRecord::getUserId, record -> record, (a, b) -> a));
        List<ActivityCreditTier> tiers = loadTiers(activity);
        boolean combo = creditKind && Integer.valueOf(2).equals(activity.getCreditMode());

        Set<Long> targetRecordIds = new HashSet<>();
        for (ApplyAwardImportRequest.Item item : request.getRows()) {
            ActivityEnroll enrollment = enrollments.get(item.getEnrollId());
            if (enrollment == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "报名记录不属于该活动：" + item.getEnrollId());
            }
            CreditRecord award = awardByUser.get(enrollment.getUserId());
            if (award == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "参与者 " + enrollment.getUserId() + " 还没有发放记录，请先签退");
            }
            if (AwardStatusEnum.APPROVED.getCode().equals(award.getVerifyStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "参与者 " + enrollment.getUserId() + " 的发放已确认，不能覆盖");
            }
            if (!targetRecordIds.add(award.getRecordId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "同一参与者不能重复提交");
            }
        }

        Map<BigDecimal, Integer> usage = new HashMap<>();
        if (combo) {
            for (CreditRecord record : awardByUser.values()) {
                if (AwardStatusEnum.REJECTED.getCode().equals(record.getVerifyStatus())
                        || targetRecordIds.contains(record.getRecordId())
                        || record.getCredit() == null) {
                    continue;
                }
                usage.merge(normalizeCredit(record.getCredit()), 1, Integer::sum);
            }
        }

        int updated = 0;
        for (ApplyAwardImportRequest.Item item : request.getRows()) {
            ActivityEnroll enrollment = enrollments.get(item.getEnrollId());
            CreditRecord award = awardByUser.get(enrollment.getUserId());
            BigDecimal value = item.getValue();
            if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "金额必须大于0");
            }
            if (creditKind) {
                if (!isValidCreditUnit(value)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "学分必须以0.05为单位");
                }
                if (combo) {
                    ActivityCreditTier tier = tiers.stream()
                            .filter(candidate -> candidate.getCredit().compareTo(value) == 0)
                            .findFirst()
                            .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "学分不在活动配置的档位内"));
                    int used = usage.getOrDefault(normalizeCredit(value), 0);
                    if (used >= tier.getQuota()) {
                        throw new BusinessException(ErrorCode.CONFLICT,
                                "学分档位 " + value + " 名额已满（" + tier.getQuota() + " 名）");
                    }
                    usage.merge(normalizeCredit(value), 1, Integer::sum);
                }
                award.setCredit(value);
                award.setCreditType(activity.getCreditType());
                award.setVolunteerHours(null);
            } else {
                award.setVolunteerHours(value.setScale(2, RoundingMode.HALF_UP));
                award.setCredit(BigDecimal.ZERO);
                award.setCreditType(null);
            }
            award.setVerifyStatus(AwardStatusEnum.PENDING_REVIEW.getCode());
            award.setVerifyUserId(currentUser.requireUserId());
            award.setVerifyTime(LocalDateTime.now());
            award.setRejectReason(null);
            creditRecordRepository.updateById(award);
            updated++;
        }
        return AwardImportApplyResultVO.builder()
                .total(request.getRows().size())
                .updated(updated)
                .message("已保存 " + updated + " 条，等待管理员审核")
                .build();
    }

    // ══════════════════ 工具 ══════════════════

    private ActivityMain requireManageableActivity(Long activityId) {
        ActivityMain activity = activityMainRepository.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        String userId = currentUser.requireUserId();
        if (!userId.equals(activity.getPublisherUserId())
                && !currentUser.hasRole(UserRoleEnum.ADMIN.getCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅活动发布者或管理员可以导入发放数据");
        }
        return activity;
    }

    private boolean requireAwardActivity(ActivityMain activity) {
        if (ActivityTypeEnum.SECOND_CLASS.getCode().equals(activity.getActivityType())) {
            return true;
        }
        if (ActivityTypeEnum.VOLUNTEER.getCode().equals(activity.getActivityType())) {
            return false;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "只有学分或志愿活动可以导入发放数据");
    }

    private List<ActivityCreditTier> loadTiers(ActivityMain activity) {
        if (!ActivityTypeEnum.SECOND_CLASS.getCode().equals(activity.getActivityType())
                || !Integer.valueOf(2).equals(activity.getCreditMode())) {
            return List.of();
        }
        return activityCreditTierRepository.selectList(new LambdaQueryWrapper<ActivityCreditTier>()
                .eq(ActivityCreditTier::getActivityId, activity.getActivityId())
                .orderByAsc(ActivityCreditTier::getSortNo));
    }

    private Map<String, String> loadUserNames(Collection<String> userIds) {
        Set<String> ids = userIds.stream().filter(id -> id != null && !id.isBlank()).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userInfoRepository.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(UserInfo::getStudentNo, info -> info.getUserName() == null ? "" : info.getUserName(),
                        (a, b) -> a));
    }

    private String cell(List<String> row, Integer col) {
        if (col == null || col < 0 || col >= row.size()) {
            return "";
        }
        String value = row.get(col);
        return value == null ? "" : value.trim();
    }

    private String normalizeNo(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().replaceAll("[\\s\\u00A0·]", "");
    }

    private BigDecimal normalizeCredit(BigDecimal value) {
        return value.stripTrailingZeros();
    }

    private boolean isValidCreditUnit(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() <= 2
                && normalized.remainder(new BigDecimal("0.05")).compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 解析金额：支持“8.5”“8.5小时”“90分钟”“3学分”等写法；分钟自动换算成小时。
     */
    private BigDecimal parseValue(String text, boolean creditKind, String hoursUnit) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean minutes = lower.contains("分钟") || lower.contains("min");
        String cleaned = text.replaceAll("[,，\\s]", "")
                .replace("小时", "")
                .replace("分钟", "")
                .replaceAll("(?i)hours?", "")
                .replaceAll("(?i)hrs?", "")
                .replaceAll("(?i)min", "")
                .replaceAll("(?i)h", "")
                .replace("学分", "")
                .replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) {
            return null;
        }
        BigDecimal value = new BigDecimal(cleaned);
        if (!creditKind && (minutes || (!lower.contains("小时") && "min".equals(hoursUnit)))) {
            value = value.divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
        }
        return value;
    }

    private AwardImportPreviewVO.Row errorRow(int rowNumber, String name, String studentNo,
                                              BigDecimal value, String action, String reason) {
        return AwardImportPreviewVO.Row.builder()
                .rowNumber(rowNumber)
                .name(trimToNull(name))
                .studentNo(trimToNull(studentNo))
                .rawValue(value)
                .action(action)
                .reason(reason)
                .selected(false)
                .build();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String labelOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private class Aggregate {
        private final int firstRow;
        private final ActivityEnroll enrollment;
        private final String excelName;
        private final String excelStudentNo;
        private final CreditRecord award;
        private final String matchedName;
        private final boolean creditKind;
        private BigDecimal value = BigDecimal.ZERO;
        private final Set<BigDecimal> creditValues = new HashSet<>();
        private int sourceCount;

        private Aggregate(int firstRow, ActivityEnroll enrollment, String excelName, String excelStudentNo,
                          CreditRecord award, String matchedName, boolean creditKind) {
            this.firstRow = firstRow;
            this.enrollment = enrollment;
            this.excelName = excelName;
            this.excelStudentNo = excelStudentNo;
            this.award = award;
            this.matchedName = matchedName;
            this.creditKind = creditKind;
        }

        private void add(BigDecimal item) {
            sourceCount++;
            if (creditKind) {
                creditValues.add(normalizeCredit(item));
            } else {
                value = value.add(item);
            }
        }

        private BigDecimal value() {
            return creditKind
                    ? creditValues.stream().findFirst().orElse(BigDecimal.ZERO)
                    : value;
        }

        private String note() {
            return sourceCount > 1 ? "合并了 " + sourceCount + " 行" : "";
        }

        private AwardImportPreviewVO.Row toRow(String action, String reason, BigDecimal valueOverride) {
            BigDecimal current = creditKind ? award.getCredit() : award.getVolunteerHours();
            return AwardImportPreviewVO.Row.builder()
                    .rowNumber(firstRow)
                    .name(trimToNull(excelName))
                    .studentNo(trimToNull(excelStudentNo))
                    .rawValue(valueOverride != null ? valueOverride : value())
                    .enrollId(enrollment.getEnrollId())
                    .userId(enrollment.getUserId())
                    .matchedName(matchedName)
                    .currentValue(current)
                    .currentStatus(award.getVerifyStatus())
                    .action(action)
                    .reason(reason == null || reason.isBlank() ? note() : reason)
                    .selected(ACTION_UPDATE.equals(action))
                    .build();
        }
    }
}
