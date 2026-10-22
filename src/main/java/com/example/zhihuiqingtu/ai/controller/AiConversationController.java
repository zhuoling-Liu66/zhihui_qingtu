package com.example.zhihuiqingtu.ai.controller;

import com.example.zhihuiqingtu.ai.dto.CreateConversationRequest;
import com.example.zhihuiqingtu.ai.dto.RenameConversationRequest;
import com.example.zhihuiqingtu.ai.entity.AiConversation;
import com.example.zhihuiqingtu.ai.service.AiConversationService;
import com.example.zhihuiqingtu.ai.vo.ConversationVO;
import com.example.zhihuiqingtu.ai.vo.MessagePageVO;
import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 会话管理。
 *
 * <p>所有接口只能操作"自己的"会话：不属于当前用户统一返回 404，
 * 不返回 403——403 会暴露"这个 ID 确实存在"。
 */
@Tag(name = "AI 助手-会话", description = "会话创建、列表、历史消息、重命名、删除")
@RestController
@RequestMapping("/api/ai/conversations")
@RequiredArgsConstructor
public class AiConversationController {

    private final AiConversationService conversationService;
    private final CurrentUser currentUser;

    @Operation(summary = "创建会话")
    @PostMapping
    public ApiResponse<ConversationVO> create(@Valid @RequestBody(required = false) CreateConversationRequest request) {
        String title = request == null ? null : request.getTitle();
        AiConversation conversation = conversationService.create(currentUser.requireUserId(), title, "chat");
        return ApiResponse.success("会话创建成功", toVO(conversation));
    }

    @Operation(summary = "我的会话列表", description = "页码从 0 开始，与项目其他列表一致")
    @GetMapping
    public ApiResponse<PageResponse<ConversationVO>> list(
            @RequestParam(value = "pageNo", defaultValue = "0") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return ApiResponse.success(conversationService.list(currentUser.requireUserId(), pageNo, pageSize));
    }

    @Operation(summary = "历史消息（游标分页）",
            description = "beforeMessageId 为空表示取最新一页；返回按 messageId 升序，前端直接追加")
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<MessagePageVO> messages(
            @PathVariable Long conversationId,
            @RequestParam(value = "beforeMessageId", required = false) Long beforeMessageId,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit) {
        return ApiResponse.success(conversationService.messages(
                currentUser.requireUserId(), conversationId, beforeMessageId, limit));
    }

    @Operation(summary = "重命名会话")
    @PatchMapping("/{conversationId}")
    public ApiResponse<Void> rename(@PathVariable Long conversationId,
                                    @Valid @RequestBody RenameConversationRequest request) {
        conversationService.rename(currentUser.requireUserId(), conversationId, request.getTitle());
        return ApiResponse.success("重命名成功", null);
    }

    @Operation(summary = "删除会话", description = "逻辑删除；消息不再返回，审计日志保留")
    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> delete(@PathVariable Long conversationId) {
        conversationService.delete(currentUser.requireUserId(), conversationId);
        return ApiResponse.success("会话已删除", null);
    }

    private ConversationVO toVO(AiConversation conversation) {
        return ConversationVO.builder()
                .conversationId(conversation.getConversationId())
                .title(conversation.getTitle())
                .messageCount(conversation.getMessageCount())
                .lastMessageAt(conversation.getLastMessageAt())
                .createTime(conversation.getCreateTime())
                .build();
    }
}
