-- ═══════════════════════════════════════════════════════════════
-- 签退 + 按人发放（学分 / 志愿时长）迁移
--
-- 适用：已经执行过 src/main/resources/Mysql.sql 的存量数据库。
-- 新库直接执行 Mysql.sql 即可，不需要再跑本文件。
--
-- 背景：
--   1. 考勤改为「学生自助签到、签退，发布者可修正」；
--      签到只流转到“已签到”（3），签退才流转到“已完成”（4）并生成发放记录。
--   2. 发放记录复用 credit_record：第二课堂活动存学分，志愿活动存时长，
--      因此 credit_type 允许为空（避免用 0/默认值污染学分分类统计）。
-- ═══════════════════════════════════════════════════════════════
USE campus_activity_platform;

-- 1. 报名表增加签退时间
ALTER TABLE activity_enroll
    ADD COLUMN sign_out_time DATETIME NULL COMMENT '签退时间' AFTER sign_in_time;

-- 2. 发放记录增加志愿时长；志愿活动记录不再强制填写学分类型
ALTER TABLE credit_record
    ADD COLUMN volunteer_hours DECIMAL(6,2) NULL COMMENT '发放的志愿时长，仅志愿服务活动使用' AFTER credit_type,
    MODIFY COLUMN credit_type TINYINT UNSIGNED NULL DEFAULT NULL
        COMMENT '1-创新创业 2-智育 3-德育 4-体育 5-美育 6-劳育；志愿时长记录为空';

-- 3. 历史数据确认（可选）
--    旧的“签到即完成”记录没有签退时间，仍视为已完成；其待审核学分可在
--    「活动详情 → 参与人员」中由发布者/管理员确认发放。
--    SELECT COUNT(*) FROM activity_enroll WHERE enroll_status = 4 AND sign_out_time IS NULL;
