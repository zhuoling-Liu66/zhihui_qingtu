package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.credit.CreditQueryRequest;
import com.example.zhihuiqingtu.dto.credit.ReviewCreditRequest;
import com.example.zhihuiqingtu.vo.credit.CreditRecordResponse;
import com.example.zhihuiqingtu.vo.credit.CreditStatisticsResponse;

/**
 * 学分业务服务接口。
 */
public interface CreditService {

    /**
     * 分页查询学分记录。
     *
     * @param request 查询条件
     * @return 学分记录分页结果
     */
    PageResponse<CreditRecordResponse> queryCredits(CreditQueryRequest request);

    /**
     * 审核某一条学分记录。
     *
     * @param recordId 学分记录主键
     * @param request  审核请求体
     * @return 审核后的学分记录
     */
    CreditRecordResponse reviewCredit(Long recordId, ReviewCreditRequest request);

    /**
     * 查询某个用户的学分统计。
     *
     * @param userId 用户学号/工号
     * @return 学分统计结果
     */
    CreditStatisticsResponse getStatistics(String userId);
}
