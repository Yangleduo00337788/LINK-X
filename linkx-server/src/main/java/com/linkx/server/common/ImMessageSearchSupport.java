package com.linkx.server.common;


import com.mybatisflex.core.query.QueryWrapper;

/**
 * IM 消息搜索：优先 search_text FULLTEXT；历史数据回退 content/file_name FULLTEXT 或 LIKE。
 */
public final class ImMessageSearchSupport {

    private ImMessageSearchSupport() {
    }

    public static boolean preferFulltext(boolean messageEncryptionEnabled) {
        return !messageEncryptionEnabled;
    }

    public static void applyContentSearch(QueryWrapper qw, String keyword, boolean messageEncryptionEnabled) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String q = keyword.trim();
        if (messageEncryptionEnabled) {
            qw.and("(search_text IS NOT NULL AND MATCH(search_text) AGAINST (? IN NATURAL LANGUAGE MODE))", q);
            return;
        }
        qw.and("((search_text IS NOT NULL AND MATCH(search_text) AGAINST (? IN NATURAL LANGUAGE MODE)) "
                + "OR MATCH(content, file_name) AGAINST (? IN NATURAL LANGUAGE MODE))", q, q);
    }

    public static void applyFileNameSearch(QueryWrapper qw, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String q = keyword.trim();
        qw.and("(file_name LIKE ? OR (search_text IS NOT NULL AND search_text LIKE ?))",
                SqlLikeUtils.containsPattern(q), SqlLikeUtils.containsPattern(q));
    }
}
