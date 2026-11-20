package com.example.zhihuiqingtu.team.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberVO {

    private Long memberId;

    private String userId;

    private Integer memberStatus;

    private LocalDateTime joinTime;
}
