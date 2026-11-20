package com.example.zhihuiqingtu.enrollment.service;

import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.award.dto.ActivityParticipantQueryRequest;
import com.example.zhihuiqingtu.enrollment.dto.CreateEnrollmentRequest;
import com.example.zhihuiqingtu.enrollment.dto.EnrollmentQueryRequest;
import com.example.zhihuiqingtu.enrollment.dto.FeedbackEnrollmentRequest;
import com.example.zhihuiqingtu.enrollment.dto.UpdateAttendanceRequest;
import com.example.zhihuiqingtu.award.dto.UpdateAwardRequest;
import com.example.zhihuiqingtu.award.vo.ActivityParticipantVO;
import com.example.zhihuiqingtu.award.vo.ActivityParticipantsResponse;
import com.example.zhihuiqingtu.enrollment.vo.EnrollmentResponse;
import com.example.zhihuiqingtu.security.CurrentUser;

/**
 * 报名业务服务接口。
 * <p>
 * 本接口<b>所有方法的操作者身份都取自当前登录用户</b>（Spring Security 的
 * {@code SecurityContextHolder}，经 {@code CurrentUser} 读取），不再由调用方传入
 * userId —— 那种入参是可伪造的，等于把越权入口摆在方法签名上。
 */
public interface EnrollmentService {

    /**
     * 创建报名记录。
     * <p>
     * 报名人取自当前登录用户，不是请求参数。
     *
     * @param request 报名请求参数
     * @return 报名结果
     */
    EnrollmentResponse createEnrollment(CreateEnrollmentRequest request);

    /**
     * 取消报名。
     * <p>
     * 操作者取自当前登录用户，并会与报名记录的归属人做校验。
     *
     * @param enrollId 报名记录主键
     * @return 更新后的报名记录
     */
    EnrollmentResponse cancelEnrollment(Long enrollId);

    /**
     * 自助签到。
     * <p>
     * 签到人取自当前登录用户，并会与报名记录的归属人做校验。
     * 签到只把状态流转到“已签到”，不生成学分/时长记录。
     *
     * @param enrollId 报名记录主键
     * @return 签到后的报名记录
     */
    EnrollmentResponse signIn(Long enrollId);

    /**
     * 自助签退。
     * <p>
     * 签退人取自当前登录用户，并会与报名记录的归属人做校验。
     * 签退把状态流转到“已完成”，并为学分/志愿活动生成待确认的发放记录。
     *
     * @param enrollId 报名记录主键
     * @return 签退后的报名记录
     */
    EnrollmentResponse signOut(Long enrollId);

    /**
     * 提交活动评价。
     * <p>
     * 评价人取自当前登录用户，并会与报名记录的归属人做校验。
     *
     * @param enrollId 报名记录主键
     * @param request  评价请求参数
     * @return 更新后的报名记录
     */
    EnrollmentResponse feedback(Long enrollId, FeedbackEnrollmentRequest request);

    /**
     * 分页查询报名记录。
     * <p>
     * 查询范围被服务端强制限定为当前登录用户自己的报名记录，请求里传不了别人的学号。
     *
     * @param request 查询条件
     * @return 报名记录分页结果
     */
    PageResponse<EnrollmentResponse> queryEnrollments(EnrollmentQueryRequest request);

    /**
     * 查询某活动的参与人员名单（发布者 / 管理员）。
     * <p>
     * 权限由服务端按“是否该活动发布者或管理员”判定；返回报名信息、
     * 考勤信息与发放记录，并附带活动摘要与汇总计数。
     *
     * @param activityId 活动主键
     * @param request    分页条件
     * @return 参与人员分页结果
     */
    ActivityParticipantsResponse queryActivityParticipants(Long activityId, ActivityParticipantQueryRequest request);

    /**
     * 发布者/管理员修正单人考勤（补签到、补签退、撤销签到、撤销签退）。
     * <p>
     * 补签退会生成待确认的发放记录；撤销签退时若发放已确认会被拒绝。
     *
     * @param enrollId 报名记录主键
     * @param request  考勤操作
     * @return 更新后的参与人员信息
     */
    ActivityParticipantVO updateAttendance(Long enrollId, UpdateAttendanceRequest request);

    /**
     * 发布者/管理员编辑某人的发放记录（金额与发放结论）。
     *
     * @param enrollId 报名记录主键
     * @param request  发放调整请求
     * @return 更新后的参与人员信息
     */
    ActivityParticipantVO updateAward(Long enrollId, UpdateAwardRequest request);

    /**
     * 一键确认某活动全部待确认的发放记录。
     *
     * @param activityId 活动主键
     * @return 本次确认的记录数
     */
    int confirmAwards(Long activityId);
}
