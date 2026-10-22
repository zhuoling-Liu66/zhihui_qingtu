package com.example.zhihuiqingtu.ai.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zhihuiqingtu.ai.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 消息数据访问。
 */
@Mapper
public interface AiMessageRepository extends BaseMapper<AiMessage> {
}
