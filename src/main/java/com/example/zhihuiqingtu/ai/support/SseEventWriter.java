package com.example.zhihuiqingtu.ai.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * SSE 事件写出器。
 *
 * <p>三件事必须做对，否则前端会"收到半截 JSON"或"连接莫名断开"：
 * <ol>
 *   <li><b>data 必须是单行 JSON</b>：SSE 协议里 {@code data:} 后遇到换行就是下一条数据，
 *       多行 JSON 会被前端按行解析成残缺对象；</li>
 *   <li><b>写出必须串行</b>：工具事件与文本增量可能来自不同线程，并发写同一个 emitter 会交错；</li>
 *   <li><b>断连后要停</b>：客户端关掉页面后继续生成只会白烧 token。</li>
 * </ol>
 */
@Slf4j
public class SseEventWriter {

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private volatile boolean closed;

    public SseEventWriter(SseEmitter emitter, ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送一个命名事件。
     *
     * @param event 事件名：meta/delta/tool/card/action/usage/done/error
     * @param data  事件载荷，序列化为单行 JSON
     */
    public synchronized void send(String event, Object data) {
        if (closed) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (RuntimeException exception) {
            log.warn("SSE 事件序列化失败 event={}: {}", event, exception.getMessage());
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(json));
        } catch (IOException | IllegalStateException exception) {
            // 客户端断开（刷新/关页面）会走到这里。不是错误，是正常结束信号。
            closed = true;
            log.debug("SSE 连接已断开，停止推送：{}", exception.getMessage());
        }
    }

    public synchronized void complete() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            emitter.complete();
        } catch (RuntimeException exception) {
            log.debug("关闭 SSE 时异常：{}", exception.getMessage());
        }
    }

    public synchronized void completeWithError(int code, String message, boolean retryable) {
        send("error", java.util.Map.of(
                "code", code,
                "message", message,
                "retryable", retryable));
        complete();
    }

    public boolean isClosed() {
        return closed;
    }

    /** 客户端断开时由 SseEmitter 回调，用来中止后续生成。 */
    public void markClosed() {
        this.closed = true;
    }
}
