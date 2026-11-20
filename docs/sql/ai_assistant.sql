-- =============================================================================
-- 智慧青途 · AI 助手模块建表脚本
-- =============================================================================
-- 版本      : v0.1
-- 日期      : 2026-10-22
-- 适用库    : campus_activity_platform（MySQL 8.0+）
-- 依赖      : 必须先有 user_info / activity_main / team_main（本脚本引用了它们的主键做外键）
-- 影响范围  : 只新增 5 张 ai_ 前缀表，不改动任何既有表，可独立上线 / 回滚
--
-- 执行方式：
--   mysql -u root -p campus_activity_platform < docs/sql/ai_assistant.sql
--
-- 说明：
--   1. 上一版 AI 推荐模块的 recommend_log 表已在 ecc428a 中删除，本脚本【不恢复】它。
--      新版推荐日志拆成两张职责更清楚的表：
--        · ai_recommendation_log —— 曝光/点击/报名归因（用于评估推荐效果）
--        · ai_tool_audit_log     —— 工具调用审计（用于排障与安全审计）
--      混在一张表里的后果是：字段语义互相打架，且审计数据会被"会话删除"连带清掉。
--   2. 所有表沿用项目的 create_time / update_time / delete_flag 三件套，
--      与 entity/BaseEntity 的自动填充和逻辑删除配置对齐。
--   3. 外键沿用既有风格（项目其它表都是物理外键）。
--      ai_tool_audit_log 例外：审计必须能独立于会话长期存在，因此不加外键。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 会话表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_conversation (
    conversation_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话主键',
    user_id          VARCHAR(20)     NOT NULL                COMMENT '所属用户学号或工号',
    title            VARCHAR(100)    NOT NULL DEFAULT '新对话' COMMENT '会话标题（首轮后异步生成，失败取首条消息前 16 字）',
    scene            VARCHAR(20)     NOT NULL DEFAULT 'chat' COMMENT '场景：chat-通用对话 recommend-推荐',
    summary          TEXT            NULL                    COMMENT '较早消息的压缩摘要（message_count>20 时生成）',
    message_count    INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '消息条数（不含 tool 角色）',
    last_message_at  DATETIME        NULL                    COMMENT '最后一条消息时间，用于列表排序',
    status           TINYINT UNSIGNED NOT NULL DEFAULT 1     COMMENT '会话状态：1-正常 0-关闭',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag      TINYINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '软删除标记：0-正常 1-删除',
    PRIMARY KEY (conversation_id),
    KEY idx_ai_conv_user_time (user_id, last_message_at),
    KEY idx_ai_conv_user_create (user_id, create_time),
    CONSTRAINT fk_ai_conv_user FOREIGN KEY (user_id) REFERENCES user_info (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话表';


-- -----------------------------------------------------------------------------
-- 2. 消息表
-- -----------------------------------------------------------------------------
-- 设计要点：
--   · 冗余存 user_id：按用户导出/审计不用 join 会话表；也避免"会话删了、消息成孤儿"
--   · role 用 VARCHAR 而非 ENUM：后续加 summary 等角色不用改表
--   · tool_calls 用 JSON：保存模型返回的工具调用片段，便于复盘
--   · content 用 MEDIUMTEXT：单条回复上限 1500 字，但工具结果可能更长，留足空间
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_message (
    message_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息主键',
    conversation_id  BIGINT UNSIGNED NOT NULL                COMMENT '所属会话',
    user_id          VARCHAR(20)     NOT NULL                COMMENT '所属用户（冗余，便于按用户审计）',
    role             VARCHAR(16)     NOT NULL                COMMENT '角色：user / assistant / tool / system',
    content          MEDIUMTEXT      NULL                    COMMENT '消息文本；assistant 为 Markdown',
    tool_name        VARCHAR(64)     NULL                    COMMENT 'role=tool 时的工具名',
    tool_call_id     VARCHAR(64)     NULL                    COMMENT '与模型返回的 tool_call.id 对应',
    tool_calls       JSON            NULL                    COMMENT 'assistant 消息附带的工具调用片段',
    cards            JSON            NULL                    COMMENT '结构化卡片JSON（刷新页面回填，避免重新调模型）',
    model            VARCHAR(64)     NULL                    COMMENT '生成该消息的模型名',
    prompt_tokens    INT UNSIGNED    NULL                    COMMENT '输入 token 数',
    completion_tokens INT UNSIGNED   NULL                    COMMENT '输出 token 数',
    latency_ms       INT UNSIGNED    NULL                    COMMENT '本次生成耗时（毫秒）',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag      TINYINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '软删除标记：0-正常 1-删除',
    PRIMARY KEY (message_id),
    KEY idx_ai_msg_conv (conversation_id, message_id),
    KEY idx_ai_msg_user_time (user_id, create_time),
    CONSTRAINT fk_ai_msg_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversation (conversation_id),
    CONSTRAINT fk_ai_msg_user FOREIGN KEY (user_id) REFERENCES user_info (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话消息表';


-- -----------------------------------------------------------------------------
-- 3. 待确认动作表（写操作的两阶段确认）
-- -----------------------------------------------------------------------------
-- 关键设计：AI 工具只"登记待确认动作"，不写业务表。
-- 用户点确认后，再由 /api/ai/actions/{id}/confirm 调用既有 Service 真正执行。
-- 这样即使模型被提示词注入，也最多生成一堆待确认卡片，不会产生真实副作用。
--
-- 状态机：
--   PENDING ──确认──► EXECUTED
--      │  ├──取消──► CANCELLED
--      │  ├──过期──► EXPIRED
--      │  └──失败──► FAILED
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_pending_action (
    action_id        VARCHAR(40)     NOT NULL                COMMENT '动作ID：act_ 前缀 + ULID，由应用生成',
    user_id          VARCHAR(20)     NOT NULL                COMMENT '发起人学号或工号',
    conversation_id  BIGINT UNSIGNED NULL                    COMMENT '来源会话（可空，便于独立确认）',
    message_id       BIGINT UNSIGNED NULL                    COMMENT '来源消息',
    type             VARCHAR(32)     NOT NULL                COMMENT '动作类型：ENROLL_ACTIVITY / JOIN_TEAM',
    arguments        JSON            NOT NULL                COMMENT '执行参数：{"activityId":12}',
    summary          VARCHAR(500)    NULL                    COMMENT '展示摘要（活动标题/时间/地点）',
    status           VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/EXECUTED/CANCELLED/EXPIRED/FAILED',
    expires_at       DATETIME        NOT NULL                COMMENT '过期时间（默认创建后 5 分钟）',
    executed_at      DATETIME        NULL                    COMMENT '执行时间',
    result_message   VARCHAR(500)    NULL                    COMMENT '执行结果或失败原因',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag      TINYINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '软删除标记：0-正常 1-删除',
    PRIMARY KEY (action_id),
    KEY idx_ai_action_user_status (user_id, status, expires_at),
    KEY idx_ai_action_conv (conversation_id),
    CONSTRAINT fk_ai_action_user FOREIGN KEY (user_id) REFERENCES user_info (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 待确认动作表（写操作二次确认）';


-- -----------------------------------------------------------------------------
-- 4. 推荐曝光/归因日志
-- -----------------------------------------------------------------------------
-- 用途：
--   · 离线评估推荐效果（CTR、报名转化率）
--   · 刷新页面时回填消息卡片（避免重新调模型）
--   · A/B 对比（规则 vs LLM 精排）
--
-- 注意：不做物理外键级联。活动是逻辑删除（delete_flag=1），
--       若用 ON DELETE CASCADE 反而会在物删时误删统计数据。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_recommendation_log (
    log_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志主键',
    user_id          VARCHAR(20)     NOT NULL                COMMENT '被推荐用户',
    conversation_id  BIGINT UNSIGNED NULL                    COMMENT '来源会话（首页推荐位为空）',
    message_id       BIGINT UNSIGNED NULL                    COMMENT '来源消息',
    target_type      VARCHAR(16)     NOT NULL                COMMENT '目标类型：ACTIVITY / TEAM',
    activity_id      BIGINT UNSIGNED NULL                    COMMENT '活动ID（target_type=ACTIVITY 时必填）',
    team_id          BIGINT UNSIGNED NULL                    COMMENT '队伍ID（target_type=TEAM 时必填）',
    scene            VARCHAR(20)     NOT NULL                COMMENT '场景：HOME / CHAT / FILTER / PERSONALIZED',
    score            DECIMAL(6,4)    NULL                    COMMENT '推荐分数（规则粗排分或精排分）',
    rank_no          SMALLINT UNSIGNED NULL                  COMMENT '展示位次，从 1 开始',
    reason           VARCHAR(200)    NULL                    COMMENT '展示给用户的推荐理由',
    ab_group         VARCHAR(16)     NULL                    COMMENT '实验分组：RULE / LLM（P2 用）',
    is_clicked       TINYINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '是否点击：0-否 1-是',
    is_enrolled      TINYINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '是否报名：0-否 1-是',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag      TINYINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '软删除标记：0-正常 1-删除',
    PRIMARY KEY (log_id),
    KEY idx_ai_rec_user_time (user_id, create_time),
    KEY idx_ai_rec_activity (activity_id),
    KEY idx_ai_rec_conv (conversation_id, message_id),
    KEY idx_ai_rec_team (team_id),
    CONSTRAINT fk_ai_rec_user FOREIGN KEY (user_id) REFERENCES user_info (student_no),
    CONSTRAINT fk_ai_rec_activity FOREIGN KEY (activity_id) REFERENCES activity_main (activity_id),
    CONSTRAINT fk_ai_rec_team FOREIGN KEY (team_id) REFERENCES team_main (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 推荐曝光与归因日志表';


-- -----------------------------------------------------------------------------
-- 5. 工具调用审计
-- -----------------------------------------------------------------------------
-- 与 ai_recommendation_log 的区别：
--   前者记"推荐了什么"，后者记"模型调了什么工具、传了什么参数、结果如何"。
-- 排障时最常看这张表："用户说报名失败" → 查 tool_name=propose_enroll 的 arguments 与 status。
--
-- 刻意不加外键：会话/用户被删除后，审计仍需留存（合规要求，也方便事后复盘）。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_tool_audit_log (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          VARCHAR(20)     NOT NULL                COMMENT '调用用户',
    conversation_id  BIGINT UNSIGNED NULL                    COMMENT '来源会话',
    tool_name        VARCHAR(64)     NOT NULL                COMMENT '工具名',
    arguments        JSON            NULL                    COMMENT '入参（已脱敏，不含身份字段）',
    status           VARCHAR(16)     NOT NULL                COMMENT 'SUCCESS / FAILED / DENIED / TIMEOUT',
    error_code       INT             NULL                    COMMENT '失败时的业务码（400/403/404/409/500）',
    result_summary   VARCHAR(500)    NULL                    COMMENT '结果摘要（截断，不含 PII）',
    latency_ms       INT UNSIGNED    NULL                    COMMENT '耗时（毫秒）',
    trace_id         VARCHAR(64)     NULL                    COMMENT '链路追踪ID，与日志关联',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag      TINYINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '软删除标记：0-正常 1-删除',
    PRIMARY KEY (id),
    KEY idx_ai_tool_user_time (user_id, create_time),
    KEY idx_ai_tool_name_time (tool_name, create_time),
    KEY idx_ai_tool_conv (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 工具调用审计表';


-- =============================================================================
-- 校验
-- =============================================================================
-- 1) 5 张表都在
-- SHOW TABLES LIKE 'ai\_%';

-- 2) 字符集应为 utf8mb4
-- SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'ai\_%';

-- 3) 外键生效
-- SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME
--   FROM information_schema.KEY_COLUMN_USAGE
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'ai\_%'
--    AND REFERENCED_TABLE_NAME IS NOT NULL;


-- =============================================================================
-- 回滚（按依赖顺序）
-- =============================================================================
-- DROP TABLE IF EXISTS ai_tool_audit_log;
-- DROP TABLE IF EXISTS ai_recommendation_log;
-- DROP TABLE IF EXISTS ai_pending_action;
-- DROP TABLE IF EXISTS ai_message;
-- DROP TABLE IF EXISTS ai_conversation;


-- =============================================================================
-- 可选：清理过期待确认动作（建议用 @Scheduled 每小时执行，而不是靠人工）
-- =============================================================================
-- UPDATE ai_pending_action
--    SET status = 'EXPIRED', update_time = NOW()
--  WHERE status = 'PENDING' AND expires_at < NOW() AND delete_flag = 0;

-- =============================================================================
-- 可选：推荐效果速查（上线一周后看这个）
-- =============================================================================
-- SELECT scene,
--        COUNT(*)                                        AS impressions,
--        SUM(is_clicked)                                 AS clicks,
--        ROUND(SUM(is_clicked) / COUNT(*), 4)            AS ctr,
--        SUM(is_enrolled)                                AS enrolls,
--        ROUND(SUM(is_enrolled) / NULLIF(SUM(is_clicked), 0), 4) AS click_to_enroll
--   FROM ai_recommendation_log
--  WHERE delete_flag = 0 AND create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
--  GROUP BY scene;
