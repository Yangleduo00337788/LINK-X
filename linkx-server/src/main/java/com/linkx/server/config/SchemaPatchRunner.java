package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 遗留：启动时轻量补丁。已由 Flyway {@code db/migration} 接管。
 * <p>
 * 默认关闭；仅在紧急回退且 {@code linkx.schema-patch.enabled=true} 时启用。
 * 后续 Schema 变更请新增 {@code Vn__*.sql}，勿再扩展本类。
 */
@Slf4j
@Component
@Order(1)
@ConditionalOnProperty(prefix = "linkx.schema-patch", name = "enabled", havingValue = "true")
public class SchemaPatchRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public SchemaPatchRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.warn("SchemaPatchRunner 已启用（遗留回退路径）；请尽快改为仅使用 Flyway 迁移");
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
