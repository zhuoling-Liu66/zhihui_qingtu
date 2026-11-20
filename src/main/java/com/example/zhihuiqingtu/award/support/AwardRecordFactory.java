package com.example.zhihuiqingtu.award.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.activity.enums.ActivityTypeEnum;
import com.example.zhihuiqingtu.award.entity.CreditRecord;
import com.example.zhihuiqingtu.award.enums.AwardStatusEnum;
import com.example.zhihuiqingtu.award.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.enrollment.entity.ActivityEnroll;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 签退后生成待确认的发放记录。
 *
 * <p>由考勤签退与管理员补签退两处复用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AwardRecordFactory {

    private final CreditRecordRepository creditRecordRepository;

    /**
     * 签退后生成待确认的发放记录。
     *
     * <p>发放记录复用 {@code credit_record}：第二课堂活动存学分与学分类型；
     * 志愿活动存时长（学分记 0、学分类型为空）。其他活动类型不产生发放记录。
     * 记录初始为“待确认”，由活动发布者（或管理员）逐人确认发放，并可调整金额。
     */
    public void generate(ActivityEnroll enrollment, ActivityMain activityMain) {
        boolean creditActivity = ActivityTypeEnum.SECOND_CLASS.getCode().equals(activityMain.getActivityType());
        boolean volunteerActivity = ActivityTypeEnum.VOLUNTEER.getCode().equals(activityMain.getActivityType());
        if (!creditActivity && !volunteerActivity) {
            return;
        }
        // 组合学分先占位（credit=0），由发布者在发放时按档位选择；
        // 灵活志愿时长先不写时长，由发布者活动结束后逐人录入并提交管理员审核。
        boolean comboCredit = creditActivity && Integer.valueOf(2).equals(activityMain.getCreditMode());
        boolean flexibleHours = volunteerActivity && Integer.valueOf(2).equals(activityMain.getVolunteerMode());
        // 这里的 selectCount 只负责"绝大多数重复签退快速跳过"，它自身有并发窗口，
        // 真正防止同一用户同一活动生成两条记录的是唯一索引
        // uk_credit_user_activity (user_id, activity_id, delete_flag)（见 04-credit-unique-index.sql）。
        if (creditRecordRepository.selectCount(new LambdaQueryWrapper<CreditRecord>()
                .eq(CreditRecord::getUserId, enrollment.getUserId())
                .eq(CreditRecord::getActivityId, enrollment.getActivityId())) > 0) {
            return;
        }
        CreditRecord award = CreditRecord.builder()
                .userId(enrollment.getUserId())
                .activityId(enrollment.getActivityId())
                .credit(creditActivity && !comboCredit ? activityMain.getActivityCredit() : BigDecimal.ZERO)
                .creditType(creditActivity ? activityMain.getCreditType() : null)
                .volunteerHours(volunteerActivity && !flexibleHours ? activityMain.getVolunteerHours() : null)
                .verifyStatus(AwardStatusEnum.PENDING.getCode())
                .build();
        try {
            creditRecordRepository.insert(award);
        } catch (DuplicateKeyException exception) {
            // 并发下另一个事务已经生成过发放记录：签退本身已经成功，不能因为"记录已存在"回滚签退。
            // 唯一索引是这里能安全吞掉冲突的前提 —— 没有索引时 MySQL 不会报这个错，重复数据仍会产生。
            log.debug("发放记录已存在，跳过重复生成 userId={} activityId={}",
                    enrollment.getUserId(), enrollment.getActivityId());
        }
    }

}
