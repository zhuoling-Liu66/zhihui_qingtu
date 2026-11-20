-- ═══════════════════════════════════════════════════════════════
-- 账号状态（禁用/启用）迁移
--
-- 适用：已经执行过 src/main/resources/Mysql.sql 的存量数据库。
-- 新库直接执行 Mysql.sql 即可，不需要再跑本文件。
--
-- 背景：管理员需要能禁用/启用、注销管理端普通账号（运营人员，user_role=4）。
--   · 禁用：account_status=1，登录被拒，且已签发令牌立即失效
--     （由 Redis 按用户撤销标记 auth:user:revoked:{studentNo} 实现，见 UserRevocationService）；
--   · 注销：复用既有 delete_flag 软删除（@TableLogic），不需要新列。
-- ═══════════════════════════════════════════════════════════════
USE campus_activity_platform;

-- 账号状态：0-正常 1-禁用。
-- 默认 0 保证存量账号不受影响；注销走 delete_flag，与本列无关。
ALTER TABLE user_info
    ADD COLUMN account_status TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '账号状态：0-正常 1-禁用' AFTER publisher_status;

-- 历史数据确认（可选）
-- SELECT account_status, COUNT(*) FROM user_info GROUP BY account_status;