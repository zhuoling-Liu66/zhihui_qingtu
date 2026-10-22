package com.example.zhihuiqingtu.ai.controller;

import com.example.zhihuiqingtu.ai.service.AiActionService;
import com.example.zhihuiqingtu.ai.vo.ActionConfirmVO;
import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 待确认动作接口。
 *
 * <p>这是"AI 能不能真的写数据"的唯一入口，而且它<b>不经过模型</b>：
 * 用户点确认 → 普通 HTTP 请求 → 复用既有报名 Service。
 */
@Tag(name = "AI 助手-待确认动作", description = "确认/取消 AI 发起的写操作")
@RestController
@RequestMapping("/api/ai/actions")
@RequiredArgsConstructor
public class AiActionController {

    private final AiActionService actionService;
    private final CurrentUser currentUser;

    @Operation(summary = "确认动作（真正执行报名）",
            description = "幂等：已执行的动作重复确认会返回原结果，不会重复报名")
    @PostMapping("/{actionId}/confirm")
    public ApiResponse<ActionConfirmVO> confirm(@PathVariable String actionId) {
        ActionConfirmVO result = actionService.confirm(currentUser.requireUserId(), actionId);
        return ApiResponse.success("报名成功", result);
    }

    @Operation(summary = "取消动作")
    @PostMapping("/{actionId}/cancel")
    public ApiResponse<ActionConfirmVO> cancel(@PathVariable String actionId) {
        return ApiResponse.success("已取消", actionService.cancel(currentUser.requireUserId(), actionId));
    }
}
