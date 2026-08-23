package com.linkx.server.common.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.SqlLikeUtils;
import org.springframework.util.StringUtils;

import java.util.Calendar;
import java.util.Date;

/**
 * 管理端关键词搜索：最短长度、通配符校验、LIKE 转义、无时间范围时的默认回溯窗口。
 */
public final class AdminKeywordQuery {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;
    public static final int MAX_LOOKBACK_DAYS = 90;

    private AdminKeywordQuery() {
    }

    /**
     * 供 MyBatis-Flex {@code .like()} 使用的已转义关键词；无效时返回 {@code null}（应忽略该条件）。
     */
    public static String forLike(String raw) {
        String normalized = normalize(raw);
        return normalized == null ? null : SqlLikeUtils.escapeLike(normalized);
    }

    /**
     * 宽松 LIKE（如 IP 片段）：仅转义通配符，不强制最短长度。
     */
    public static String forLikeLoose(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LENGTH);
        }
        if (SqlLikeUtils.isWildcardOnly(trimmed)) {
            return null;
        }
        return SqlLikeUtils.escapeLike(trimmed);
    }

    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LENGTH);
        }
        if (trimmed.length() < MIN_LENGTH) {
            return null;
        }
        if (SqlLikeUtils.isWildcardOnly(trimmed)) {
            return null;
        }
        return trimmed;
    }

    /** 关键词搜索且未指定 startTime 时，返回默认最早 create_time；否则 {@code null}。 */
    public static Date createTimeFloorOrNull(Long startTime, String keyword) {
        if (startTime != null || forLike(keyword) == null) {
            return null;
        }
        return keywordSearchEarliestTime();
    }

    public static Date keywordSearchEarliestTime() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -MAX_LOOKBACK_DAYS);
        return cal.getTime();
    }
}
