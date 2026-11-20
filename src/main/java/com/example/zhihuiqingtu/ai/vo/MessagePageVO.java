package com.example.zhihuiqingtu.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import com.example.zhihuiqingtu.common.PageResponse;

/**
 * 历史消息游标分页结果。
 *
 * <p>不用 {@code PageResponse} 是有意的：消息会持续增长，offset 分页在流式写入期间会漂移，
 * 导致"上拉加载"重复或丢消息。游标分页对"聊天记录"这种只增不改的数据天然合适。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagePageVO {

    private List<ChatMessageVO> items;

    /** 是否还有更早的消息。 */
    private Boolean hasMore;

    /** 下一次请求的 beforeMessageId。 */
    private Long nextBeforeMessageId;
}
