-- ═══════════════════════════════════════════════════════════════
-- 活动审核 + 管理端运营人员账号 迁移
--
-- 适用：已经执行过 src/main/resources/Mysql.sql 的存量数据库。
-- 新库直接执行 Mysql.sql 即可，不需要再跑本文件。
-- ═══════════════════════════════════════════════════════════════
USE campus_activity_platform;

-- 1. 活动审核字段
--    默认值 1（已通过）用于兼容历史活动，避免升级后老活动突然从列表消失。
--    新发布的活动由代码显式写入 0（待审核）。
ALTER TABLE activity_main
    ADD COLUMN review_status    TINYINT UNSIGNED NOT NULL DEFAULT 1
        COMMENT '审核状态：0-待审核 1-已通过 2-已驳回',
    ADD COLUMN review_reason    VARCHAR(255) NULL COMMENT '审核驳回原因或备注',
    ADD COLUMN reviewer_user_id VARCHAR(20) NULL COMMENT '审核人学号或工号',
    ADD COLUMN review_time      DATETIME NULL COMMENT '审核时间',
    ADD KEY idx_activity_review (review_status, create_time);

-- 2. 运营人员角色
--    user_role = 4 表示管理端普通账号（非管理员），只能登录管理端发布学分活动。
--    账号由管理员在「账号管理」中创建，也可手工插入（密码需为 BCrypt 摘要）。
--    示例（请替换为真实 BCrypt 密码摘要）：
--    INSERT INTO user_info (student_no, user_name, user_role, publisher_status, password_hash)
--    VALUES ('S0001', '学分运营', 4, 0, '$2a$10$...');

-- 3. 历史数据确认（可选）
--    SELECT review_status, COUNT(*) FROM activity_main GROUP BY review_status;
