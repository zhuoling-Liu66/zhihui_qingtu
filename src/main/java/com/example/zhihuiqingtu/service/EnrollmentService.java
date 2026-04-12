package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.enrollment.CreateEnrollmentRequest;
import com.example.zhihuiqingtu.dto.enrollment.EnrollmentQueryRequest;
import com.example.zhihuiqingtu.dto.enrollment.FeedbackEnrollmentRequest;
import com.example.zhihuiqingtu.vo.enrollment.EnrollmentResponse;

/**
 * 报名服务接口。
 */
public interface EnrollmentService {

    EnrollmentResponse createEnrollment(CreateEnrollmentRequest request);

    EnrollmentResponse cancelEnrollment(Long enrollId, String userId);

    EnrollmentResponse signIn(Long enrollId, String userId);

    EnrollmentResponse feedback(Long enrollId, FeedbackEnrollmentRequest request);

    PageResponse<EnrollmentResponse> queryEnrollments(EnrollmentQueryRequest request);
}
