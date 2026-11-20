-- ═══════════════════════════════════════════════════════════════
-- 签退 + 按人发放（学分 / 志愿时长）迁移
--
-- 适用：已经执行过 src/main/resources/Mysql.sql 的存量数据库。
-- 新库直接执行 Mysql.sql 即可，不需要再跑本文件。
-- ═══════════════════════════════════════════════════════════════
USE campus_activity_platform;

ALTER TABLE activity_enroll
    ADD COLUMN sign_out_time DATETIME NULL COMMENT '签退时间' AFTER sign_in_time;

ALTER TABLE credit_record
    ADD COLUMN volunteer_hours DECIMAL(6,2) NULL COMMENT '发放的志愿时长，仅志愿服务活动使用' AFTER credit_type,
    MODIFY COLUMN credit_type TINYINT UNSIGNED NULL DEFAULT NULL
        COMMENT '1-创新创业 2-智育 3-德育 4-体育 5-美育 6-劳育；志愿时长记录为空';
