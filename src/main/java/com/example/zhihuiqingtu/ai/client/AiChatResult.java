package com.example.zhihuiqingtu.ai.client;

/**
 * 一次模型调用的结果。
 *
 * @param reply            最终回复文本
 * @param promptTokens     输入 token（可能为 null，取决于供应商是否返回）
 * @param completionTokens 输出 token
 * @param model            实际模型名
 * @param finishReason     结束原因
 */
public record AiChatResult(String reply,
                           Integer promptTokens,
                           Integer completionTokens,
                           String model,
                           String finishReason) {

    public int totalTokens() {
        return (promptTokens == null ? 0 : promptTokens) + (completionTokens == null ? 0 : completionTokens);
    }
}
