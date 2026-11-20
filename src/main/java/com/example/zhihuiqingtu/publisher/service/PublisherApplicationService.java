package com.example.zhihuiqingtu.publisher.service;

import com.example.zhihuiqingtu.publisher.dto.ApplyPublisherRequest;
import com.example.zhihuiqingtu.publisher.vo.PublisherApplicationStatusResponse;
import com.example.zhihuiqingtu.publisher.vo.PublisherPermissionRecordVO;

/**
 * 学生端发布权限申请服务。
 */
public interface PublisherApplicationService {

    /**
     * 提交开通活动发布权限的申请。
     *
     * @param request 申请理由
     * @return 新生成的申请记录
     */
    PublisherPermissionRecordVO apply(ApplyPublisherRequest request);

    /**
     * 查询当前用户的发布权限状态与最近一次申请。
     *
     * @return 权限状态、是否可申请、最近申请
     */
    PublisherApplicationStatusResponse getMyStatus();
}
