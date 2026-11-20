package com.example.zhihuiqingtu.enrollment.controller;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.award.dto.ActivityParticipantQueryRequest;
import com.example.zhihuiqingtu.enrollment.dto.CreateEnrollmentRequest;
import com.example.zhihuiqingtu.enrollment.dto.EnrollmentQueryRequest;
import com.example.zhihuiqingtu.enrollment.dto.FeedbackEnrollmentRequest;
import com.example.zhihuiqingtu.enrollment.dto.UpdateAttendanceRequest;
import com.example.zhihuiqingtu.award.dto.UpdateAwardRequest;
import com.example.zhihuiqingtu.enrollment.service.EnrollmentService;
import com.example.zhihuiqingtu.award.vo.ActivityParticipantVO;
import com.example.zhihuiqingtu.award.vo.ActivityParticipantsResponse;
import com.example.zhihuiqingtu.enrollment.vo.EnrollmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.zhihuiqingtu.security.CurrentUser;

/**
 * 报名参与模块控制器。
 * <p>
 * 负责活动报名、退出、签到、评价和报名记录查询。
 * <p>
 * 本模块<b>不接受任何客户端自报的身份</b>：报名人/退出人/签到人/评价人一律由
 * Spring Security 从 JWT 解析后，由服务层通过 {@code CurrentUser} 读取。
 */
@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    /**
     * 报名业务服务。
     */
    private final EnrollmentService enrollmentService;

    /**
     * 报名某个活动。
     *
     * @param request 报名请求体
     * @return 报名后的记录信息
     */
    @PostMapping
    public ApiResponse<EnrollmentResponse> createEnrollment(@Valid @RequestBody CreateEnrollmentRequest request) {
        return ApiResponse.success("报名成功", enrollmentService.createEnrollment(request));
    }

    /**
     * 取消报名。
     * <p>
     * 不再需要 {@code userId} 查询参数：操作者身份由 Spring Security 从 JWT 解析，
     * 服务层通过 {@code CurrentUser} 读取。少一个可伪造的入参，就少一处越权面。
     *
     * @param enrollId 报名记录主键
     * @return 更新后的报名记录
     */
    @DeleteMapping("/{enrollId}")
    public ApiResponse<EnrollmentResponse> cancelEnrollment(@PathVariable Long enrollId) {
        return ApiResponse.success("取消报名成功", enrollmentService.cancelEnrollment(enrollId));
    }

    /**
     * 用户自助签到。
     * <p>
     * 不再需要 {@code userId} 查询参数：签到人身份由 Spring Security 从 JWT 解析，
     * 服务层通过 {@code CurrentUser} 读取。少一个可伪造的入参，就少一处越权面。
     *
     * @param enrollId 报名记录主键
     * @return 签到后的报名记录
     */
    @PatchMapping("/{enrollId}/sign-in")
    public ApiResponse<EnrollmentResponse> signIn(@PathVariable Long enrollId) {
        return ApiResponse.success("签到成功", enrollmentService.signIn(enrollId));
    }

    /**
     * 用户自助签退。
     * <p>
     * 签退人身份同样由 Spring Security 从 JWT 解析；签退后状态为“已完成”，
     * 并为学分/志愿活动生成待发布者确认的发放记录。
     *
     * @param enrollId 报名记录主键
     * @return 签退后的报名记录
     */
    @PatchMapping("/{enrollId}/sign-out")
    public ApiResponse<EnrollmentResponse> signOut(@PathVariable Long enrollId) {
        return ApiResponse.success("签退成功", enrollmentService.signOut(enrollId));
    }

    /**
     * 提交活动评价。
     *
     * @param enrollId 报名记录主键
     * @param request  评价请求体
     * @return 更新后的报名记录
     */
    @PatchMapping("/{enrollId}/feedback")
    public ApiResponse<EnrollmentResponse> feedback(
            @PathVariable Long enrollId,
            @Valid @RequestBody FeedbackEnrollmentRequest request) {
        return ApiResponse.success("评价提交成功", enrollmentService.feedback(enrollId, request));
    }

    /**
     * 查询报名记录列表。
     *
     * @param request 查询条件
     * @return 报名记录分页结果
     */
    @GetMapping
    public ApiResponse<PageResponse<EnrollmentResponse>> queryEnrollments(@ModelAttribute EnrollmentQueryRequest request) {
        return ApiResponse.success(enrollmentService.queryEnrollments(request));
    }

    /**
     * 查询某活动的参与人员名单。
     * <p>
     * 仅活动发布者本人或管理员可访问；服务端按数据库中的活动归属判定，客户端伪造不了。
     *
     * @param activityId 活动主键
     * @param request    分页条件
     * @return 参与人员、考勤与发放信息
     */
    @GetMapping("/activity/{activityId}")
    public ApiResponse<ActivityParticipantsResponse> queryActivityParticipants(
            @PathVariable Long activityId,
            @ModelAttribute ActivityParticipantQueryRequest request) {
        return ApiResponse.success(enrollmentService.queryActivityParticipants(activityId, request));
    }

    /**
     * 发布者/管理员修正单人考勤。
     *
     * @param enrollId 报名记录主键
     * @param request  考勤操作（补签到、补签退、撤销签到、撤销签退）
     * @return 更新后的参与人员信息
     */
    @PatchMapping("/{enrollId}/attendance")
    public ApiResponse<ActivityParticipantVO> updateAttendance(
            @PathVariable Long enrollId,
            @Valid @RequestBody UpdateAttendanceRequest request) {
        return ApiResponse.success("考勤已更新", enrollmentService.updateAttendance(enrollId, request));
    }

    /**
     * 发布者/管理员编辑某人的发放记录（学分/志愿时长与发放结论）。
     *
     * @param enrollId 报名记录主键
     * @param request  发放调整请求
     * @return 更新后的参与人员信息
     */
    @PatchMapping("/{enrollId}/award")
    public ApiResponse<ActivityParticipantVO> updateAward(
            @PathVariable Long enrollId,
            @Valid @RequestBody UpdateAwardRequest request) {
        return ApiResponse.success("发放信息已更新", enrollmentService.updateAward(enrollId, request));
    }

    /**
     * 一键确认某活动全部待确认的发放记录。
     *
     * @param activityId 活动主键
     * @return 本次确认的记录数
     */
    @PatchMapping("/activity/{activityId}/awards/confirm")
    public ApiResponse<Integer> confirmAwards(@PathVariable Long activityId) {
        int confirmed = enrollmentService.confirmAwards(activityId);
        return ApiResponse.success("已确认发放 " + confirmed + " 条记录", confirmed);
    }
}
