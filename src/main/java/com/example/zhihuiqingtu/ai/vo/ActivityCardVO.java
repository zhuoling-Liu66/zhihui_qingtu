package com.example.zhihuiqingtu.ai.vo;

import com.example.zhihuiqingtu.vo.activity.ActivityTagVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动卡片（前端直接渲染，也可点击进详情页）。
 *
 * <p>字段裁剪原则：只给模型和前端都需要的字段。手机号、发布者联系方式一律不出现在这里——
 * 卡片数据会被塞进 prompt，字段越多泄漏面越大。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityCardVO {

    private Long activityId;
    private String activityTitle;
    private Integer activityType;
    private String activityTypeName;
    private String coverUrl;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String activityLocation;
    /** 剩余名额；不限人数时为 null。 */
    private Integer remaining;
    /** 是否限人数。 */
    private Boolean limited;
    private BigDecimal activityCredit;
    private Integer creditType;
    private String creditTypeName;
    private BigDecimal volunteerHours;
    private List<ActivityTagVO> tags;
    /** 推荐理由（必填）：必须引用真实特征，不能是"很适合你"这种空话。 */
    private String reason;
    /** 热度归一化值 0~1。 */
    private Double hotScore;
}
