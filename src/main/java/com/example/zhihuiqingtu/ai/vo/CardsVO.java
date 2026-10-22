package com.example.zhihuiqingtu.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次回复里携带的结构化卡片集合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardsVO {

    @Builder.Default
    private List<ActivityCardVO> activities = new ArrayList<>();

    @Builder.Default
    private List<TeamCardVO> teams = new ArrayList<>();

    /**
     * 是否没有任何卡片。
     *
     * <p>方法名刻意不用 {@code isEmpty()}：Jackson 会把 {@code isXxx()} 当成 Bean 属性，
     * 于是响应里会多出一个 {@code "empty": false} 字段，既污染契约又让前端困惑。
     */
    public boolean hasNoCards() {
        return (activities == null || activities.isEmpty()) && (teams == null || teams.isEmpty());
    }
}
