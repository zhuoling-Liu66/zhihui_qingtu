package com.example.zhihuiqingtu.ai.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zhihuiqingtu.ai.entity.AiToolAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 工具审计数据访问。
 */
@Mapper
public interface AiToolAuditLogRepository extends BaseMapper<AiToolAuditLog> {
}
