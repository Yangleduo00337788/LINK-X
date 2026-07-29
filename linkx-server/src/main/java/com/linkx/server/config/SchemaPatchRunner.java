package com.linkx.server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 存量库轻量补丁：确保关键列存在（init.sql 的 ALTER 不会在每次启动自动执行）。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SchemaPatchRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
    ensureColumn(
                "sys_user_relation",
                "group_name",
                "ALTER TABLE `sys_user_relation` ADD COLUMN `group_name` varchar(32) DEFAULT NULL COMMENT '好友分组名' AFTER `remark`"
        );
        ensureColumn(
                "conference",
                "scene",
                "ALTER TABLE `conference` ADD COLUMN `scene` varchar(16) NOT NULL DEFAULT 'meeting' COMMENT '场景: call=电话 meeting=会议' AFTER `type`"
        );
    }

    private void ensureColumn(String table, String column, String alterSql) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = ?
                      AND COLUMN_NAME = ?
                    """,
                    Integer.class,
                    table,
                    column
            );
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute(alterSql);
            log.info("Schema patch applied: {}.{} added", table, column);
        } catch (Exception e) {
            log.error("Schema patch failed for {}.{}: {}", table, column, e.toString());
        }
    }
}
