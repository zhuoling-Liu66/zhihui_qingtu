package com.example.zhihuiqingtu.ai.client;

/**
 * 回灌给模型的一轮历史对话。
 *
 * @param role    user / assistant
 * @param content 文本
 */
public record AiChatTurn(String role, String content) {
}
