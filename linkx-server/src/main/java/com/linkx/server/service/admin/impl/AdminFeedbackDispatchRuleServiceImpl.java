package com.linkx.server.service.admin.impl;

import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminFeedbackDispatchRuleDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminFeedbackDispatchRuleVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysFeedbackDispatchRule;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysFeedbackDispatchRuleMapper;
import com.linkx.server.service.admin.AdminFeedbackDispatchRuleService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminFeedbackDispatchRuleServiceImpl implements AdminFeedbackDispatchRuleService {

    private final SysFeedbackDispatchRuleMapper ruleMapper;
    private final SysUserMapper sysUserMapper;

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
        requireAssignee(dto.getAssigneeId());
        Date now = new Date();
        SysFeedbackDispatchRule entity = SysFeedbackDispatchRule.builder()
                .name(normalizeName(dto.getName()))
                .feedbackType(normalizeOptional(dto.getFeedbackType()))
                .keyword(normalizeOptional(dto.getKeyword()))
                .assigneeId(dto.getAssigneeId())
                .priority(normalizePriority(dto.getPriority()))
                .enabled(dto.getEnabled() == null || Boolean.TRUE.equals(dto.getEnabled()))
                .createdBy(operatorId)
                .updatedBy(operatorId)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        ruleMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public AdminFeedbackDispatchRuleVO update(Long id, AdminFeedbackDispatchRuleDTO dto, Long operatorId) {
        SysFeedbackDispatchRule entity = requireRule(id);
        requireAssignee(dto.getAssigneeId());
        entity.setName(normalizeName(dto.getName()));
        entity.setFeedbackType(normalizeOptional(dto.getFeedbackType()));
        entity.setKeyword(normalizeOptional(dto.getKeyword()));
        entity.setAssigneeId(dto.getAssigneeId());
        entity.setPriority(normalizePriority(dto.getPriority()));
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
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

    private SysFeedbackDispatchRule requireRule(Long id) {
        SysFeedbackDispatchRule rule = ruleMapper.selectOneById(id);
        if (rule == null || Integer.valueOf(1).equals(rule.getDeleted())) {
            throw new CustomException(404, "dispatch rule not found");
        }
        return rule;
    }

    private void requireAssignee(Long assigneeId) {
        if (assigneeId == null) {
            throw new CustomException(400, "assignee required");
        }
        SysUser user = sysUserMapper.selectOneById(assigneeId);
        if (user == null) {
            throw new CustomException(400, "assignee not found");
        }
    }

    private AdminFeedbackDispatchRuleVO toVO(SysFeedbackDispatchRule rule) {
        return AdminFeedbackDispatchRuleVO.builder()
                .id(rule.getId())
                .name(rule.getName())
                .feedbackType(rule.getFeedbackType())
                .keyword(rule.getKeyword())
                .assigneeId(rule.getAssigneeId())
                .assigneeName(resolveAssigneeName(rule.getAssigneeId()))
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
