package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.entity.admin.SysApprovalRecord;
import com.linkx.server.entity.admin.SysApprovalTempGrant;
import com.linkx.server.mapper.admin.SysApprovalRecordMapper;
import com.linkx.server.mapper.admin.SysApprovalTempGrantMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.ApprovalTempGrantService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalTempGrantServiceImpl implements ApprovalTempGrantService {

    private final SysApprovalTempGrantMapper tempGrantMapper;
    private final SysApprovalRecordMapper recordMapper;
    private final RbacService rbacService;

    @Lazy
    @Autowired
    private ApprovalTempGrantService self;

    @Override
    @Transactional
    public void grantForRecord(Long recordId, Long userId) {
        if (recordId == null || userId == null) {
            return;
        }
        if (hasAllApprovalPermissions(userId)) {
            return;
        }
        Date now = new Date();
        boolean granted = false;
        for (String perm : APPROVAL_TEMP_PERMISSIONS) {
            if (rbacService.hasPermission(userId, perm)) {
                continue;
            }
            long exists = tempGrantMapper.selectCountByQuery(
                    QueryWrapper.create()
                            .where(SysApprovalTempGrant::getRecordId).eq(recordId)
                            .and(SysApprovalTempGrant::getUserId).eq(userId)
                            .and(SysApprovalTempGrant::getPermissionCode).eq(perm)
                            .and(SysApprovalTempGrant::getRevokedAt).isNull());
            if (exists > 0) {
                continue;
            }
            tempGrantMapper.insert(SysApprovalTempGrant.builder()
                    .recordId(recordId)
                    .userId(userId)
                    .permissionCode(perm)
                    .grantedAt(now)
                    .build());
            granted = true;
        }
        if (granted) {
            rbacService.evictUserCache(userId);
            log.info("审批临时授权: recordId={}, userId={}", recordId, userId);
        }
    }

    @Override
    @Transactional
    public void revokeForRecord(Long recordId) {
        if (recordId == null) {
            return;
        }
        List<SysApprovalTempGrant> active = tempGrantMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysApprovalTempGrant::getRecordId).eq(recordId)
                        .and(SysApprovalTempGrant::getRevokedAt).isNull());
        if (active.isEmpty()) {
            return;
        }
        Date now = new Date();
        Set<Long> userIds = new LinkedHashSet<>();
        for (SysApprovalTempGrant grant : active) {
            grant.setRevokedAt(now);
            tempGrantMapper.update(grant);
            if (grant.getUserId() != null) {
                userIds.add(grant.getUserId());
            }
        }
        userIds.forEach(rbacService::evictUserCache);
        log.info("审批临时授权已撤销: recordId={}, users={}", recordId, userIds);
    }

    @Override
    @Transactional
    public void revokeForInstance(Long instanceId) {
        if (instanceId == null) {
            return;
        }
        List<SysApprovalRecord> records = recordMapper.selectListByQuery(
                QueryWrapper.create().where(SysApprovalRecord::getInstanceId).eq(instanceId));
        for (SysApprovalRecord record : records) {
            revokeForRecord(record.getId());
        }
    }

    @Override
    public List<String> activePermissionCodes(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return tempGrantMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(SysApprovalTempGrant::getUserId).eq(userId)
                                .and(SysApprovalTempGrant::getRevokedAt).isNull())
                .stream()
                .map(SysApprovalTempGrant::getPermissionCode)
                .distinct()
                .toList();
    }

    @Override
    public boolean wasGrantedForRecord(Long recordId, Long userId) {
        if (recordId == null || userId == null) {
            return false;
        }
        return tempGrantMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysApprovalTempGrant::getRecordId).eq(recordId)
                        .and(SysApprovalTempGrant::getUserId).eq(userId)) > 0;
    }

    @Override
    @Transactional
    public void syncGrantsForUser(Long userId) {
        if (userId == null || hasAllApprovalPermissions(userId)) {
            return;
        }
        List<SysApprovalRecord> pending = recordMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysApprovalRecord::getAssigneeId).eq(userId)
                        .and(SysApprovalRecord::getStatus).eq(SysApprovalRecord.STATUS_PENDING)
                        .and(SysApprovalRecord::getNodeType).ne(SysApprovalRecord.NODE_CC));
        for (SysApprovalRecord record : pending) {
            self.grantForRecord(record.getId(), userId);
        }
    }

    private boolean hasAllApprovalPermissions(Long userId) {
        return rbacService.hasRole(userId, AdminConstants.ROLE_AUDIT_ADMIN)
                || rbacService.hasRole(userId, AdminConstants.ROLE_ADMIN)
                || rbacService.hasRole(userId, AdminConstants.ROLE_SUPER_ADMIN);
    }
}
