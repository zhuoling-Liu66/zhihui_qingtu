package com.example.zhihuiqingtu.ai.client;

import com.example.zhihuiqingtu.ai.config.AiProperties;
import com.example.zhihuiqingtu.ai.tool.AiToolSet;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 唯一的 Spring AI 接触点。
 *
 * <p>把框架类型关在这个类里，业务层只看到 {@link AiChatContext} / {@link AiChatResult}。
 * 将来换成手写 OpenAI 兼容客户端，只需要替换本类实现。
 *
 * <p><b>为什么用 blockLast 而不是响应式链路一路传下去</b>：
 * 流式回复本来就跑在专用线程池（{@code aiExecutor}）上，阻塞等待不会影响 Tomcat 线程；
 * 而把 Reactor 的 {@code Flux} 暴露到 Controller 层，会让"取消、超时、异常"三件事
 * 分散到多个回调里，反而更容易泄漏。这里用 {@code blockLast(timeout)} 换来实现简单与可预测。
 */
@Slf4j
@Component
public class SpringAiChatGateway {

    private final ChatClient chatClient;
    private final AiProperties properties;
    private final String apiKey;
    private final String model;

    public SpringAiChatGateway(ObjectProvider<ChatClient.Builder> builderProvider,
                               AiProperties properties,
                               @Value("${spring.ai.deepseek.api-key:}") String apiKey,
                               @Value("${spring.ai.deepseek.chat.model:}") String model) {
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.build();
        this.properties = properties;
        this.apiKey = apiKey;
        this.model = model;
        if (chatClient == null) {
            log.warn("未找到 ChatClient.Builder：AI 对话功能不可用（检查是否引入了 spring-ai starter）");
        } else if (!hasRealKey()) {
            log.warn("AI 助手未配置真实 API Key（当前为占位符），对话接口将返回降级结果");
        }
    }

    /**
     * 是否具备真正调用模型的条件。
     */
    public boolean isAvailable() {
        return properties.isEnabled() && chatClient != null && hasRealKey();
    }

    private boolean hasRealKey() {
        return apiKey != null && !apiKey.isBlank()
                && !properties.getApiKeyPlaceholder().equals(apiKey);
    }

    public String modelName() {
        return model == null || model.isBlank() ? "unknown" : model;
    }

    /**
     * 非流式调用。
     */
    public AiChatResult call(AiChatContext context, AiToolSet tools) {
        ensureAvailable();
        try {
            ChatResponse response = buildSpec(context, tools).call().chatResponse();
            String reply = extractText(response);
            Usage usage = usageOf(response);
            return new AiChatResult(reply == null ? "" : reply,
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens(),
                    modelName(),
                    finishReasonOf(response));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("调用模型失败：{}", exception.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "AI 服务暂时不可用");
        }
    }

    /**
     * 流式调用：每收到一段文本就回调 {@code onDelta}。
     *
     * <p>注意：工具调用发生在 Reactor 的线程上，因此工具里<b>不能</b>依赖
     * {@code SecurityContextHolder}——身份是在构造 {@link AiToolSet} 时注入的。
     */
    public AiChatResult stream(AiChatContext context, AiToolSet tools, Consumer<String> onDelta) {
        ensureAvailable();
        StringBuilder buffer = new StringBuilder();
        AtomicReference<Usage> usageRef = new AtomicReference<>();
        AtomicReference<String> finishRef = new AtomicReference<>();
        Duration timeout = properties.getChat().getTimeout();
        try {
            buildSpec(context, tools).stream().chatResponse()
                    .doOnNext(response -> {
                        Usage usage = usageOf(response);
                        if (usage != null) {
                            usageRef.set(usage);
                        }
                        String finish = finishReasonOf(response);
                        if (finish != null) {
                            finishRef.set(finish);
                        }
                        String delta = extractText(response);
                        if (delta != null && !delta.isEmpty()) {
                            buffer.append(delta);
                            onDelta.accept(delta);
                        }
                    })
                    .blockLast(timeout);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("流式调用模型失败：{}", exception.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "AI 服务暂时不可用");
        }
        Usage usage = usageRef.get();
        return new AiChatResult(buffer.toString(),
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                modelName(),
                finishRef.get() == null ? "stop" : finishRef.get());
    }

    // ══════════════════ 内部 ══════════════════

    private void ensureAvailable() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "AI 助手已关闭");
        }
        if (chatClient == null || !hasRealKey()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "AI 助手尚未配置");
        }
    }

    private ChatClient.ChatClientRequestSpec buildSpec(AiChatContext context, AiToolSet tools) {
        List<Message> messages = new ArrayList<>();
        for (AiChatTurn turn : context.history()) {
            if (turn.content() == null || turn.content().isBlank()) {
                continue;
            }
            messages.add("assistant".equals(turn.role())
                    ? new AssistantMessage(turn.content())
                    : new UserMessage(turn.content()));
        }
        messages.add(new UserMessage(context.userMessage()));
        return chatClient.prompt()
                .system(context.systemPrompt())
                .messages(messages.toArray(new Message[0]))
                .tools(tools);
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private Usage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getUsage();
    }

    private String finishReasonOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getMetadata() == null) {
            return null;
        }
        return response.getResult().getMetadata().getFinishReason();
    }
}
