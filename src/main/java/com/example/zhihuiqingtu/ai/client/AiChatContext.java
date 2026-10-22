package com.example.zhihuiqingtu.ai.client;

import java.util.List;

/**
 * 一次模型调用的输入。
 *
 * @param systemPrompt 系统提示词（含当前时间与用户画像）
 * @param history      历史消息（不含本轮）
 * @param userMessage  本轮用户消息
 */
public record AiChatContext(String systemPrompt,
                            List<AiChatTurn> history,
                            String userMessage) {
}
