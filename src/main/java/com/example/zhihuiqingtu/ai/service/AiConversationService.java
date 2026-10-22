package com.example.zhihuiqingtu.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.ai.entity.AiConversation;
import com.example.zhihuiqingtu.ai.entity.AiMessage;
import com.example.zhihuiqingtu.ai.repository.AiConversationRepository;
import com.example.zhihuiqingtu.ai.repository.AiMessageRepository;
import com.example.zhihuiqingtu.ai.support.AiConstants;
import com.example.zhihuiqingtu.ai.support.AiTextUtils;
import com.example.zhihuiqingtu.ai.support.StoredExtras;
import com.example.zhihuiqingtu.ai.vo.CardsVO;
import com.example.zhihuiqingtu.ai.vo.ChatMessageVO;
import com.example.zhihuiqingtu.ai.vo.ConversationVO;
import com.example.zhihuiqingtu.ai.vo.MessagePageVO;
import com.example.zhihuiqingtu.ai.vo.PendingActionVO;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 会话与消息的读写。
 *
 * <p><b>归属校验是这里唯一的硬约束</b>：所有按 ID 查会话的方法都必须同时带 {@code user_id}。
 * 不用"{@code selectById} 之后判断 userId"的写法——那种写法一旦有人复制到新方法里忘了判断，
 * 就是一次越权。把 user_id 放进 WHERE 条件，漏判在 SQL 层就查不到数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConversationService {

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    // ══════════════════ 会话 ══════════════════

    /**
     * 创建会话。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiConversation create(String userId, String title, String scene) {
        AiConversation conversation = AiConversation.builder()
                .userId(userId)
                .title(title == null || title.isBlank() ? AiConstants.DEFAULT_TITLE : title.trim())
                .scene(scene == null || scene.isBlank() ? "chat" : scene)
                .messageCount(0)
                .status(AiConstants.CONVERSATION_ACTIVE)
                .lastMessageAt(LocalDateTime.now())
                .build();
        conversationRepository.insert(conversation);
        return conversation;
    }

    /**
     * 查会话并校验归属，不存在或不属于当前用户统一 404。
     *
     * <p>不返回 403：403 等于告诉攻击者"这个 ID 是存在的"，属于存在性泄露。
     */
    public AiConversation getOwned(String userId, Long conversationId) {
        AiConversation conversation = conversationRepository.selectOne(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getConversationId, conversationId)
                        .eq(AiConversation::getUserId, userId));
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    public PageResponse<ConversationVO> list(String userId, Integer pageNo, Integer pageSize) {
        int safePageNo = pageNo == null || pageNo < 0 ? 0 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        Page<AiConversation> page = conversationRepository.selectPage(
                new Page<>(safePageNo + 1L, safePageSize),
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .orderByDesc(AiConversation::getLastMessageAt)
                        .orderByDesc(AiConversation::getConversationId));
        List<ConversationVO> records = page.getRecords().stream()
                .map(item -> ConversationVO.builder()
                        .conversationId(item.getConversationId())
                        .title(item.getTitle())
                        .messageCount(item.getMessageCount())
                        .lastMessageAt(item.getLastMessageAt())
                        .createTime(item.getCreateTime())
                        .build())
                .toList();
        return PageResponse.<ConversationVO>builder()
                .pageNo(safePageNo)
                .pageSize(safePageSize)
                .totalElements(page.getTotal())
                .totalPages((int) page.getPages())
                .records(records)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void rename(String userId, Long conversationId, String title) {
        AiConversation conversation = getOwned(userId, conversationId);
        conversation.setTitle(title.trim());
        conversationRepository.updateById(conversation);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String userId, Long conversationId) {
        getOwned(userId, conversationId);
        // 逻辑删除：消息保留在库里便于审计，但接口不再返回
        conversationRepository.deleteById(conversationId);
    }

    // ══════════════════ 消息 ══════════════════

    /**
     * 追加一条消息并更新会话计数。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiMessage appendMessage(Long conversationId,
                                   String userId,
                                   String role,
                                   String content,
                                   CardsVO cards,
                                   PendingActionVO pendingAction,
                                   String model,
                                   Integer promptTokens,
                                   Integer completionTokens,
                                   Integer latencyMs) {
        AiMessage message = AiMessage.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role(role)
                .content(AiTextUtils.truncate(content, 20000))
                .cards(serializeExtras(cards, pendingAction))
                .model(model)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .latencyMs(latencyMs)
                .build();
        messageRepository.insert(message);

        AiConversation conversation = conversationRepository.selectById(conversationId);
        if (conversation != null) {
            int count = conversation.getMessageCount() == null ? 0 : conversation.getMessageCount();
            conversation.setMessageCount(count + 1);
            conversation.setLastMessageAt(LocalDateTime.now());
            conversationRepository.updateById(conversation);
        }
        return message;
    }

    /**
     * 历史消息（游标分页，按 messageId 升序返回）。
     */
    public MessagePageVO messages(String userId, Long conversationId, Long beforeMessageId, Integer limit) {
        getOwned(userId, conversationId);
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 50);
        List<AiMessage> rows = messageRepository.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .in(AiMessage::getRole, AiConstants.ROLE_USER, AiConstants.ROLE_ASSISTANT,
                                AiConstants.ROLE_SYSTEM)
                        .lt(beforeMessageId != null, AiMessage::getMessageId, beforeMessageId)
                        .orderByDesc(AiMessage::getMessageId)
                        .last("LIMIT " + (safeLimit + 1)));
        boolean hasMore = rows.size() > safeLimit;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, safeLimit));
        }
        // 查询是倒序（为了取"最新 N 条"），返回前翻回正序，前端直接 append
        Collections.reverse(rows);
        List<ChatMessageVO> items = rows.stream().map(this::toMessageVO).toList();
        // 没有更多时把游标置空：返回一个"下一页其实是空"的游标，前端容易误判成还能加载
        Long next = !hasMore || items.isEmpty() ? null : items.get(0).getMessageId();
        return MessagePageVO.builder()
                .items(items)
                .hasMore(hasMore)
                .nextBeforeMessageId(next)
                .build();
    }

    /**
     * 取最近 N 条消息（只含 user/assistant），用于回灌给模型。
     */
    public List<AiMessage> recentHistory(Long conversationId, int window) {
        List<AiMessage> rows = messageRepository.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .in(AiMessage::getRole, AiConstants.ROLE_USER, AiConstants.ROLE_ASSISTANT)
                        .orderByDesc(AiMessage::getMessageId)
                        .last("LIMIT " + Math.max(1, window)));
        Collections.reverse(rows);
        return rows;
    }

    public AiMessage findLatestAssistantMessage(Long conversationId) {
        return messageRepository.selectOne(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .eq(AiMessage::getRole, AiConstants.ROLE_ASSISTANT)
                        .orderByDesc(AiMessage::getMessageId)
                        .last("LIMIT 1"));
    }

    /**
     * 先插入一条空的助手消息，拿到 messageId 后再流式生成。
     *
     * <p>这样 {@code meta} 事件里就能带上稳定的 messageId，前端可以用它做
     * "同一条消息的增量更新"，刷新页面后也能对上同一条记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiMessage createAssistantPlaceholder(Long conversationId, String userId, String model) {
        return appendMessage(conversationId, userId, AiConstants.ROLE_ASSISTANT, "",
                null, null, model, null, null, null);
    }

    /**
     * 生成结束后回填助手消息的内容与用量。
     */
    @Transactional(rollbackFor = Exception.class)
    public void finalizeAssistantMessage(Long messageId,
                                         String content,
                                         CardsVO cards,
                                         PendingActionVO pendingAction,
                                         Integer promptTokens,
                                         Integer completionTokens,
                                         Integer latencyMs) {
        AiMessage message = messageRepository.selectById(messageId);
        if (message == null) {
            return;
        }
        message.setContent(AiTextUtils.truncate(content, 20000));
        message.setCards(serializeExtras(cards, pendingAction));
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setLatencyMs(latencyMs);
        messageRepository.updateById(message);
    }

    // ══════════════════ 内部 ══════════════════

    private String serializeExtras(CardsVO cards, PendingActionVO action) {
        boolean emptyCards = cards == null || cards.hasNoCards();
        if (emptyCards && action == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(new StoredExtras(cards, action));
        } catch (RuntimeException exception) {
            log.warn("序列化消息卡片失败，已跳过：{}", exception.getMessage());
            return null;
        }
    }

    private ChatMessageVO toMessageVO(AiMessage message) {
        StoredExtras extras = deserializeExtras(message.getCards());
        return ChatMessageVO.builder()
                .messageId(message.getMessageId())
                .role(message.getRole())
                .content(message.getContent())
                .cards(extras == null ? null : extras.getCards())
                .pendingAction(extras == null ? null : extras.getPendingAction())
                .createTime(message.getCreateTime())
                .build();
    }

    private StoredExtras deserializeExtras(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, StoredExtras.class);
        } catch (RuntimeException exception) {
            // 历史数据格式变更时不至于让整个列表 500，降级为"没有卡片"
            log.warn("反序列化消息卡片失败：{}", exception.getMessage());
            return null;
        }
    }
}
