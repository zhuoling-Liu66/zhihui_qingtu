-- ═══════════════════════════════════════════════════════════════
-- 固定/组合学分 + 固定/灵活志愿时长 迁移
--
-- 适用：已经执行过 src/main/resources/Mysql.sql 的存量数据库。
-- 新库直接执行 Mysql.sql 即可，不需要再跑本文件。
-- ═══════════════════════════════════════════════════════════════
USE campus_activity_platform;

ALTER TABLE activity_main
    ADD COLUMN credit_mode TINYINT UNSIGNED NULL DEFAULT 1
        COMMENT '学分模式：1-固定学分 2-组合学分（按档位与名额分配），仅第二课堂活动使用'
        AFTER credit_type,
    ADD COLUMN volunteer_mode TINYINT UNSIGNED NULL DEFAULT 1
        COMMENT '志愿时长模式：1-固定时长 2-灵活时长（活动结束后逐人录入），仅志愿活动使用'
        AFTER volunteer_hours;

CREATE TABLE IF NOT EXISTS activity_credit_tier (
    tier_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '档位主键',
    activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    credit DECIMAL(5,2) NOT NULL COMMENT '该档学分',
    quota INT UNSIGNED NOT NULL COMMENT '该档名额',
    sort_no INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '排序号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删除标记，0-正常 1-删除',
    PRIMARY KEY (tier_id),
    KEY idx_credit_tier_activity (activity_id, sort_no),
    CONSTRAINT fk_credit_tier_activity FOREIGN KEY (activity_id) REFERENCES activity_main (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组合学分档位表（不同学分对应不同名额）';
