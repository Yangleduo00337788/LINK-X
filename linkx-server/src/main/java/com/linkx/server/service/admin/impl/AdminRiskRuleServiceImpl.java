package com.linkx.server.service.admin.impl;

import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminRiskRuleDTO;
import com.linkx.server.controller.admin.dto.AdminRiskRuleSimulateDTO;
import com.linkx.server.controller.admin.vo.AdminRiskRuleSimulateVO;
import com.linkx.server.controller.admin.vo.AdminRiskRuleVO;
import com.linkx.server.entity.admin.SysRiskRule;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.SysRiskRuleMapper;
import com.linkx.server.service.admin.AdminRiskRuleService;
import com.linkx.server.service.admin.ReviewRiskScoringService;
import com.linkx.server.service.admin.rule.RiskRuleContext;
import com.linkx.server.service.admin.rule.RiskRuleEngine;
import com.linkx.server.service.admin.rule.RiskRuleEvaluationResult;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminRiskRuleServiceImpl implements AdminRiskRuleService {

    private final SysRiskRuleMapper riskRuleMapper;
    private final RiskRuleEngine riskRuleEngine;
    private final ReviewRiskScoringService reviewRiskScoringService;

    @Override
    public PageResultVO<AdminRiskRuleVO> list(AdminPageQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create().where(SysRiskRule::getDeleted).eq(0);
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysRiskRule::getName).like(kw)
                        .or(SysRiskRule::getKeyword).like(kw)
                        .or(SysRiskRule::getScope).like(kw);
            });
        }
        if (query.getStatus() != null) {
            qw.and(SysRiskRule::getEnabled).eq(query.getStatus() == 1);
        }
        qw.orderBy(SysRiskRule::getPriority, false)
                .orderBy(SysRiskRule::getUpdateTime, false);
        long total = riskRuleMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminRiskRuleVO> items = riskRuleMapper.selectListByQuery(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminRiskRuleVO detail(Long id) {
        return toVO(requireRule(id));
    }

    @Override
    @Transactional
    public AdminRiskRuleVO create(AdminRiskRuleDTO dto, Long operatorId) {
        SysRiskRule entity = fromDto(dto);
        Date now = new Date();
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
        riskRuleMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public AdminRiskRuleVO update(Long id, AdminRiskRuleDTO dto, Long operatorId) {
        SysRiskRule entity = requireRule(id);
        applyDto(entity, dto);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(new Date());
        riskRuleMapper.update(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        SysRiskRule entity = requireRule(id);
        entity.setDeleted(1);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(new Date());
        riskRuleMapper.update(entity);
    }

    @Override
    public AdminRiskRuleSimulateVO simulate(AdminRiskRuleSimulateDTO dto) {
        RiskRuleContext context = buildContext(dto);
        RiskRuleEvaluationResult result = riskRuleEngine.evaluate(context);
        return toSimulateVO(result);
    }

    private RiskRuleContext buildContext(AdminRiskRuleSimulateDTO dto) {
        Long subjectUserId = dto != null ? dto.getSubjectUserId() : null;
        int historyScore = reviewRiskScoringService.computeUserHistoryScore(subjectUserId);
        return RiskRuleContext.builder()
                .scope(dto != null ? dto.getScope() : "simulate")
                .text(dto != null ? dto.getText() : null)
                .subjectUserId(subjectUserId)
                .historyScore(historyScore)
                .messageCount(dto != null ? dto.getMessageCount() : null)
                .memberCount(dto != null ? dto.getMemberCount() : null)
                .taskRiskLevel(dto != null ? dto.getTaskRiskLevel() : null)
                .sensitiveBlocked(dto != null ? dto.getSensitiveBlocked() : null)
                .sensitiveAlerted(dto != null ? dto.getSensitiveAlerted() : null)
                .sensitiveFiltered(dto != null ? dto.getSensitiveFiltered() : null)
                .escalationCount(dto != null ? dto.getEscalationCount() : null)
                .build();
    }

    private AdminRiskRuleSimulateVO toSimulateVO(RiskRuleEvaluationResult result) {
        List<AdminRiskRuleSimulateVO.MatchedRuleVO> matched = result.getMatchedRules().stream()
                .map(rule -> AdminRiskRuleSimulateVO.MatchedRuleVO.builder()
                        .ruleId(rule.getRuleId())
                        .ruleName(rule.getRuleName())
                        .scoreDelta(rule.getScoreDelta())
                        .actionType(rule.getActionType())
                        .build())
                .collect(Collectors.toList());
        return AdminRiskRuleSimulateVO.builder()
                .scoreDelta(result.getScoreDelta())
                .blocked(result.isBlocked())
                .alerted(result.isAlerted())
                .factors(result.getFactors())
                .matchedRules(matched)
                .build();
    }

    private SysRiskRule requireRule(Long id) {
        SysRiskRule rule = riskRuleMapper.selectOneById(id);
        if (rule == null || Integer.valueOf(1).equals(rule.getDeleted())) {
            throw new CustomException(404, "risk rule not found");
        }
        return rule;
    }

    private SysRiskRule fromDto(AdminRiskRuleDTO dto) {
        SysRiskRule entity = new SysRiskRule();
        applyDto(entity, dto);
        return entity;
    }

    private void applyDto(SysRiskRule entity, AdminRiskRuleDTO dto) {
        entity.setName(normalizeName(dto.getName()));
        entity.setScope(normalizeScope(dto.getScope()));
        entity.setKeyword(normalizeOptional(dto.getKeyword()));
        entity.setConditionJson(normalizeOptional(dto.getConditionJson()));
        entity.setScoreDelta(normalizeScoreDelta(dto.getScoreDelta()));
        entity.setActionType(normalizeActionType(dto.getActionType()));
        entity.setActionConfig(normalizeOptional(dto.getActionConfig()));
        entity.setPriority(normalizePriority(dto.getPriority()));
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        } else if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        validateRule(entity);
    }

    private void validateRule(SysRiskRule entity) {
        if (!StringUtils.hasText(entity.getConditionJson()) && !StringUtils.hasText(entity.getKeyword())) {
            throw new CustomException(400, "keyword or condition required");
        }
    }

    private AdminRiskRuleVO toVO(SysRiskRule rule) {
        return AdminRiskRuleVO.builder()
                .id(rule.getId())
                .name(rule.getName())
                .scope(rule.getScope())
                .keyword(rule.getKeyword())
                .conditionJson(rule.getConditionJson())
                .scoreDelta(rule.getScoreDelta())
                .actionType(rule.getActionType())
                .actionConfig(rule.getActionConfig())
                .priority(rule.getPriority())
                .enabled(rule.getEnabled())
                .createTime(rule.getCreateTime())
                .updateTime(rule.getUpdateTime())
                .build();
    }

    private static String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new CustomException(400, "name required");
        }
        return name.trim();
    }

    private static String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return "global";
        }
        return scope.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeActionType(String actionType) {
        if (!StringUtils.hasText(actionType)) {
            return "score_only";
        }
        return actionType.trim().toLowerCase(Locale.ROOT);
    }

    private static int normalizeScoreDelta(Integer scoreDelta) {
        if (scoreDelta == null) {
            return 0;
        }
        return Math.max(-100, Math.min(100, scoreDelta));
    }

    private static int normalizePriority(Integer priority) {
        if (priority == null) {
            return 0;
        }
        return Math.max(-1000, Math.min(1000, priority));
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
