package com.example.zhihuiqingtu.ai.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zhihuiqingtu.ai.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话数据访问。
 */
@Mapper
public interface AiConversationRepository extends BaseMapper<AiConversation> {
}
