package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.enrollment.CreateEnrollmentRequest;
import com.example.zhihuiqingtu.dto.enrollment.EnrollmentQueryRequest;
import com.example.zhihuiqingtu.dto.enrollment.FeedbackEnrollmentRequest;
import com.example.zhihuiqingtu.vo.enrollment.EnrollmentResponse;

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
     *
     * @param enrollId 报名记录主键
     * @return 签到后的报名记录
     */
    EnrollmentResponse signIn(Long enrollId);

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
}
