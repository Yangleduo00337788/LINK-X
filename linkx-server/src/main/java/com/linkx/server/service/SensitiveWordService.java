package com.linkx.server.service;

import com.linkx.server.entity.SysSensitiveWord;
import com.linkx.server.mapper.SensitiveWordMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 敏感词过滤服务（DFA 自动机算法）。
 * <p>
 * 启动时加载全部启用的敏感词构建 DFA 树，每 5 分钟定时刷新。
 * 提供三种处理策略：filter（替换）、block（拦截）、alert（仅标记，不拦截）。
 * </p>
 */
@Slf4j
@Service
public class SensitiveWordService {

    private final SensitiveWordMapper sensitiveWordMapper;

    /** DFA 根节点（不可变快照，通过 AtomicReference 实现无锁热替换） */
    private final AtomicReference<DfaNode> rootNodeRef = new AtomicReference<>(new DfaNode());

    /** action 缓存：word -> action */
    private final AtomicReference<Map<String, String>> actionCacheRef = new AtomicReference<>(Map.of());

    /** replacement 缓存：word -> replacement */
    private final AtomicReference<Map<String, String>> replacementCacheRef = new AtomicReference<>(Map.of());

    public SensitiveWordService(SensitiveWordMapper sensitiveWordMapper) {
        this.sensitiveWordMapper = sensitiveWordMapper;
    }

    @PostConstruct
    void init() {
        refreshDictionary();
    }

    /** 每 5 分钟刷新敏感词库 */
    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void refreshDictionary() {
        try {
            List<SysSensitiveWord> words = sensitiveWordMapper.selectListByQuery(
                    QueryWrapper.create().where(SysSensitiveWord::getEnabled).eq(true)
            );

            DfaNode newRoot = new DfaNode();
            java.util.Map<String, String> newActions = new ConcurrentHashMap<>();
            java.util.Map<String, String> newReplacements = new ConcurrentHashMap<>();

            for (SysSensitiveWord sw : words) {
                if (sw.getWord() == null || sw.getWord().isBlank()) continue;
                insertWord(newRoot, sw.getWord().toLowerCase());
                newActions.put(sw.getWord().toLowerCase(), sw.getAction());
                if (sw.getReplacement() != null) {
                    newReplacements.put(sw.getWord().toLowerCase(), sw.getReplacement());
                }
            }

            rootNodeRef.set(newRoot);
            actionCacheRef.set(newActions);
            replacementCacheRef.set(newReplacements);
            log.info("敏感词库刷新完成，共加载 {} 条", words.size());
        } catch (Exception e) {
            log.error("刷新敏感词库失败", e);
        }
    }

    /**
     * 检测文本中是否包含敏感词。
     *
     * @return true 如果包含任何敏感词
     */
    public boolean containsSensitive(String text) {
        if (text == null || text.isBlank()) return false;
        return detect(text).hasMatch;
    }

    /**
     * 过滤文本，根据策略返回处理后的文本。
     *
     * @return 过滤结果
     */
    public FilterResult filter(String text) {
        if (text == null || text.isBlank()) {
            return new FilterResult(text, false, false, List.of());
        }

        DfaNode root = rootNodeRef.get();
        Map<String, String> actions = actionCacheRef.get();
        Map<String, String> replacements = replacementCacheRef.get();

        String lowerText = text.toLowerCase();
        StringBuilder result = new StringBuilder(text);
        boolean blocked = false;
        boolean filtered = false;
        java.util.List<String> matchedWords = new java.util.ArrayList<>();

        int i = 0;
        while (i < lowerText.length()) {
            DfaNode current = root;
            int matchEnd = -1;
            String matchedWord = null;
            int j = i;

            while (j < lowerText.length()) {
                DfaNode next = current.children.get(lowerText.charAt(j));
                if (next == null) break;
                current = next;
                if (current.isEnd) {
                    matchEnd = j;
                    // 取最长匹配
                    for (java.util.Map.Entry<String, String> entry : actions.entrySet()) {
                        String word = entry.getKey();
                        if (word.length() == (j - i + 1) && lowerText.substring(i, j + 1).equals(word)) {
                            matchedWord = word;
                        }
                    }
                }
                j++;
            }

            if (matchedWord != null) {
                matchedWords.add(matchedWord);
                String action = actions.getOrDefault(matchedWord, SysSensitiveWord.ACTION_FILTER);
                String replacement = replacements.getOrDefault(matchedWord, "***");

                if (SysSensitiveWord.ACTION_BLOCK.equals(action)) {
                    blocked = true;
                    break;
                } else if (SysSensitiveWord.ACTION_FILTER.equals(action)) {
                    result.replace(i, matchEnd + 1, replacement);
                    filtered = true;
                    i += replacement.length();
                } else {
                    // alert: 不修改文本，仅标记
                    filtered = true;
                    i = matchEnd + 1;
                }
            } else {
                i++;
            }
        }

        return new FilterResult(
                blocked ? text : result.toString(),
                filtered,
                blocked,
                matchedWords
        );
    }

    /**
     * 获取文本中匹配的敏感词列表（仅检测，不修改）。
     */
    public DetectionResult detect(String text) {
        if (text == null || text.isBlank()) {
            return new DetectionResult(false, List.of());
        }

        DfaNode root = rootNodeRef.get();
        String lowerText = text.toLowerCase();
        java.util.List<String> matches = new java.util.ArrayList<>();

        int i = 0;
        while (i < lowerText.length()) {
            DfaNode current = root;
            int j = i;
            String longestMatch = null;

            while (j < lowerText.length()) {
                DfaNode next = current.children.get(lowerText.charAt(j));
                if (next == null) break;
                current = next;
                if (current.isEnd) {
                    String candidate = lowerText.substring(i, j + 1);
                    if (longestMatch == null || candidate.length() > longestMatch.length()) {
                        longestMatch = candidate;
                    }
                }
                j++;
            }

            if (longestMatch != null) {
                matches.add(longestMatch);
                i += longestMatch.length();
            } else {
                i++;
            }
        }

        return new DetectionResult(!matches.isEmpty(), matches);
    }

    // ---- DFA 内部结构 ----

    private static class DfaNode {
        final Map<Character, DfaNode> children = new ConcurrentHashMap<>();
        boolean isEnd;
    }

    private void insertWord(DfaNode root, String word) {
        DfaNode current = root;
        for (char c : word.toCharArray()) {
            current = current.children.computeIfAbsent(c, k -> new DfaNode());
        }
        current.isEnd = true;
    }

    // ---- 结果类 ----

    public record FilterResult(String text, boolean filtered, boolean blocked, List<String> matchedWords) {
    }

    public record DetectionResult(boolean hasMatch, List<String> matches) {
    }
}
