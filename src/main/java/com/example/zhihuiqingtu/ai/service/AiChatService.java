package com.example.zhihuiqingtu.ai.service;

import com.example.zhihuiqingtu.ai.client.AiChatContext;
import com.example.zhihuiqingtu.ai.client.AiChatResult;
import com.example.zhihuiqingtu.ai.client.AiChatTurn;
import com.example.zhihuiqingtu.ai.client.SpringAiChatGateway;
import com.example.zhihuiqingtu.ai.config.AiProperties;
import com.example.zhihuiqingtu.ai.dto.ChatContext;
import com.example.zhihuiqingtu.ai.dto.ChatRequest;
import com.example.zhihuiqingtu.ai.entity.AiConversation;
import com.example.zhihuiqingtu.ai.entity.AiMessage;
import com.example.zhihuiqingtu.ai.support.AiConstants;
import com.example.zhihuiqingtu.ai.support.AiTextUtils;
import com.example.zhihuiqingtu.ai.support.AiToolEventSink;
import com.example.zhihuiqingtu.ai.support.SseEventWriter;
import com.example.zhihuiqingtu.ai.support.SystemPromptFactory;
import com.example.zhihuiqingtu.ai.tool.AiToolDependencies;
import com.example.zhihuiqingtu.ai.tool.AiToolSet;
import com.example.zhihuiqingtu.ai.vo.ActivityCardVO;
import com.example.zhihuiqingtu.ai.vo.CardsVO;
import com.example.zhihuiqingtu.ai.vo.ChatResponseVO;
import com.example.zhihuiqingtu.ai.vo.PendingActionVO;
import com.example.zhihuiqingtu.ai.vo.TeamCardVO;
import com.example.zhihuiqingtu.ai.vo.UsageVO;
import com.example.zhihuiqingtu.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 对话编排：会话、历史、提示词、工具、降级、落库。
 *
 * <p><b>降级策略</b>：模型不可用（没配 Key / 上游失败 / 全局熔断）时，不让用户面对一个 503 弹窗，
 * 而是用规则引擎给出推荐 + 一句诚实说明。前端拿到 {@code degraded=true} 就可以弱化"AI 正在回答"的观感。
 * 只有"管理员关闭了功能"才返回 503——那时前端应该直接隐藏入口。
 */
@Slf4j
@Service
public class AiChatService {

    private static final DateTimeFormatter CARD_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final AiConversationService conversationService;
    private final SpringAiChatGateway gateway;
    private final AiToolDependencies toolDependencies;
    private final AiRecommendationService recommendationService;
    private final AiQuotaService quotaService;
    private final SystemPromptFactory promptFactory;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final Executor aiExecutor;

    public AiChatService(AiConversationService conversationService,
                         SpringAiChatGateway gateway,
                         AiToolDependencies toolDependencies,
                         AiRecommendationService recommendationService,
                         AiQuotaService quotaService,
                         SystemPromptFactory promptFactory,
                         AiProperties properties,
                         ObjectMapper objectMapper,
                         @Qualifier("aiExecutor") Executor aiExecutor) {
        this.conversationService = conversationService;
        this.gateway = gateway;
        this.toolDependencies = toolDependencies;
        this.recommendationService = recommendationService;
        this.quotaService = quotaService;
        this.promptFactory = promptFactory;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.aiExecutor = aiExecutor;
    }

    // ══════════════════ 非流式 ══════════════════

    /**
     * 非流式对话：一次返回完整回复。
     */
    public ChatResponseVO chat(String userId, ChatRequest request) {
        quotaService.checkRateLimit(userId);
        quotaService.checkTokenQuota(userId);

        AiConversation conversation = resolveConversation(userId, request);
        Long conversationId = conversation.getConversationId();
        // 先取历史再存本条：避免把"本轮问题"重复塞进历史
        List<AiChatTurn> history = buildHistory(conversationId);
        AiMessage userMessage = conversationService.appendMessage(conversationId, userId,
                AiConstants.ROLE_USER, request.getMessage(), null, null, null, null, null, null);

        CollectingSink sink = new CollectingSink(null);
        AiToolSet tools = new AiToolSet(userId, conversationId, sink, toolDependencies);

        String reply;
        boolean degraded = false;
        AiChatResult result = null;
        if (!gateway.isAvailable() || quotaService.isDegraded()) {
            reply = degradeReply(userId, sink);
            degraded = true;
        } else {
            try {
                result = gateway.call(buildContext(userId, request, history), tools);
                reply = result.reply();
            } catch (BusinessException exception) {
                log.warn("模型调用失败，走规则降级：{}", exception.getMessage());
                reply = degradeReply(userId, sink);
                degraded = true;
            }
        }
        if (reply == null || reply.isBlank()) {
            reply = "抱歉，我没能生成回复，请换个说法再试一次。";
        }

        AiMessage assistant = conversationService.appendMessage(conversationId, userId,
                AiConstants.ROLE_ASSISTANT, reply, sink.cards(), sink.action(), gateway.modelName(),
                result == null ? null : result.promptTokens(),
                result == null ? null : result.completionTokens(),
                null);

        recommendationService.logImpressions(userId, conversationId, assistant.getMessageId(),
                AiConstants.SCENE_CHAT, sink.activities(), sink.teams());
        if (result != null) {
            quotaService.recordTokens(userId, result.totalTokens());
        }

        return ChatResponseVO.builder()
                .conversationId(conversationId)
                .userMessageId(userMessage.getMessageId())
                .assistantMessageId(assistant.getMessageId())
                .reply(reply)
                .cards(sink.cards())
                .pendingAction(sink.action())
                .usage(result == null
                        ? UsageVO.builder().promptTokens(0).completionTokens(0).totalTokens(0).build()
                        : UsageVO.builder().promptTokens(result.promptTokens())
                        .completionTokens(result.completionTokens()).totalTokens(result.totalTokens()).build())
                .degraded(degraded)
                .build();
    }

