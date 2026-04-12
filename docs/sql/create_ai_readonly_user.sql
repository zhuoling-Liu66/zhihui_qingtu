-- MySQL read-only account template for the Python AI recommendation service.
-- Replace CHANGE_ME_STRONG_PASSWORD before executing this script.

CREATE USER IF NOT EXISTS 'ai_reader'@'localhost' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';

GRANT SELECT ON campus_activity_platform.user_info TO 'ai_reader'@'localhost';
GRANT SELECT ON campus_activity_platform.user_interest_rel TO 'ai_reader'@'localhost';
GRANT SELECT ON campus_activity_platform.activity_main TO 'ai_reader'@'localhost';
GRANT SELECT ON campus_activity_platform.activity_tag TO 'ai_reader'@'localhost';
GRANT SELECT ON campus_activity_platform.activity_tag_rel TO 'ai_reader'@'localhost';
GRANT SELECT ON campus_activity_platform.activity_enroll TO 'ai_reader'@'localhost';
GRANT SELECT ON campus_activity_platform.credit_record TO 'ai_reader'@'localhost';

FLUSH PRIVILEGES;

-- If the Python service runs on another machine, create a host-restricted account instead.
-- Example:
-- CREATE USER IF NOT EXISTS 'ai_reader'@'192.168.1.%' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';
-- GRANT SELECT ON campus_activity_platform.user_info TO 'ai_reader'@'192.168.1.%';
