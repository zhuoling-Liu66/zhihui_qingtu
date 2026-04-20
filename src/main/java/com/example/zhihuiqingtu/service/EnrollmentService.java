package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.enrollment.CreateEnrollmentRequest;
import com.example.zhihuiqingtu.dto.enrollment.EnrollmentQueryRequest;
import com.example.zhihuiqingtu.dto.enrollment.FeedbackEnrollmentRequest;
import com.example.zhihuiqingtu.vo.enrollment.EnrollmentResponse;

/**
 * 报名业务服务接口。
 */
public interface EnrollmentService {

    /**
     * 创建报名记录。
     *
     * @param request 报名请求参数
     * @return 报名结果
     */
    EnrollmentResponse createEnrollment(CreateEnrollmentRequest request);

    /**
     * 取消报名。
     *
     * @param enrollId 报名记录主键
     * @param userId   当前用户学号/工号
     * @return 更新后的报名记录
     */
    EnrollmentResponse cancelEnrollment(Long enrollId, String userId);

    /**
     * 自助签到。
     *
     * @param enrollId 报名记录主键
     * @param userId   当前用户学号/工号
     * @return 签到后的报名记录
     */
    EnrollmentResponse signIn(Long enrollId, String userId);

    /**
     * 提交活动评价。
     *
     * @param enrollId 报名记录主键
     * @param request  评价请求参数
     * @return 更新后的报名记录
     */
    EnrollmentResponse feedback(Long enrollId, FeedbackEnrollmentRequest request);

    /**
     * 分页查询报名记录。
     *
     * @param request 查询条件
     * @return 报名记录分页结果
     */
    PageResponse<EnrollmentResponse> queryEnrollments(EnrollmentQueryRequest request);
}
