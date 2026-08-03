package com.linkx.server.service.admin.impl;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.DataScope;
import com.linkx.server.common.DataScopeContext;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminAuditLogQueryDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminLoginLogVO;
import com.linkx.server.controller.admin.vo.AdminOperationLogVO;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.entity.SysLoginAudit;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysAuditLogMapper;
import com.linkx.server.mapper.SysLoginAuditMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.IpGeoService;
import com.linkx.server.service.admin.AdminAuditLogService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private final SysAuditLogMapper sysAuditLogMapper;
    private final SysLoginAuditMapper sysLoginAuditMapper;
    private final IpGeoService ipGeoService;
    private final SysUserMapper sysUserMapper;

    @Override
    @DataScope
    public PageResultVO<AdminOperationLogVO> listAuditLogs(AdminAuditLogQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = buildAuditQuery(query);
        long total = sysAuditLogMapper.selectCountByQuery(qw);
        qw.orderBy(SysAuditLog::getCreateTime, false);
        qw.limit((page - 1L) * size, size);
        List<AdminOperationLogVO> items = sysAuditLogMapper.selectListByQuery(qw).stream()
                .map(this::toAuditVO)
                .collect(Collectors.toList());
        enrichUsernames(items);
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    @DataScope
    public List<AdminOperationLogVO> listAuditLogsForExport(AdminAuditLogQueryDTO query) {
        QueryWrapper qw = buildAuditQuery(query);
        qw.orderBy(SysAuditLog::getCreateTime, false);
        qw.limit(0, AdminConstants.EXPORT_MAX_SIZE);
        List<AdminOperationLogVO> items = sysAuditLogMapper.selectListByQuery(qw).stream()
                .map(this::toAuditVO)
                .collect(Collectors.toList());
        enrichUsernames(items);
        return items;
    }

    @Override
    @DataScope
    public AdminOperationLogVO auditDetail(Long id) {
        SysAuditLog log = sysAuditLogMapper.selectOneById(id);
        if (log == null || !inScope(log.getUserId())) {
            throw new CustomException(404, "audit log not found");
        }
        return enrichUsername(toAuditVO(log));
    }

    @Override
    @DataScope
    public PageResultVO<AdminLoginLogVO> listLoginLogs(AdminPageQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = buildLoginQuery(query);
        long total = sysLoginAuditMapper.selectCountByQuery(qw);
        qw.orderBy(SysLoginAudit::getCreateTime, false);
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

    private QueryWrapper buildAuditQuery(AdminAuditLogQueryDTO query) {
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
        if (StringUtils.hasText(query.getOperationType())) {
            qw.and(SysAuditLog::getOperationType).eq(query.getOperationType().trim());
        }
        if (StringUtils.hasText(query.getResultStatus())) {
            qw.and(SysAuditLog::getStatus).eq(query.getResultStatus().trim());
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

    private AdminOperationLogVO enrichUsername(AdminOperationLogVO vo) {
        enrichUsernames(List.of(vo));
        return vo;
    }

    private void enrichUsernames(List<AdminOperationLogVO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<Long> ids = new HashSet<>();
        for (AdminOperationLogVO vo : items) {
            if (!StringUtils.hasText(vo.getUsername()) && vo.getUserId() != null) {
                ids.add(vo.getUserId());
            }
            if (!StringUtils.hasText(vo.getTargetUsername()) && vo.getTargetUserId() != null) {
                ids.add(vo.getTargetUserId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> names = sysUserMapper.selectListByIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (a, b) -> a));
        for (AdminOperationLogVO vo : items) {
            if (!StringUtils.hasText(vo.getUsername()) && vo.getUserId() != null) {
                vo.setUsername(names.get(vo.getUserId()));
            }
            if (!StringUtils.hasText(vo.getTargetUsername()) && vo.getTargetUserId() != null) {
                vo.setTargetUsername(names.get(vo.getTargetUserId()));
            }
        }
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
        String ip = ClientIpResolver.normalizeToIpv4(log.getIp());
        return AdminLoginLogVO.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .username(log.getUsername())
                .ip(ip)
                .region(ipGeoService.resolve(ip))
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
