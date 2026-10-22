package com.example.zhihuiqingtu.ai.support;

import com.example.zhihuiqingtu.ai.vo.ActivityCardVO;
import com.example.zhihuiqingtu.ai.vo.PendingActionVO;
import com.example.zhihuiqingtu.ai.vo.TeamCardVO;

import java.util.List;

/**
 * 工具执行过程中的事件出口。
 *
 * <p>工具在模型循环内部执行，它不知道自己是在流式还是非流式场景里。
 * 通过这个接口把"工具开始/结束、产出了哪些卡片、生成了哪个待确认动作"告诉上层：
 * 流式场景实现为写 SSE，非流式场景实现为收集到内存。
 */
public interface AiToolEventSink {

    void toolStarted(String name);

    void toolSucceeded(String name, long latencyMs, int resultCount);

    void toolFailed(String name, long latencyMs, String message);

    void activityCards(List<ActivityCardVO> cards);

    void teamCards(List<TeamCardVO> cards);

    void pendingAction(PendingActionVO action);

    /** 什么都不做的实现（非流式或测试用）。 */
    AiToolEventSink NOOP = new AiToolEventSink() {
        @Override
        public void toolStarted(String name) {
        }

        @Override
        public void toolSucceeded(String name, long latencyMs, int resultCount) {
        }

        @Override
        public void toolFailed(String name, long latencyMs, String message) {
        }

        @Override
        public void activityCards(List<ActivityCardVO> cards) {
        }

        @Override
        public void teamCards(List<TeamCardVO> cards) {
        }

        @Override
        public void pendingAction(PendingActionVO action) {
        }
    };
}
