package com.linkx.server.service.admin.impl;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.DataScope;
import com.linkx.server.common.DataScopeContext;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminDeviceQueryDTO;
import com.linkx.server.controller.admin.vo.AdminDeviceVO;
import com.linkx.server.entity.DeviceSession;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.DeviceSessionMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.DeviceSessionService;
import com.linkx.server.service.PresenceService;
import com.linkx.server.service.admin.AdminDeviceService;
import com.linkx.server.service.admin.AdminEventPublisher;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDeviceServiceImpl implements AdminDeviceService {

    private final DeviceSessionMapper deviceSessionMapper;
    private final SysUserMapper sysUserMapper;
    private final DeviceSessionService deviceSessionService;
    private final PresenceService presenceService;
    private final AdminEventPublisher adminEventPublisher;

    @Override
    @DataScope
    public PageResultVO<AdminDeviceVO> list(AdminDeviceQueryDTO query) {
        int page = normalizePage(query == null ? null : query.getPage());
        int size = normalizeSize(query == null ? null : query.getSize());
        QueryWrapper qw = buildQuery(query);
        qw.orderBy(DeviceSession::getLastActive, false);
        long total = deviceSessionMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<DeviceSession> rows = deviceSessionMapper.selectListByQuery(qw);
        Map<Long, SysUser> users = resolveUsers(rows);
        Map<Long, Set<String>> onlineByUser = resolveOnlineDevices(rows);
        List<AdminDeviceVO> items = rows.stream()
                .map(row -> toVO(row, users.get(row.getUserId()), onlineByUser))
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    @Transactional
    public void kick(Long userId, String deviceId, Long operatorId, String operatorUsername, String ip, String userAgent) {
        if (userId == null) {
            throw new CustomException(400, "用户ID不能为空");
        }
        if (!StringUtils.hasText(deviceId)) {
            throw new CustomException(400, "设备ID不能为空");
        }
        String normalized = deviceId.trim();
        DeviceSession session = deviceSessionMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(DeviceSession::getUserId).eq(userId)
                        .and(DeviceSession::getDeviceId).eq(normalized)
                        .limit(1)
        );
        if (session == null) {
            throw new CustomException(404, "设备会话不存在或已下线");
        }
        String operator = StringUtils.hasText(operatorUsername)
                ? operatorUsername.trim()
                : resolveOperatorName(operatorId);
        deviceSessionService.kickDevice(
                userId,
                normalized,
                operatorId,
                operator,
                ip,
                userAgent);
        // 管理端列表即时收敛；跨实例 WS 依赖 token 吊销 + device:kicked
        try {
            adminEventPublisher.publish(
                    "device_presence",
                    userId,
                    "{\"deviceId\":\"" + normalized.replace("\\", "\\\\").replace("\"", "\\\"")
                            + "\",\"online\":false}"
            );
        } catch (Exception ignored) {
            // 实时事件失败不影响踢下线
        }
    }

    private String resolveOperatorName(Long operatorId) {
        if (operatorId == null) {
            return "admin";
        }
        SysUser operator = sysUserMapper.selectOneById(operatorId);
        if (operator != null && StringUtils.hasText(operator.getUsername())) {
            return operator.getUsername();
        }
        return String.valueOf(operatorId);
    }

    private QueryWrapper buildQuery(AdminDeviceQueryDTO query) {
        QueryWrapper qw = QueryWrapper.create();
        Long scopeUserId = DataScopeContext.getUserId();
        if (scopeUserId != null) {
            qw.and(DeviceSession::getUserId).eq(scopeUserId);
        }
        if (query == null) {
            return qw;
        }
        if (query.getUserId() != null) {
            if (scopeUserId != null && !scopeUserId.equals(query.getUserId())) {
                // 无范围时强制空结果，避免越权窥探他人设备
                qw.and(DeviceSession::getUserId).eq(-1L);
            } else {
                qw.and(DeviceSession::getUserId).eq(query.getUserId());
            }
        }
        if (StringUtils.hasText(query.getDeviceType())) {
            qw.and(DeviceSession::getDeviceType).eq(query.getDeviceType().trim());
        }
        if (query.getStartTime() != null) {
            qw.and(DeviceSession::getLastActive).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(DeviceSession::getLastActive).le(new Date(query.getEndTime()));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            Set<Long> matchedUserIds = findUserIdsByKeyword(kw);
            qw.and((QueryWrapper w) -> {
                w.where(DeviceSession::getDeviceId).like(kw)
                        .or(DeviceSession::getDeviceName).like(kw)
                        .or(DeviceSession::getIp).like(kw);
                if (!matchedUserIds.isEmpty()) {
                    w.or(DeviceSession::getUserId).in(matchedUserIds);
                }
            });
        }
        return qw;
    }

    private Set<Long> findUserIdsByKeyword(String keyword) {
        List<SysUser> users = sysUserMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysUser::getUsername).like(keyword)
                        .or(SysUser::getNickname).like(keyword)
                        .limit(200)
        );
        if (users == null || users.isEmpty()) {
            return Collections.emptySet();
        }
        return users.stream().map(SysUser::getId).collect(Collectors.toCollection(HashSet::new));
    }

    private Map<Long, SysUser> resolveUsers(List<DeviceSession> rows) {
        Set<Long> ids = new HashSet<>();
        for (DeviceSession row : rows) {
            if (row.getUserId() != null) {
                ids.add(row.getUserId());
            }
        }
        Map<Long, SysUser> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        List<SysUser> users = sysUserMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(ids));
        for (SysUser user : users) {
            map.put(user.getId(), user);
        }
        return map;
    }

    private Map<Long, Set<String>> resolveOnlineDevices(List<DeviceSession> rows) {
        Map<Long, Set<String>> map = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (DeviceSession row : rows) {
            Long userId = row.getUserId();
            if (userId == null || !seen.add(userId)) {
                continue;
            }
            map.put(userId, presenceService.onlineDeviceIds(userId));
        }
        return map;
    }

    private AdminDeviceVO toVO(DeviceSession row, SysUser user, Map<Long, Set<String>> onlineByUser) {
        Set<String> onlineDevices = onlineByUser.getOrDefault(row.getUserId(), Collections.emptySet());
        boolean online = row.getDeviceId() != null && onlineDevices.contains(row.getDeviceId());
        return AdminDeviceVO.builder()
                .id(row.getId())
                .userId(row.getUserId())
                .username(user == null ? null : user.getUsername())
                .nickname(user == null ? null : user.getNickname())
                .deviceId(row.getDeviceId())
                .deviceName(row.getDeviceName())
                .deviceType(row.getDeviceType())
                .ip(ClientIpResolver.normalizeToIpv4(row.getIp()))
                .userAgent(row.getUserAgent())
                .lastActive(row.getLastActive())
                .createTime(row.getCreateTime())
                .online(online)
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
