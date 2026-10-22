package com.example.zhihuiqingtu.ai.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zhihuiqingtu.ai.entity.AiPendingAction;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 待确认动作数据访问。
 */
@Mapper
public interface AiPendingActionRepository extends BaseMapper<AiPendingAction> {
}
