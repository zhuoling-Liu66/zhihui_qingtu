CREATE DATABASE IF NOT EXISTS campus_activity_platform
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_unicode_ci;

USE campus_activity_platform;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS user_info (
    student_no VARCHAR(20) NOT NULL COMMENT '学号或工号，作为用户主键',
    user_name VARCHAR(50) NOT NULL COMMENT '姓名',
    user_phone VARCHAR(20) NULL COMMENT '手机号',
    user_email VARCHAR(100) NULL COMMENT '邮箱',
    user_role TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1-学生 2-管理员 3-老师',
    publisher_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未开通发布权限 1-已开通发布权限',
    major VARCHAR(100) NULL COMMENT '专业',
    grade VARCHAR(20) NULL COMMENT '年级',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码摘要',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删除标记，0-正常 1-删除',
    PRIMARY KEY (student_no),
    UNIQUE KEY uk_user_phone (user_phone, delete_flag),
    UNIQUE KEY uk_user_email (user_email, delete_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

CREATE TABLE IF NOT EXISTS activity_tag (
    tag_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签主键',
    tag_name VARCHAR(50) NOT NULL COMMENT '标签名称',
    tag_desc VARCHAR(255) NULL COMMENT '标签说明',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删除标记，0-正常 1-删除',
    PRIMARY KEY (tag_id),
    UNIQUE KEY uk_activity_tag_name (tag_name, delete_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动标签表';

CREATE TABLE IF NOT EXISTS activity_main (
    activity_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '活动主键，自动生成',
    publisher_user_id VARCHAR(20) NOT NULL COMMENT '发布者学号或工号',
    activity_title VARCHAR(255) NOT NULL COMMENT '活动标题',
    activity_desc TEXT NULL COMMENT '活动描述',
    activity_type TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1-第二课堂 2-志愿服务 3-运动健身 4-兴趣团建',
    activity_credit DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '活动学分，仅第二课堂学分活动使用',
    credit_type TINYINT UNSIGNED NULL COMMENT '学分类型，1-创新创业 2-智育 3-德育 4-体育 5-美育 6-劳育，仅第二课堂学分活动使用',
    volunteer_hours DECIMAL(6,2) NULL COMMENT '志愿服务时长，仅志愿服务活动使用',
    cover_url VARCHAR(500) NULL COMMENT '活动封面地址',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME NOT NULL COMMENT '结束时间',
    activity_location VARCHAR(255) NULL COMMENT '活动地点',
    max_participant INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最大参与人数，0表示不限',
    current_participant INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前报名人数',
    activity_status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1-已发布 4-已取消',
    sign_in_enabled TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否开放签到，0-关闭 1-开启',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删除标记，0-正常 1-删除',
    PRIMARY KEY (activity_id),
    KEY idx_activity_publisher (publisher_user_id),
    KEY idx_activity_type_status (activity_type, activity_status),
    KEY idx_activity_time (start_time, end_time),
    CONSTRAINT fk_activity_publisher FOREIGN KEY (publisher_user_id) REFERENCES user_info (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动主表';

CREATE TABLE IF NOT EXISTS activity_tag_rel (
    rel_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联主键',
    activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    tag_id BIGINT UNSIGNED NOT NULL COMMENT '标签ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删除标记，0-正常 1-删除',
    PRIMARY KEY (rel_id),
    UNIQUE KEY uk_activity_tag_rel (activity_id, tag_id, delete_flag),
    CONSTRAINT fk_activity_tag_rel_activity FOREIGN KEY (activity_id) REFERENCES activity_main (activity_id),
    CONSTRAINT fk_activity_tag_rel_tag FOREIGN KEY (tag_id) REFERENCES activity_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动标签关联表';

CREATE TABLE IF NOT EXISTS user_interest_rel (
    rel_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联主键',
    user_id VARCHAR(20) NOT NULL COMMENT '用户学号或工号',
    tag_id BIGINT UNSIGNED NOT NULL COMMENT '标签ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删除标记，0-正常 1-删除',
    PRIMARY KEY (rel_id),
    UNIQUE KEY uk_user_interest_rel (user_id, tag_id, delete_flag),
    CONSTRAINT fk_user_interest_user FOREIGN KEY (user_id) REFERENCES user_info (student_no),
    CONSTRAINT fk_user_interest_tag FOREIGN KEY (tag_id) REFERENCES activity_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户兴趣标签关联表';

CREATE TABLE IF NOT EXISTS activity_enroll (
    enroll_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '报名主键',
    user_id VARCHAR(20) NOT NULL COMMENT '用户学号或工号',
    activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    enroll_status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1-已报名 3-已签到 4-已完成',
    sign_in_time DATETIME NULL COMMENT '签到时间',
    feedback_score TINYINT UNSIGNED NULL COMMENT '活动评分',
    feedback_content VARCHAR(500) NULL COMMENT '活动评价',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删除标记，0-正常 1-删除',
    PRIMARY KEY (enroll_id),
    UNIQUE KEY uk_activity_enroll_user (user_id, activity_id, delete_flag),
    KEY idx_activity_enroll_status (enroll_status),
    CONSTRAINT fk_activity_enroll_user FOREIGN KEY (user_id) REFERENCES user_info (student_no),
    CONSTRAINT fk_activity_enroll_activity FOREIGN KEY (activity_id) REFERENCES activity_main (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动报名表';

CREATE TABLE IF NOT EXISTS team_main (
    team_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '队伍主键',
    activity_id BIGINT UNSIGNED NULL COMMENT '关联活动ID',
    leader_user_id VARCHAR(20) NOT NULL COMMENT '队长学号或工号',
    team_name VARCHAR(100) NOT NULL COMMENT '队伍名称',
    team_desc VARCHAR(500) NULL COMMENT '队伍简介',
    max_size INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最大人数，0表示不限',
    current_size INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前人数',
    team_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-招募中 1-已满员 2-已解散',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删除标记，0-正常 1-删除',
    PRIMARY KEY (team_id),
    KEY idx_team_activity (activity_id),
    KEY idx_team_leader (leader_user_id),
    CONSTRAINT fk_team_activity FOREIGN KEY (activity_id) REFERENCES activity_main (activity_id),
    CONSTRAINT fk_team_leader FOREIGN KEY (leader_user_id) REFERENCES user_info (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='队伍主表';

CREATE TABLE IF NOT EXISTS team_member (
    member_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '队员主键',
    team_id BIGINT UNSIGNED NOT NULL COMMENT '队伍ID',
    user_id VARCHAR(20) NOT NULL COMMENT '用户学号或工号',
    member_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-待审核 1-已加入 2-已驳回 3-已退出',
    join_time DATETIME NULL COMMENT '加入时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删除标记，0-正常 1-删除',
    PRIMARY KEY (member_id),
    UNIQUE KEY uk_team_member_user (team_id, user_id, delete_flag),
    CONSTRAINT fk_team_member_team FOREIGN KEY (team_id) REFERENCES team_main (team_id),
    CONSTRAINT fk_team_member_user FOREIGN KEY (user_id) REFERENCES user_info (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='队伍成员表';

CREATE TABLE IF NOT EXISTS credit_record (
    record_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '学分记录主键',
    user_id VARCHAR(20) NOT NULL COMMENT '用户学号或工号',
    activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    credit DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '获得学分',
    credit_type TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1-创新创业 2-智育 3-德育 4-体育 5-美育 6-劳育',
    verify_user_id VARCHAR(20) NULL COMMENT '审核人学号或工号',
    verify_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-待审核 1-已通过 2-已驳回',
    reject_reason VARCHAR(255) NULL COMMENT '驳回原因',
    verify_time DATETIME NULL COMMENT '审核时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删除标记，0-正常 1-删除',
    PRIMARY KEY (record_id),
    KEY idx_credit_user (user_id),
    KEY idx_credit_verify (verify_status),
    CONSTRAINT fk_credit_user FOREIGN KEY (user_id) REFERENCES user_info (student_no),
    CONSTRAINT fk_credit_activity FOREIGN KEY (activity_id) REFERENCES activity_main (activity_id),
    CONSTRAINT fk_credit_verify_user FOREIGN KEY (verify_user_id) REFERENCES user_info (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学分记录表';

CREATE TABLE IF NOT EXISTS recommend_log (
    log_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '推荐日志主键',
    user_id VARCHAR(20) NOT NULL COMMENT '用户学号或工号',
    activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    recommend_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '推荐时间',
    is_click TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否点击 0-否 1-是',
    is_enroll TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否报名 0-否 1-是',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删除标记，0-正常 1-删除',
    PRIMARY KEY (log_id),
    KEY idx_recommend_user_time (user_id, recommend_time),
    CONSTRAINT fk_recommend_user FOREIGN KEY (user_id) REFERENCES user_info (student_no),
    CONSTRAINT fk_recommend_activity FOREIGN KEY (activity_id) REFERENCES activity_main (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI推荐日志表';

INSERT INTO activity_tag (tag_id, tag_name, tag_desc)
VALUES
    (1, '第二课堂', '适用于第二课堂学分活动'),
    (2, '志愿服务', '适用于志愿类公益服务'),
    (3, '运动健身', '适用于跑步、球类、健身打卡'),
    (4, '兴趣团建', '适用于兴趣社团和团建活动'),
    (5, '创新创业', '适用于竞赛、讲座、创业训练'),
    (6, '社会实践', '适用于实践调研和社会服务')
ON DUPLICATE KEY UPDATE
    tag_name = VALUES(tag_name),
    tag_desc = VALUES(tag_desc),
    update_time = CURRENT_TIMESTAMP,
    delete_flag = 0;
