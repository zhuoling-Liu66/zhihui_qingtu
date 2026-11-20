package com.example.zhihuiqingtu.award.service;

import com.example.zhihuiqingtu.award.dto.ActivityParticipantQueryRequest;
import com.example.zhihuiqingtu.award.dto.UpdateAwardRequest;
import com.example.zhihuiqingtu.award.vo.ActivityParticipantVO;
import com.example.zhihuiqingtu.award.vo.ActivityParticipantsResponse;
import com.example.zhihuiqingtu.enrollment.dto.UpdateAttendanceRequest;

/**
 * 活动参与人员与发放服务（发布者/管理员视角）。
 */
public interface ParticipantAwardService {

    ActivityParticipantsResponse queryActivityParticipants(Long activityId,
                                                           ActivityParticipantQueryRequest request);

    ActivityParticipantVO updateAttendance(Long enrollId, UpdateAttendanceRequest request);

    ActivityParticipantVO updateAward(Long enrollId, UpdateAwardRequest request);

    int confirmAwards(Long activityId);
}
