package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.AdminKeywordQuery;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminSensitiveWordDTO;
import com.linkx.server.controller.admin.vo.AdminSensitiveWordVO;
import com.linkx.server.entity.SysSensitiveWord;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SensitiveWordMapper;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.service.admin.AdminSensitiveWordService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminSensitiveWordServiceImpl implements AdminSensitiveWordService {

    private static final Set<String> ACTIONS = Set.of(
            SysSensitiveWord.ACTION_FILTER,
            SysSensitiveWord.ACTION_BLOCK,
            SysSensitiveWord.ACTION_ALERT
    );
    private static final Set<String> CATEGORIES = Set.of("general", "politics", "violence", "ad");

    private final SensitiveWordMapper sensitiveWordMapper;
    private final SensitiveWordService sensitiveWordService;

    @Override
    public PageResultVO<AdminSensitiveWordVO> list(AdminPageQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create();
        String kw = AdminKeywordQuery.forLike(query.getKeyword());
        if (kw != null) {
            qw.and((QueryWrapper w) -> {
                w.where(SysSensitiveWord::getWord).like(kw)
                        .or(SysSensitiveWord::getCategory).like(kw)
                        .or(SysSensitiveWord::getAction).like(kw);
            });
        }
        if (query.getStatus() != null) {
            qw.and(SysSensitiveWord::getEnabled).eq(query.getStatus() == 1);
        }
        qw.orderBy(SysSensitiveWord::getUpdateTime, false);
        long total = sensitiveWordMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminSensitiveWordVO> items = sensitiveWordMapper.selectListByQuery(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminSensitiveWordVO detail(Long id) {
        return toVO(requireWord(id));
    }

    @Override
    @Transactional
    public AdminSensitiveWordVO create(AdminSensitiveWordDTO dto, Long operatorId) {
        String word = normalizeWord(dto.getWord());
        ensureUnique(word, null);
        String action = normalizeAction(dto.getAction());
        Date now = new Date();
        SysSensitiveWord entity = SysSensitiveWord.builder()
                .word(word)
                .category(normalizeCategory(dto.getCategory()))
                .action(action)
                .replacement(normalizeReplacement(action, dto.getReplacement()))
                .enabled(dto.getEnabled() == null || Boolean.TRUE.equals(dto.getEnabled()))
                .createTime(now)
                .updateTime(now)
                .build();
        sensitiveWordMapper.insert(entity);
        sensitiveWordService.refreshDictionary();
        return toVO(entity);
    }

    @Override
    @Transactional
    public AdminSensitiveWordVO update(Long id, AdminSensitiveWordDTO dto, Long operatorId) {
        SysSensitiveWord entity = requireWord(id);
        String word = normalizeWord(dto.getWord());
        ensureUnique(word, id);
        String action = normalizeAction(dto.getAction());
        entity.setWord(word);
        entity.setCategory(normalizeCategory(dto.getCategory()));
        entity.setAction(action);
        entity.setReplacement(normalizeReplacement(action, dto.getReplacement()));
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        entity.setUpdateTime(new Date());
        sensitiveWordMapper.update(entity);
        sensitiveWordService.refreshDictionary();
        return toVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        requireWord(id);
        sensitiveWordMapper.deleteById(id);
        sensitiveWordService.refreshDictionary();
    }

    private void ensureUnique(String word, Long excludeId) {
        QueryWrapper qw = QueryWrapper.create().where(SysSensitiveWord::getWord).eq(word);
        if (excludeId != null) {
            qw.and(SysSensitiveWord::getId).ne(excludeId);
        }
        if (sensitiveWordMapper.selectCountByQuery(qw) > 0) {
            throw new CustomException(400, "sensitive word already exists");
        }
    }

    private static String normalizeWord(String word) {
        if (!StringUtils.hasText(word)) {
            throw new CustomException(400, "word required");
        }
        return word.trim();
    }

    private static String normalizeAction(String action) {
        String a = action == null ? "" : action.trim().toLowerCase();
        if (!ACTIONS.contains(a)) {
            throw new CustomException(400, "invalid action");
        }
        return a;
    }

    private static String normalizeCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return "general";
        }
        String c = category.trim().toLowerCase();
        return CATEGORIES.contains(c) ? c : "general";
    }

    private static String normalizeReplacement(String action, String replacement) {
        if (!SysSensitiveWord.ACTION_FILTER.equals(action)) {
            // 非替换策略清空替换文本（空串保证 update 写入，避免 null 被忽略）
            return "";
        }
        return StringUtils.hasText(replacement) ? replacement.trim() : "***";
    }

    private AdminSensitiveWordVO toVO(SysSensitiveWord entity) {
        boolean isFilter = SysSensitiveWord.ACTION_FILTER.equals(entity.getAction());
        String replacement = entity.getReplacement();
        if (!isFilter || !StringUtils.hasText(replacement)) {
            replacement = null;
        }
        return AdminSensitiveWordVO.builder()
                .id(entity.getId())
                .word(entity.getWord())
                .category(entity.getCategory())
                .action(entity.getAction())
                .replacement(replacement)
                .enabled(entity.getEnabled())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private SysSensitiveWord requireWord(Long id) {
        SysSensitiveWord entity = sensitiveWordMapper.selectOneById(id);
        if (entity == null) {
            throw new CustomException(404, "sensitive word not found");
        }
        return entity;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? AdminConstants.DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return AdminConstants.DEFAULT_SIZE;
        }
        return Math.min(size, AdminConstants.MAX_SIZE);
    }
}