    // ══════════════════ 流式 ══════════════════

    /**
     * 流式对话入口（在请求线程上执行校验与会话准备，随后切到 AI 线程池）。
     *
     * <p>限流/配额/会话归属的异常在这里抛出，此时还没有写 SSE 响应，
     * 因此能被 {@code GlobalExceptionHandler} 正常转成 JSON 错误。
     */
    public void stream(String userId, ChatRequest request, SseEmitter emitter) {
        quotaService.checkRateLimit(userId);
        quotaService.checkTokenQuota(userId);

        AiConversation conversation = resolveConversation(userId, request);
        Long conversationId = conversation.getConversationId();
        List<AiChatTurn> history = buildHistory(conversationId);
        conversationService.appendMessage(conversationId, userId, AiConstants.ROLE_USER,
                request.getMessage(), null, null, null, null, null, null);

        SseEventWriter writer = new SseEventWriter(emitter, objectMapper);
        emitter.onCompletion(writer::markClosed);
        emitter.onError(throwable -> writer.markClosed());
        emitter.onTimeout(writer::markClosed);

        aiExecutor.execute(() -> doStream(userId, request, conversation, history, writer));
    }

    private void doStream(String userId, ChatRequest request, AiConversation conversation,
                          List<AiChatTurn> history, SseEventWriter writer) {
        long startedAt = System.currentTimeMillis();
        Long conversationId = conversation.getConversationId();

        AiMessage placeholder = conversationService.createAssistantPlaceholder(
                conversationId, userId, gateway.modelName());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("conversationId", conversationId);
        meta.put("messageId", placeholder.getMessageId());
        meta.put("model", gateway.modelName());
        writer.send("meta", meta);

        CollectingSink sink = new CollectingSink(writer);
        AiToolSet tools = new AiToolSet(userId, conversationId, sink, toolDependencies);

        String reply;
        boolean degraded = false;
        AiChatResult result = null;
        try {
            if (!gateway.isAvailable() || quotaService.isDegraded()) {
                reply = degradeReply(userId, sink);
                degraded = true;
                writer.send("delta", Map.of("text", reply));
            } else {
                try {
                    AiChatContext context = buildContext(userId, request, history);
                    result = gateway.stream(context, tools, text -> writer.send("delta", Map.of("text", text)));
                    reply = result.reply();
                } catch (RuntimeException exception) {
                    log.warn("流式生成失败，走规则降级：{}", exception.getMessage());
                    reply = degradeReply(userId, sink);
                    degraded = true;
                    writer.send("delta", Map.of("text", reply));
                }
            }
            if (reply == null || reply.isBlank()) {
                reply = "抱歉，我没能生成回复，请换个说法再试一次。";
                writer.send("delta", Map.of("text", reply));
            }

            conversationService.finalizeAssistantMessage(placeholder.getMessageId(), reply,
                    sink.cards(), sink.action(),
                    result == null ? null : result.promptTokens(),
                    result == null ? null : result.completionTokens(),
                    (int) (System.currentTimeMillis() - startedAt));

            recommendationService.logImpressions(userId, conversationId, placeholder.getMessageId(),
                    AiConstants.SCENE_CHAT, sink.activities(), sink.teams());
            if (result != null) {
                quotaService.recordTokens(userId, result.totalTokens());
            }

            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("promptTokens", result == null ? 0 : result.promptTokens());
            usage.put("completionTokens", result == null ? 0 : result.completionTokens());
            usage.put("totalTokens", result == null ? 0 : result.totalTokens());
            writer.send("usage", usage);

            Map<String, Object> done = new LinkedHashMap<>();
            done.put("messageId", placeholder.getMessageId());
            done.put("finishReason", degraded ? "degraded" : "stop");
            writer.send("done", done);
        } catch (RuntimeException exception) {
            log.error("流式对话异常 conversationId={}", conversationId, exception);
            writer.completeWithError(500, "生成失败，请重试", true);
            return;
        }
        writer.complete();
    }

