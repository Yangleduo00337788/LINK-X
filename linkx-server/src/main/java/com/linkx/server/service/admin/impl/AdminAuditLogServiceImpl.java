package com.linkx.server.service.admin.impl;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.DataScope;
import com.linkx.server.common.DataScopeContext;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminLoginLogVO;
import com.linkx.server.controller.admin.vo.AdminOperationLogVO;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.entity.SysLoginAudit;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysAuditLogMapper;
import com.linkx.server.mapper.SysLoginAuditMapper;
import com.linkx.server.service.admin.AdminAuditLogService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private final SysAuditLogMapper sysAuditLogMapper;
    private final SysLoginAuditMapper sysLoginAuditMapper;

    @Override
    @DataScope
    public PageResultVO<AdminOperationLogVO> listAuditLogs(AdminPageQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = buildAuditQuery(query);
        qw.orderBy(SysAuditLog::getCreateTime, false);
        long total = sysAuditLogMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminOperationLogVO> items = sysAuditLogMapper.selectListByQuery(qw).stream()
                .map(this::toAuditVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    @DataScope
    public List<AdminOperationLogVO> listAuditLogsForExport(AdminPageQueryDTO query) {
        QueryWrapper qw = buildAuditQuery(query);
        qw.orderBy(SysAuditLog::getCreateTime, false);
        qw.limit(0, AdminConstants.EXPORT_MAX_SIZE);
        return sysAuditLogMapper.selectListByQuery(qw).stream()
                .map(this::toAuditVO)
                .collect(Collectors.toList());
    }

    @Override
    @DataScope
    public AdminOperationLogVO auditDetail(Long id) {
        SysAuditLog log = sysAuditLogMapper.selectOneById(id);
        if (log == null || !inScope(log.getUserId())) {
            throw new CustomException(404, "audit log not found");
        }
        return toAuditVO(log);
    }

    @Override
    @DataScope
    public PageResultVO<AdminLoginLogVO> listLoginLogs(AdminPageQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = buildLoginQuery(query);
        qw.orderBy(SysLoginAudit::getCreateTime, false);
        long total = sysLoginAuditMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminLoginLogVO> items = sysLoginAuditMapper.selectListByQuery(qw).stream()
                .map(this::toLoginVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    @DataScope
    public List<AdminLoginLogVO> listLoginLogsForExport(AdminPageQueryDTO query) {
        QueryWrapper qw = buildLoginQuery(query);
        qw.orderBy(SysLoginAudit::getCreateTime, false);
        qw.limit(0, AdminConstants.EXPORT_MAX_SIZE);
        return sysLoginAuditMapper.selectListByQuery(qw).stream()
                .map(this::toLoginVO)
                .collect(Collectors.toList());
    }

    @Override
    @DataScope
    public AdminLoginLogVO loginDetail(Long id) {
        SysLoginAudit log = sysLoginAuditMapper.selectOneById(id);
        if (log == null || !inScope(log.getUserId())) {
            throw new CustomException(404, "login log not found");
        }
        return toLoginVO(log);
    }

    private QueryWrapper buildAuditQuery(AdminPageQueryDTO query) {
        QueryWrapper qw = QueryWrapper.create();
        applyOperatorScope(qw);
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysAuditLog::getUsername).like(kw)
                        .or(SysAuditLog::getDescription).like(kw)
                        .or(SysAuditLog::getOperationType).like(kw);
            });
        }
        if (query.getStartTime() != null) {
            qw.and(SysAuditLog::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(SysAuditLog::getCreateTime).le(new Date(query.getEndTime()));
        }
        return qw;
    }

    private QueryWrapper buildLoginQuery(AdminPageQueryDTO query) {
        QueryWrapper qw = QueryWrapper.create();
        applyLoginUserScope(qw);
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysLoginAudit::getUsername).like(kw)
                        .or(SysLoginAudit::getIp).like(kw)
                        .or(SysLoginAudit::getReason).like(kw);
            });
        }
        if (query.getStatus() != null) {
            qw.and(SysLoginAudit::getSuccess).eq(query.getStatus());
        }
        if (query.getStartTime() != null) {
            qw.and(SysLoginAudit::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(SysLoginAudit::getCreateTime).le(new Date(query.getEndTime()));
        }
        return qw;
    }

    private void applyOperatorScope(QueryWrapper qw) {
        var allowed = DataScopeContext.getAllowedUserIds();
        if (allowed == null) {
            return;
        }
        if (allowed.isEmpty()) {
            qw.and(SysAuditLog::getUserId).eq(-1L);
            return;
        }
        qw.and(SysAuditLog::getUserId).in(allowed);
    }

    private void applyLoginUserScope(QueryWrapper qw) {
        var allowed = DataScopeContext.getAllowedUserIds();
        if (allowed == null) {
            return;
        }
        if (allowed.isEmpty()) {
            qw.and(SysLoginAudit::getUserId).eq(-1L);
            return;
        }
        qw.and(SysLoginAudit::getUserId).in(allowed);
    }

    private boolean inScope(Long rowUserId) {
        return DataScopeContext.allows(rowUserId);
    }

    private AdminOperationLogVO toAuditVO(SysAuditLog log) {
        return AdminOperationLogVO.builder()
                .id(log.getId())
                .operationType(log.getOperationType())
                .description(log.getDescription())
                .userId(log.getUserId())
                .username(log.getUsername())
                .targetUserId(log.getTargetUserId())
                .targetUsername(log.getTargetUsername())
                .targetResourceId(log.getTargetResourceId())
                .targetResourceType(log.getTargetResourceType())
                .ip(ClientIpResolver.normalizeToIpv4(log.getIp()))
                .userAgent(log.getUserAgent())
                .status(log.getStatus())
                .failureReason(log.getFailureReason())
                .extraData(log.getExtraData())
                .createTime(log.getCreateTime())
                .build();
    }

    private AdminLoginLogVO toLoginVO(SysLoginAudit log) {
        return AdminLoginLogVO.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .username(log.getUsername())
                .ip(ClientIpResolver.normalizeToIpv4(log.getIp()))
                .userAgent(log.getUserAgent())
                .success(log.getSuccess())
                .reason(log.getReason())
                .createTime(log.getCreateTime())
                .build();
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
