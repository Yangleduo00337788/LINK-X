package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminFeedbackDispatchRuleDTO;
import com.linkx.server.controller.admin.dto.AdminFeedbackDispatchSimulateDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminFeedbackDispatchRuleVO;
import com.linkx.server.controller.admin.vo.AdminFeedbackDispatchSimulateVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysDutySchedule;
import com.linkx.server.entity.admin.SysFeedbackDispatchRule;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysDutyScheduleMapper;
import com.linkx.server.mapper.admin.SysFeedbackDispatchRuleMapper;
import com.linkx.server.service.admin.AdminFeedbackDispatchRuleService;
import com.linkx.server.service.admin.FeedbackDispatchService;
import com.linkx.server.service.admin.rule.FeedbackAssigneeResolver;
import com.linkx.server.service.admin.rule.FeedbackDispatchResult;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminFeedbackDispatchRuleServiceImpl implements AdminFeedbackDispatchRuleService {

    private final SysFeedbackDispatchRuleMapper ruleMapper;
    private final SysUserMapper sysUserMapper;
    private final SysDutyScheduleMapper dutyScheduleMapper;
    private final FeedbackAssigneeResolver assigneeResolver;
    private final FeedbackDispatchService feedbackDispatchService;

    @Override
    public PageResultVO<AdminFeedbackDispatchRuleVO> list(AdminPageQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create().where(SysFeedbackDispatchRule::getDeleted).eq(0);
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysFeedbackDispatchRule::getName).like(kw)
                        .or(SysFeedbackDispatchRule::getFeedbackType).like(kw)
                        .or(SysFeedbackDispatchRule::getKeyword).like(kw);
            });
        }
        if (query.getStatus() != null) {
            qw.and(SysFeedbackDispatchRule::getEnabled).eq(query.getStatus() == 1);
        }
        qw.orderBy(SysFeedbackDispatchRule::getPriority, false)
                .orderBy(SysFeedbackDispatchRule::getUpdateTime, false);
        long total = ruleMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminFeedbackDispatchRuleVO> items = ruleMapper.selectListByQuery(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminFeedbackDispatchRuleVO detail(Long id) {
        return toVO(requireRule(id));
    }

    @Override
    @Transactional
    public AdminFeedbackDispatchRuleVO create(AdminFeedbackDispatchRuleDTO dto, Long operatorId) {
        SysFeedbackDispatchRule entity = fromDto(dto);
        assigneeResolver.validateRuleAssignee(entity);
        Date now = new Date();
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
        ruleMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public AdminFeedbackDispatchRuleVO update(Long id, AdminFeedbackDispatchRuleDTO dto, Long operatorId) {
        SysFeedbackDispatchRule entity = requireRule(id);
        applyDto(entity, dto);
        assigneeResolver.validateRuleAssignee(entity);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(new Date());
        ruleMapper.update(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        SysFeedbackDispatchRule entity = requireRule(id);
        entity.setDeleted(1);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(new Date());
        ruleMapper.update(entity);
    }

    @Override
    public AdminFeedbackDispatchSimulateVO simulate(AdminFeedbackDispatchSimulateDTO dto) {
        Feedback feedback = Feedback.builder()
                .type(dto.getType())
                .content(dto.getContent())
                .status(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "pending")
                .assigneeId(Boolean.TRUE.equals(dto.getHasAssignee()) ? 1L : null)
                .createTime(resolveSimulateCreateTime(dto.getCreateOffsetHours()))
                .build();
        return feedbackDispatchService.evaluate(feedback)
                .map(result -> AdminFeedbackDispatchSimulateVO.builder()
                        .matched(true)
                        .ruleId(result.getRuleId())
                        .ruleName(result.getRuleName())
                        .assigneeId(result.getAssigneeId())
                        .assigneeName(resolveAssigneeName(result.getAssigneeId()))
                        .actionType(result.getActionType())
                        .assigneeSource(result.getAssigneeSource())
                        .notifyRoles(result.getNotifyRoles())
                        .notifyChannels(result.getNotifyChannels())
                        .build())
                .orElse(AdminFeedbackDispatchSimulateVO.builder().matched(false).build());
    }

    private SysFeedbackDispatchRule requireRule(Long id) {
        SysFeedbackDispatchRule rule = ruleMapper.selectOneById(id);
        if (rule == null || Integer.valueOf(1).equals(rule.getDeleted())) {
            throw new CustomException(404, "dispatch rule not found");
        }
        return rule;
    }

    private SysFeedbackDispatchRule fromDto(AdminFeedbackDispatchRuleDTO dto) {
        SysFeedbackDispatchRule entity = new SysFeedbackDispatchRule();
        applyDto(entity, dto);
        return entity;
    }

    private void applyDto(SysFeedbackDispatchRule entity, AdminFeedbackDispatchRuleDTO dto) {
        entity.setName(normalizeName(dto.getName()));
        entity.setFeedbackType(normalizeOptional(dto.getFeedbackType()));
        entity.setKeyword(normalizeOptional(dto.getKeyword()));
        entity.setConditionJson(normalizeOptional(dto.getConditionJson()));
        entity.setAssigneeId(dto.getAssigneeId());
        entity.setAssigneeSource(normalizeSource(dto.getAssigneeSource()));
        entity.setDutyScheduleId(dto.getDutyScheduleId());
        entity.setActionType(normalizeActionType(dto.getActionType()));
        entity.setActionConfig(normalizeOptional(dto.getActionConfig()));
        entity.setNotifyRoles(normalizeOptional(dto.getNotifyRoles()));
        entity.setNotifyChannels(normalizeOptional(dto.getNotifyChannels()));
        entity.setPriority(normalizePriority(dto.getPriority()));
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        } else if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        validateAction(entity);
    }

    private void validateAction(SysFeedbackDispatchRule entity) {
        String actionType = normalizeActionType(entity.getActionType());
        if ("notify".equals(actionType) && !StringUtils.hasText(entity.getNotifyRoles())) {
            throw new CustomException(400, "notify roles required for notify action");
        }
        if ("assign_notify".equals(actionType) && !StringUtils.hasText(entity.getNotifyRoles())) {
            throw new CustomException(400, "notify roles required for assign_notify action");
        }
    }

    private AdminFeedbackDispatchRuleVO toVO(SysFeedbackDispatchRule rule) {
        return AdminFeedbackDispatchRuleVO.builder()
                .id(rule.getId())
                .name(rule.getName())
                .feedbackType(rule.getFeedbackType())
                .keyword(rule.getKeyword())
                .conditionJson(rule.getConditionJson())
                .assigneeId(rule.getAssigneeId())
                .assigneeName(resolveAssigneeName(rule.getAssigneeId()))
                .assigneeSource(rule.getAssigneeSource())
                .dutyScheduleId(rule.getDutyScheduleId())
                .dutyScheduleName(resolveDutyScheduleName(rule.getDutyScheduleId()))
                .actionType(rule.getActionType())
                .actionConfig(rule.getActionConfig())
                .notifyRoles(rule.getNotifyRoles())
                .notifyChannels(rule.getNotifyChannels())
                .priority(rule.getPriority())
                .enabled(rule.getEnabled())
                .createTime(rule.getCreateTime())
                .updateTime(rule.getUpdateTime())
                .build();
    }

    private String resolveAssigneeName(Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectOneById(assigneeId);
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private String resolveDutyScheduleName(Long dutyScheduleId) {
        if (dutyScheduleId == null) {
            return null;
        }
        SysDutySchedule schedule = dutyScheduleMapper.selectOneById(dutyScheduleId);
        return schedule == null ? null : schedule.getName();
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

    private static String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return "fixed";
        }
        return source.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeActionType(String actionType) {
        if (!StringUtils.hasText(actionType)) {
            return "assign";
        }
        return actionType.trim().toLowerCase(Locale.ROOT);
    }

    private static int normalizePriority(Integer priority) {
        if (priority == null) {
            return 0;
        }
        return Math.max(-1000, Math.min(1000, priority));
    }

    private static Date resolveSimulateCreateTime(Integer offsetHours) {
        Calendar cal = Calendar.getInstance();
        if (offsetHours != null) {
            cal.add(Calendar.HOUR_OF_DAY, offsetHours);
        }
        return cal.getTime();
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