    // ══════════════════ 内部 ══════════════════

    /**
     * 取会话；不传 conversationId 时自动创建，标题取首条消息前 16 字。
     */
    private AiConversation resolveConversation(String userId, ChatRequest request) {
        if (request.getConversationId() != null) {
            return conversationService.getOwned(userId, request.getConversationId());
        }
        String title = AiTextUtils.truncate(request.getMessage().trim(), 16);
        return conversationService.create(userId, title, "chat");
    }

    private List<AiChatTurn> buildHistory(Long conversationId) {
        return conversationService.recentHistory(conversationId, properties.getChat().getHistoryWindow())
                .stream()
                .map(message -> new AiChatTurn(message.getRole(), message.getContent()))
                .toList();
    }

    private AiChatContext buildContext(String userId, ChatRequest request, List<AiChatTurn> history) {
        String systemPrompt = promptFactory.build(userId) + contextHint(request.getContext());
        return new AiChatContext(systemPrompt, history, request.getMessage());
    }

    private String contextHint(ChatContext context) {
        if (context == null) {
            return "";
        }
        if (context.getActivityId() != null) {
            return "\n# 当前页面\n用户正在浏览活动 ID=" + context.getActivityId()
                    + "，当用户说「这个活动」时指的就是它。";
        }
        if (context.getTeamId() != null) {
            return "\n# 当前页面\n用户正在浏览队伍 ID=" + context.getTeamId() + "。";
        }
        return "";
    }

    /**
     * 规则降级回复：不调模型，直接用推荐引擎的结果拼一段话。
     */
    private String degradeReply(String userId, CollectingSink sink) {
        List<ActivityCardVO> cards;
        try {
            cards = recommendationService.recommendActivities(userId, 3, AiConstants.SCENE_CHAT);
        } catch (RuntimeException exception) {
            log.warn("降级推荐也失败了：{}", exception.getMessage());
            cards = List.of();
        }
        if (cards.isEmpty()) {
            return "我暂时没找到合适的活动。你可以告诉我偏好哪类——志愿、运动、第二课堂，或者有空的时间，我再帮你找。";
        }
        sink.activityCards(cards);
        StringBuilder builder = new StringBuilder("AI 助手暂时不可用，我先按你的兴趣挑了这几个：\n\n");
        int index = 1;
        for (ActivityCardVO card : cards) {
            builder.append(index++).append(". ").append(card.getActivityTitle());
            if (card.getStartTime() != null) {
                builder.append("（").append(card.getStartTime().format(CARD_TIME));
                if (card.getActivityLocation() != null) {
                    builder.append(" · ").append(card.getActivityLocation());
                }
                builder.append("）");
            }
            builder.append("\n   ").append(card.getReason()).append("\n");
        }
        return builder.toString();
    }

    /**
     * 收集工具事件：流式场景同时写 SSE，非流式场景只收集。
     */
    private static final class CollectingSink implements AiToolEventSink {

        private final SseEventWriter writer;
        private final List<ActivityCardVO> activities = new ArrayList<>();
        private final List<TeamCardVO> teams = new ArrayList<>();
        private PendingActionVO action;

        private CollectingSink(SseEventWriter writer) {
            this.writer = writer;
        }

        @Override
        public void toolStarted(String name) {
            if (writer != null) {
                writer.send("tool", Map.of("name", name, "status", "started"));
            }
        }

        @Override
        public void toolSucceeded(String name, long latencyMs, int resultCount) {
            if (writer != null) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("name", name);
                payload.put("status", "succeeded");
                payload.put("latencyMs", latencyMs);
                payload.put("resultCount", resultCount);
                writer.send("tool", payload);
            }
        }

        @Override
        public void toolFailed(String name, long latencyMs, String message) {
            if (writer != null) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("name", name);
                payload.put("status", "failed");
                payload.put("latencyMs", latencyMs);
                payload.put("message", message);
                writer.send("tool", payload);
            }
        }

        @Override
        public void activityCards(List<ActivityCardVO> cards) {
            activities.addAll(cards);
            if (writer != null) {
                for (ActivityCardVO card : cards) {
                    writer.send("card", Map.of("type", "activity", "data", card));
                }
            }
        }

        @Override
        public void teamCards(List<TeamCardVO> cards) {
            teams.addAll(cards);
            if (writer != null) {
                for (TeamCardVO card : cards) {
                    writer.send("card", Map.of("type", "team", "data", card));
                }
            }
        }

        @Override
        public void pendingAction(PendingActionVO pendingAction) {
            this.action = pendingAction;
            if (writer != null) {
                writer.send("action", pendingAction);
            }
        }

        private List<ActivityCardVO> activities() {
            return activities;
        }

        private List<TeamCardVO> teams() {
            return teams;
        }

        private PendingActionVO action() {
            return action;
        }

        private CardsVO cards() {
            return CardsVO.builder().activities(activities).teams(teams).build();
        }
    }
}
