-- 发布权限申请与变更记录表（增量迁移脚本）
--
-- 适用场景：数据库已经按旧版 Mysql.sql 初始化过，只需新增本表，不改动 user_info。
-- 新注册用户的 publisher_status 由应用层写入 0（未开通），数据库列默认值本来就是 0。
-- 已有用户的 publisher_status 保持不变，因此已经发布的活动不会因为本次升级受到影响。
--
-- 执行方式：
--   mysql -u root -p campus_activity_platform < docs/sql/publisher_permission.sql

USE campus_activity_platform;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS publisher_permission_record (
    record_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录主键',
    student_no VARCHAR(20) NOT NULL COMMENT '目标用户学号或工号',
    record_type TINYINT UNSIGNED NOT NULL COMMENT '1-学生申请 2-管理员开通 3-管理员收回',
    record_status TINYINT UNSIGNED NOT NULL COMMENT '0-待审批 1-已通过 2-已驳回 3-已关闭',
    apply_reason VARCHAR(500) NULL COMMENT '申请理由；管理员直接调整时作为操作说明',
    operator_user_id VARCHAR(20) NULL COMMENT '审批人或操作管理员学号',
    operate_time DATETIME NULL COMMENT '审批/操作时间',
    reject_reason VARCHAR(255) NULL COMMENT '驳回或关闭原因',
    pending_key VARCHAR(20) GENERATED ALWAYS AS (
        CASE WHEN record_type = 1 AND record_status = 0 THEN student_no ELSE NULL END
    ) STORED COMMENT '待审批申请唯一键，非待审批记录为 NULL',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删除标记，0-正常 1-删除',
    PRIMARY KEY (record_id),
    UNIQUE KEY uk_publisher_pending (pending_key),
    KEY idx_publisher_student (student_no, create_time),
    KEY idx_publisher_status (record_status),
    KEY idx_publisher_type_status (record_type, record_status),
    CONSTRAINT fk_publisher_record_user FOREIGN KEY (student_no) REFERENCES user_info (student_no),
    CONSTRAINT fk_publisher_record_operator FOREIGN KEY (operator_user_id) REFERENCES user_info (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布权限申请与变更记录表';

-- 可选：如果希望把历史上“注册即开通”的学生统一收回，人工确认后取消注释执行。
-- UPDATE user_info SET publisher_status = 0 WHERE user_role = 1;
