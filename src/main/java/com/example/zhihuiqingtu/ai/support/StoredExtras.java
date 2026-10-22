package com.example.zhihuiqingtu.ai.support;

import com.example.zhihuiqingtu.ai.vo.CardsVO;
import com.example.zhihuiqingtu.ai.vo.PendingActionVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息里持久化的结构化载荷。
 *
 * <p>存在 {@code ai_message.cards} 这一个 JSON 列里：卡片与待确认动作都是"这条消息的附属物"，
 * 拆成两列除了让表更宽没有任何好处。刷新页面时反序列化即可还原，不必重新调模型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoredExtras {

    private CardsVO cards;

    private PendingActionVO pendingAction;
}
