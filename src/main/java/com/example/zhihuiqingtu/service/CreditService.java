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
     * 分页查询学分记录（按角色区分可见范围）。
     * <p>
     * 管理员/老师：可见全部记录，并可用 {@code request.userId} 按学号筛选（审核队列场景）；
     * 学生发布者：只能看到<b>自己发布的活动</b>产生的记录（与 {@code canReviewCredit} 允许他审核的
     * 范围保持一致，否则"能审却看不到"），也可在该范围内按学号筛选；
     * 普通学生：忽略 {@code request.userId}，强制只查当前登录用户。
     * 可见范围由服务端按角色 + 数据库里的活动归属判定，客户端无法通过自报学号越权。
     *
     * @param request 查询条件
     * @return 学分记录分页结果
     */
    PageResponse<CreditRecordResponse> queryCredits(CreditQueryRequest request);

    /**
     * 审核某一条学分记录。
     * <p>
     * 审核人不再由请求体声明，实现类从 {@code CurrentUser} 取当前登录用户，
     * 避免伪造审核人（越权 + 审计失真）。
     *
     * @param recordId 学分记录主键
     * @param request  审核请求体
     * @return 审核后的学分记录
     */
    CreditRecordResponse reviewCredit(Long recordId, ReviewCreditRequest request);

    /**
     * 查询学分统计（按角色区分可见范围）。
     * <p>
     * 原签名是 {@code getStatistics(String userId)} 且直接统计传入的 userId，任何人都能查别人。
     * 现在 {@code userId} 是<b>可选的目标用户</b>：
     * <ul>
     *   <li>管理员 / 老师：传了 {@code userId} 就统计该用户，不传则统计自己；</li>
     *   <li>普通用户：<b>忽略</b> {@code userId}，永远统计当前登录用户自己。</li>
     * </ul>
     *
     * @param userId 目标用户学号（可为 null；仅管理员/老师生效）
     * @return 学分统计结果
     */
    CreditStatisticsResponse getStatistics(String userId);
}
