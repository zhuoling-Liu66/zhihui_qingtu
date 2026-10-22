package com.example.zhihuiqingtu.ai.controller;

import com.example.zhihuiqingtu.ai.dto.ChatRequest;
import com.example.zhihuiqingtu.ai.config.AiProperties;
import com.example.zhihuiqingtu.ai.service.AiChatService;
import com.example.zhihuiqingtu.ai.vo.ChatResponseVO;
import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话接口。
 *
 * <p>身份一律由 {@link CurrentUser} 从 JWT 取，请求体里没有 userId，也不接受 userId。
 */
@Tag(name = "AI 助手-对话", description = "非流式与流式（SSE）对话")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final AiProperties aiProperties;
    private final CurrentUser currentUser;

    /**
     * 非流式对话（Swagger 调试 / 降级使用）。
     */
    @Operation(summary = "非流式对话", description = "返回完整回复；模型不可用时返回规则降级结果（degraded=true）")
    @PostMapping("/chat")
    public ApiResponse<ChatResponseVO> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.success(aiChatService.chat(currentUser.requireUserId(), request));
    }

    /**
     * 流式对话。
     *
     * <p>不能在这里用 {@code @RequestBody} 校验失败时返回 SSE——参数错误发生在建立流之前，
     * 由 {@code GlobalExceptionHandler} 返回普通 JSON 400，前端据 Content-Type 区分。
     */
    @Operation(summary = "流式对话（SSE）",
            description = "事件类型：meta/delta/tool/card/action/usage/done/error。前端请用 fetch 读取，不要用 EventSource")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        String userId = currentUser.requireUserId();
        // 关掉反向代理缓冲，否则 Nginx 会攒满一个 buffer 才吐给浏览器，"流式"就变成了"一次性"
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        SseEmitter emitter = new SseEmitter(aiProperties.getChat().getTimeout().toMillis());
        aiChatService.stream(userId, request, emitter);
        return emitter;
    }
}
