package com.linkx.server.common;


import com.mybatisflex.core.query.QueryWrapper;

/**
 * 朋友圈动态搜索：优先 search_text FULLTEXT；历史数据回退 content/location FULLTEXT 或 LIKE。
 */
public final class MomentsPostSearchSupport {

    private MomentsPostSearchSupport() {
    }

    public static boolean preferFulltext(boolean contentEncryptionEnabled) {
        return !contentEncryptionEnabled;
    }

    public static void applyContentSearch(QueryWrapper qw, String keyword, boolean contentEncryptionEnabled) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String q = keyword.trim();
        if (q.length() > 64) {
            q = q.substring(0, 64);
        }
        if (contentEncryptionEnabled) {
            qw.and("(search_text IS NOT NULL AND MATCH(search_text) AGAINST (? IN NATURAL LANGUAGE MODE))", q);
            return;
        }
        qw.and("((search_text IS NOT NULL AND MATCH(search_text) AGAINST (? IN NATURAL LANGUAGE MODE)) "
                + "OR MATCH(content, location) AGAINST (? IN NATURAL LANGUAGE MODE))", q, q);
    }
}
