package com.example.zhihuiqingtu.ai.tool;

import com.example.zhihuiqingtu.ai.config.AiProperties;
import com.example.zhihuiqingtu.ai.engine.RecommendationEngine;
import com.example.zhihuiqingtu.ai.repository.AiToolAuditLogRepository;
import com.example.zhihuiqingtu.ai.service.AiActionService;
import com.example.zhihuiqingtu.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.repository.ActivityTagRelRepository;
import com.example.zhihuiqingtu.repository.ActivityTagRepository;
import com.example.zhihuiqingtu.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.repository.TeamMainRepository;
import com.example.zhihuiqingtu.repository.UserInfoRepository;
import com.example.zhihuiqingtu.repository.UserInterestRelRepository;
import com.example.zhihuiqingtu.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 工具依赖的集中持有者。
 *
 * <p>目的是让 {@link AiToolSet} 的构造参数保持"用户态 3 个 + 依赖 1 个"，
 * 而不是十几个 Repository——后者每加一个工具就要改一次构造函数，很快就会写错顺序。
 */
@Component
@RequiredArgsConstructor
public class AiToolDependencies {

    public final ActivityMainRepository activityMainRepository;
    public final ActivityTagRelRepository activityTagRelRepository;
    public final ActivityTagRepository activityTagRepository;
    public final ActivityEnrollRepository activityEnrollRepository;
    public final CreditRecordRepository creditRecordRepository;
    public final TeamMainRepository teamMainRepository;
    public final UserInfoRepository userInfoRepository;
    public final UserInterestRelRepository userInterestRelRepository;
    public final ActivityService activityService;
    public final AiActionService actionService;
    public final RecommendationEngine recommendationEngine;
    public final AiToolAuditLogRepository auditLogRepository;
    public final AiProperties properties;
    public final ObjectMapper objectMapper;
}
