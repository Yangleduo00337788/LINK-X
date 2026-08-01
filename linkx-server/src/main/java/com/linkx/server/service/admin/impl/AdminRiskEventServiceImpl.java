package com.linkx.server.service.admin.impl;

import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminRiskEventBatchDTO;
import com.linkx.server.controller.admin.dto.AdminRiskEventHandleDTO;
import com.linkx.server.controller.admin.dto.AdminRiskEventQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserActionDTO;
import com.linkx.server.controller.admin.vo.AdminReviewBatchResultVO;
import com.linkx.server.controller.admin.vo.AdminRiskEventVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysRiskEvent;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysRiskEventMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.AdminEventPublisher;
import com.linkx.server.service.admin.AdminRiskEventService;
import com.linkx.server.service.admin.AdminUserService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRiskEventServiceImpl implements AdminRiskEventService {

    private final SysRiskEventMapper riskEventMapper;
    private final SysUserMapper sysUserMapper;
    private final AdminEventPublisher adminEventPublisher;
    private final AdminUserService adminUserService;
    private final RbacService rbacService;

    @Override
    public PageResultVO<AdminRiskEventVO> list(AdminRiskEventQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = buildQuery(query);
        qw.orderBy(SysRiskEvent::getCreateTime, false);
        long total = riskEventMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminRiskEventVO> items = riskEventMapper.selectListByQuery(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public List<AdminRiskEventVO> listForExport(AdminRiskEventQueryDTO query) {
        QueryWrapper qw = buildQuery(query);
        qw.orderBy(SysRiskEvent::getCreateTime, false);
        qw.limit(0, AdminConstants.EXPORT_MAX_SIZE);
        return riskEventMapper.selectListByQuery(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public AdminRiskEventVO detail(Long id) {
        return toVO(requireEvent(id));
    }

    @Override
    @Transactional
    public void handle(Long id, AdminRiskEventHandleDTO dto, Long operatorId) {
        SysRiskEvent event = requireEvent(id);
        if (!SysRiskEvent.STATUS_PENDING.equals(event.getStatus())) {
            throw new CustomException(400, "该风险事件已处置");
        }
        String action = dto.getAction() == null ? "" : dto.getAction().trim().toLowerCase();
        String status;
        if ("handled".equals(action)) {
            status = SysRiskEvent.STATUS_HANDLED;
        } else if ("ignored".equals(action)) {
            status = SysRiskEvent.STATUS_IGNORED;
        } else {
            throw new CustomException(400, "处置动作无效，仅支持 handled / ignored");
        }

        String userAction = normalizeUserAction(dto.getUserAction());
        if (!SysRiskEvent.STATUS_HANDLED.equals(status) && !"none".equals(userAction)) {
            throw new CustomException(400, "忽略风险事件时不能连带处罚用户");
        }

        String appliedUserAction = applyUserAction(event, userAction, dto.getResolution(), operatorId);

        Date now = new Date();
        event.setStatus(status);
        event.setResolution(buildResolution(dto.getResolution(), appliedUserAction));
        event.setHandledBy(operatorId);
        event.setHandledAt(now);
        event.setUpdateTime(now);
        riskEventMapper.update(event);
        adminEventPublisher.publish("risk_handled", event.getId());
    }

    @Override
    public AdminReviewBatchResultVO batch(AdminRiskEventBatchDTO dto, Long operatorId) {
        String action = dto.getAction() == null ? "" : dto.getAction().trim().toLowerCase();
        if (!"handled".equals(action) && !"ignored".equals(action)) {
            throw new CustomException(400, "处置动作无效，仅支持 handled / ignored");
        }
        AdminRiskEventHandleDTO handleDto = new AdminRiskEventHandleDTO();
        handleDto.setAction(action);
        handleDto.setResolution(dto.getResolution());
        handleDto.setUserAction("none");

        int success = 0;
        List<AdminReviewBatchResultVO.FailureItem> failures = new ArrayList<>();
        for (Long id : dto.getIds()) {
            if (id == null) {
                continue;
            }
            try {
                handle(id, handleDto, operatorId);
                success++;
            } catch (CustomException ex) {
                failures.add(AdminReviewBatchResultVO.FailureItem.builder()
                        .id(id)
                        .reason(ex.getMessage())
                        .build());
            } catch (Exception ex) {
                failures.add(AdminReviewBatchResultVO.FailureItem.builder()
                        .id(id)
                        .reason(ex.getMessage() != null ? ex.getMessage() : "unknown error")
                        .build());
            }
        }
        return AdminReviewBatchResultVO.builder()
                .successCount(success)
                .failCount(failures.size())
                .failures(failures)
                .build();
    }

    private String normalizeUserAction(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "none";
        }
        String action = raw.trim().toLowerCase();
        if ("none".equals(action) || "freeze".equals(action) || "ban".equals(action)) {
            return action;
        }
        throw new CustomException(400, "用户处置动作无效，仅支持 none / freeze / ban");
    }

    private String applyUserAction(SysRiskEvent event, String userAction, String resolution, Long operatorId) {
        if ("none".equals(userAction)) {
            return "none";
        }
        if (event.getUserId() == null) {
            throw new CustomException(400, "该风险事件没有关联用户，无法处罚");
        }
        String reason = StringUtils.hasText(resolution)
                ? resolution.trim()
                : ("风险事件处置: " + (event.getTitle() == null ? event.getEventType() : event.getTitle()));
        AdminUserActionDTO actionDTO = new AdminUserActionDTO();
        actionDTO.setReason(reason.length() > 255 ? reason.substring(0, 255) : reason);

        if ("freeze".equals(userAction)) {
            if (!rbacService.hasPermission(operatorId, "admin:user:freeze")) {
                throw new CustomException(403, "无冻结用户权限");
            }
            adminUserService.freeze(event.getUserId(), actionDTO, operatorId);
            return "freeze";
        }
        if (!rbacService.hasPermission(operatorId, "admin:user:ban")) {
            throw new CustomException(403, "无封禁用户权限");
        }
        adminUserService.ban(event.getUserId(), actionDTO, operatorId);
        return "ban";
    }

    private String buildResolution(String resolution, String appliedUserAction) {
        String base = StringUtils.hasText(resolution) ? resolution.trim() : "";
        String suffix;
        if ("freeze".equals(appliedUserAction)) {
            suffix = "[同时冻结用户]";
        } else if ("ban".equals(appliedUserAction)) {
            suffix = "[同时封禁用户]";
        } else {
            return StringUtils.hasText(base) ? base : null;
        }
        if (!StringUtils.hasText(base)) {
            return suffix;
        }
        String merged = base + " " + suffix;
        return merged.length() > 1000 ? merged.substring(0, 1000) : merged;
    }

    @Override
    public long countPending() {
        return riskEventMapper.selectCountByQuery(
                QueryWrapper.create().where(SysRiskEvent::getStatus).eq(SysRiskEvent.STATUS_PENDING));
    }

    @Override
    public long countSince(Date since) {
        QueryWrapper qw = QueryWrapper.create();
        if (since != null) {
            qw.where(SysRiskEvent::getCreateTime).ge(since);
        }
        return riskEventMapper.selectCountByQuery(qw);
    }

    @Override
    public long countSinceByType(String eventType, Date since) {
        QueryWrapper qw = QueryWrapper.create();
        if (StringUtils.hasText(eventType)) {
            qw.where(SysRiskEvent::getEventType).eq(eventType.trim());
        }
        if (since != null) {
            qw.and(SysRiskEvent::getCreateTime).ge(since);
        }
        return riskEventMapper.selectCountByQuery(qw);
    }

    @Override
    public void recordSensitiveMatch(Long userId, String matchedWords, String failReason, Long conversationId) {
        String words = matchedWords == null ? "" : matchedWords.trim();
        String level = "blocked".equals(failReason)
                ? SysRiskEvent.LEVEL_HIGH
                : ("alert".equals(failReason) ? SysRiskEvent.LEVEL_MEDIUM : SysRiskEvent.LEVEL_LOW);
        insertEvent(SysRiskEvent.builder()
                .eventType(SysRiskEvent.TYPE_SENSITIVE_WORD_MATCH)
                .title("敏感词命中")
                .detail(StringUtils.hasText(words) ? "敏感词命中: " + words : "敏感词命中")
                .riskLevel(level)
                .status(SysRiskEvent.STATUS_PENDING)
                .userId(userId)
                .username(resolveUsername(userId))
                .targetResourceId(conversationId == null ? null : String.valueOf(conversationId))
                .targetResourceType(conversationId == null ? null : "conversation")
                .extraData(failReason)
                .createTime(new Date())
                .updateTime(new Date())
                .build());
    }

    @Override
    public void recordMessageStorm(Long userId, String eventType, int messageCount, Long conversationId) {
        String typeLabel = eventType == null ? "unknown" : eventType;
        insertEvent(SysRiskEvent.builder()
                .eventType(SysRiskEvent.TYPE_MESSAGE_STORM)
                .title("消息风暴")
                .detail("消息风暴: " + typeLabel + " count=" + messageCount)
                .riskLevel(SysRiskEvent.LEVEL_HIGH)
                .status(SysRiskEvent.STATUS_PENDING)
                .userId(userId)
                .username(resolveUsername(userId))
                .targetResourceId(conversationId == null ? null : String.valueOf(conversationId))
                .targetResourceType(conversationId == null ? null : "conversation")
                .extraData(typeLabel)
                .createTime(new Date())
                .updateTime(new Date())
                .build());
    }

    @Override
    public void recordLoginLock(Long userId, String username, String ip, String side, int lockMinutes) {
        String sideLabel = StringUtils.hasText(side) ? side.trim().toLowerCase() : "client";
        String name = StringUtils.hasText(username) ? username.trim() : resolveUsername(userId);
        insertEvent(SysRiskEvent.builder()
                .eventType(SysRiskEvent.TYPE_LOGIN_LOCK)
                .title("登录暴力破解锁定")
                .detail("登录失败超限已锁定: user=" + (name == null ? "-" : name)
                        + ", side=" + sideLabel + ", lockMinutes=" + lockMinutes)
                .riskLevel(SysRiskEvent.LEVEL_HIGH)
                .status(SysRiskEvent.STATUS_PENDING)
                .userId(userId)
                .username(name)
                .ip(ip)
                .extraData(sideLabel)
                .targetResourceType("login")
                .targetResourceId(sideLabel)
                .createTime(new Date())
                .updateTime(new Date())
                .build());
    }

    @Override
    public void recordRateLimit(Long userId, String identity, String scope, String ip) {
        String scopeLabel = StringUtils.hasText(scope) ? scope.trim() : "unknown";
        String idLabel = StringUtils.hasText(identity) ? identity.trim() : "-";
        insertEvent(SysRiskEvent.builder()
                .eventType(SysRiskEvent.TYPE_RATE_LIMIT)
                .title("接口限流触发")
                .detail("限流触发: scope=" + scopeLabel + ", identity=" + idLabel)
                .riskLevel(SysRiskEvent.LEVEL_MEDIUM)
                .status(SysRiskEvent.STATUS_PENDING)
                .userId(userId)
                .username(resolveUsername(userId))
                .ip(ip)
                .extraData(scopeLabel)
                .targetResourceType("rate_limit")
                .targetResourceId(scopeLabel)
                .createTime(new Date())
                .updateTime(new Date())
                .build());
    }

    private void insertEvent(SysRiskEvent event) {
        try {
            riskEventMapper.insert(event);
            adminEventPublisher.publish("risk_created", event.getId());
        } catch (Exception e) {
            log.warn("风险事件落库失败: type={}, userId={}", event.getEventType(), event.getUserId(), e);
        }
    }

    private QueryWrapper buildQuery(AdminRiskEventQueryDTO query) {
        QueryWrapper qw = QueryWrapper.create();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysRiskEvent::getTitle).like(kw)
                        .or(SysRiskEvent::getDetail).like(kw)
                        .or(SysRiskEvent::getUsername).like(kw)
                        .or(SysRiskEvent::getEventType).like(kw);
            });
        }
        if (StringUtils.hasText(query.getEventStatus())) {
            qw.and(SysRiskEvent::getStatus).eq(query.getEventStatus().trim());
        }
        if (StringUtils.hasText(query.getEventType())) {
            qw.and(SysRiskEvent::getEventType).eq(query.getEventType().trim());
        }
        if (StringUtils.hasText(query.getRiskLevel())) {
            qw.and(SysRiskEvent::getRiskLevel).eq(query.getRiskLevel().trim());
        }
        if (query.getStartTime() != null) {
            qw.and(SysRiskEvent::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(SysRiskEvent::getCreateTime).le(new Date(query.getEndTime()));
        }
        return qw;
    }

    private SysRiskEvent requireEvent(Long id) {
        if (id == null) {
            throw new CustomException(400, "风险事件ID无效");
        }
        SysRiskEvent event = riskEventMapper.selectOneById(id);
        if (event == null) {
            throw new CustomException(404, "风险事件不存在");
        }
        return event;
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectOneById(userId);
        return user == null ? null : user.getUsername();
    }

    private AdminRiskEventVO toVO(SysRiskEvent event) {
        return AdminRiskEventVO.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .title(event.getTitle())
                .detail(event.getDetail())
                .riskLevel(event.getRiskLevel())
                .status(event.getStatus())
                .userId(event.getUserId())
                .username(event.getUsername())
                .targetResourceId(event.getTargetResourceId())
                .targetResourceType(event.getTargetResourceType())
                .ip(event.getIp())
                .extraData(event.getExtraData())
                .auditLogId(event.getAuditLogId())
                .resolution(event.getResolution())
                .handledBy(event.getHandledBy())
                .handledAt(event.getHandledAt())
                .createTime(event.getCreateTime())
                .build();
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return AdminConstants.DEFAULT_PAGE;
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return AdminConstants.DEFAULT_SIZE;
        }
        return Math.min(size, AdminConstants.MAX_SIZE);
    }
}
