package com.example.zhihuiqingtu.ai.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zhihuiqingtu.ai.entity.AiRecommendationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 推荐日志数据访问。
 */
@Mapper
public interface AiRecommendationLogRepository extends BaseMapper<AiRecommendationLog> {
}
