package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminBlacklistAddDTO;
import com.linkx.server.controller.admin.dto.AdminBlacklistQueryDTO;
import com.linkx.server.controller.admin.dto.AdminBlacklistReleaseDTO;
import com.linkx.server.controller.admin.dto.AdminUserActionDTO;
import com.linkx.server.controller.admin.vo.AdminBlacklistVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysAdminBlacklist;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysAdminBlacklistMapper;
import com.linkx.server.service.admin.AdminBlacklistService;
import com.linkx.server.service.admin.AdminUserService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminBlacklistServiceImpl implements AdminBlacklistService {

    private final SysAdminBlacklistMapper blacklistMapper;
    private final SysUserMapper sysUserMapper;
    private final AdminUserService adminUserService;

    public AdminBlacklistServiceImpl(SysAdminBlacklistMapper blacklistMapper,
                                     SysUserMapper sysUserMapper,
                                     @Lazy AdminUserService adminUserService) {
        this.blacklistMapper = blacklistMapper;
        this.sysUserMapper = sysUserMapper;
        this.adminUserService = adminUserService;
    }

    @Override
    public PageResultVO<AdminBlacklistVO> list(AdminBlacklistQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = buildQuery(query);
        qw.orderBy(SysAdminBlacklist::getCreateTime, false);
        long total = blacklistMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<SysAdminBlacklist> rows = blacklistMapper.selectListByQuery(qw);
        Map<Long, String> operatorNames = resolveOperatorNames(rows);
        List<AdminBlacklistVO> items = rows.stream()
                .map(row -> toVO(row, operatorNames))
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public List<AdminBlacklistVO> listForExport(AdminBlacklistQueryDTO query) {
        QueryWrapper qw = buildQuery(query);
        qw.orderBy(SysAdminBlacklist::getCreateTime, false);
        qw.limit(0, AdminConstants.EXPORT_MAX_SIZE);
        List<SysAdminBlacklist> rows = blacklistMapper.selectListByQuery(qw);
        Map<Long, String> operatorNames = resolveOperatorNames(rows);
        return rows.stream()
                .map(row -> toVO(row, operatorNames))
                .collect(Collectors.toList());
    }

    @Override
    public AdminBlacklistVO detail(Long id) {
        SysAdminBlacklist row = requireEntry(id);
        Map<Long, String> operatorNames = resolveOperatorNames(List.of(row));
        return toVO(row, operatorNames);
    }

    @Override
    @Transactional
    public void add(AdminBlacklistAddDTO dto, Long operatorId) {
        if (dto == null || dto.getUserId() == null) {
            throw new CustomException(400, "用户ID不能为空");
        }
        AdminUserActionDTO action = new AdminUserActionDTO();
        action.setReason(dto.getReason());
        adminUserService.ban(dto.getUserId(), action, operatorId);
    }

    @Override
    @Transactional
    public void release(Long id, AdminBlacklistReleaseDTO dto, Long operatorId) {
        SysAdminBlacklist entry = requireEntry(id);
        if (!SysAdminBlacklist.STATUS_ACTIVE.equals(entry.getStatus())) {
            throw new CustomException(400, "该黑名单记录已解除");
        }
        String reason = dto == null ? null : dto.getReason();
        adminUserService.unban(entry.getUserId(), operatorId);
        // unban 会按 userId 释放 active 记录；若记录状态未变则补写解封原因
        SysAdminBlacklist latest = blacklistMapper.selectOneById(id);
        if (latest != null && SysAdminBlacklist.STATUS_RELEASED.equals(latest.getStatus())
                && StringUtils.hasText(reason) && !StringUtils.hasText(latest.getReleaseReason())) {
            latest.setReleaseReason(reason.trim());
            latest.setUpdateTime(new Date());
            blacklistMapper.update(latest);
        } else if (latest != null && SysAdminBlacklist.STATUS_ACTIVE.equals(latest.getStatus())) {
            releaseRecord(latest, reason, operatorId);
        }
    }

    @Override
    @Transactional
    public void recordBan(Long userId, String reason, Long operatorId) {
        if (userId == null) {
            return;
        }
        SysUser user = sysUserMapper.selectOneById(userId);
        String username = user == null ? null : user.getUsername();
        String nickname = user == null ? null : user.getNickname();
        Date now = new Date();
        SysAdminBlacklist active = findActiveByUserId(userId);
        if (active != null) {
            if (StringUtils.hasText(reason)) {
                active.setReason(reason.trim());
            }
            active.setUsername(username);
            active.setNickname(nickname);
            active.setCreatedBy(operatorId);
            active.setUpdateTime(now);
            blacklistMapper.update(active);
            return;
        }
        blacklistMapper.insert(SysAdminBlacklist.builder()
                .userId(userId)
                .username(username)
                .nickname(nickname)
                .reason(StringUtils.hasText(reason) ? reason.trim() : null)
                .status(SysAdminBlacklist.STATUS_ACTIVE)
                .createdBy(operatorId)
                .createTime(now)
                .updateTime(now)
                .build());
    }

    @Override
    @Transactional
    public void releaseByUserId(Long userId, String releaseReason, Long operatorId) {
        if (userId == null) {
            return;
        }
        SysAdminBlacklist active = findActiveByUserId(userId);
        if (active == null) {
            return;
        }
        releaseRecord(active, releaseReason, operatorId);
    }

    @Override
    public boolean hasActiveBan(Long userId) {
        return userId != null && findActiveByUserId(userId) != null;
    }

    private void releaseRecord(SysAdminBlacklist entry, String releaseReason, Long operatorId) {
        Date now = new Date();
        entry.setStatus(SysAdminBlacklist.STATUS_RELEASED);
        entry.setReleasedBy(operatorId);
        entry.setReleasedAt(now);
        entry.setReleaseReason(StringUtils.hasText(releaseReason) ? releaseReason.trim() : null);
        entry.setUpdateTime(now);
        blacklistMapper.update(entry);
    }

    private SysAdminBlacklist findActiveByUserId(Long userId) {
        return blacklistMapper.selectOneByQuery(QueryWrapper.create()
                .where(SysAdminBlacklist::getUserId).eq(userId)
                .and(SysAdminBlacklist::getStatus).eq(SysAdminBlacklist.STATUS_ACTIVE)
                .orderBy(SysAdminBlacklist::getCreateTime, false)
                .limit(1));
    }

    private QueryWrapper buildQuery(AdminBlacklistQueryDTO query) {
        QueryWrapper qw = QueryWrapper.create();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysAdminBlacklist::getUsername).like(kw)
                        .or(SysAdminBlacklist::getNickname).like(kw)
                        .or(SysAdminBlacklist::getReason).like(kw);
            });
        }
        if (StringUtils.hasText(query.getEntryStatus())) {
            qw.and(SysAdminBlacklist::getStatus).eq(query.getEntryStatus().trim());
        }
        if (query.getStartTime() != null) {
            qw.and(SysAdminBlacklist::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(SysAdminBlacklist::getCreateTime).le(new Date(query.getEndTime()));
        }
        return qw;
    }

    private SysAdminBlacklist requireEntry(Long id) {
        if (id == null) {
            throw new CustomException(400, "黑名单ID无效");
        }
        SysAdminBlacklist entry = blacklistMapper.selectOneById(id);
        if (entry == null) {
            throw new CustomException(404, "黑名单记录不存在");
        }
        return entry;
    }

    private Map<Long, String> resolveOperatorNames(List<SysAdminBlacklist> rows) {
        Set<Long> ids = new HashSet<>();
        for (SysAdminBlacklist row : rows) {
            if (row.getCreatedBy() != null) {
                ids.add(row.getCreatedBy());
            }
            if (row.getReleasedBy() != null) {
                ids.add(row.getReleasedBy());
            }
        }
        Map<Long, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        List<SysUser> users = sysUserMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(ids));
        for (SysUser user : users) {
            map.put(user.getId(), user.getUsername());
        }
        return map;
    }

    private AdminBlacklistVO toVO(SysAdminBlacklist row, Map<Long, String> operatorNames) {
        return AdminBlacklistVO.builder()
                .id(row.getId())
                .userId(row.getUserId())
                .username(row.getUsername())
                .nickname(row.getNickname())
                .reason(row.getReason())
                .status(row.getStatus())
                .createdBy(row.getCreatedBy())
                .createdByName(operatorNames.get(row.getCreatedBy()))
                .releasedBy(row.getReleasedBy())
                .releasedByName(operatorNames.get(row.getReleasedBy()))
                .releasedAt(row.getReleasedAt())
                .releaseReason(row.getReleaseReason())
                .createTime(row.getCreateTime())
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
