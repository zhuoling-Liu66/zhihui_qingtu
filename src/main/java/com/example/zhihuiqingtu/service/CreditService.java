package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.credit.CreditQueryRequest;
import com.example.zhihuiqingtu.dto.credit.ReviewCreditRequest;
import com.example.zhihuiqingtu.vo.credit.CreditRecordResponse;
import com.example.zhihuiqingtu.vo.credit.CreditStatisticsResponse;

/**
 * 学分服务接口。
 */
public interface CreditService {

    PageResponse<CreditRecordResponse> queryCredits(CreditQueryRequest request);

    CreditRecordResponse reviewCredit(Long recordId, ReviewCreditRequest request);

    CreditStatisticsResponse getStatistics(String userId);
}
