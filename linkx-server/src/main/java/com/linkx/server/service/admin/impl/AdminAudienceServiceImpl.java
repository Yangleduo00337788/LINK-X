package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RbacConstants;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.AdminAudienceService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminAudienceServiceImpl implements AdminAudienceService {

    private final SysUserRoleMapper sysUserRoleMapper;
    private final RbacService rbacService;

    @Override
    public List<Long> reviewOperatorUserIds() {
        return userIdsWithPermission("admin:review:list");
    }

    @Override
    public List<Long> feedbackOperatorUserIds() {
        return userIdsWithPermission("admin:feedback:list");
    }

    @Override
    public List<Long> riskOperatorUserIds() {
        return userIdsWithPermission("admin:risk-event:list");
    }

    @Override
    public List<Long> userIdsWithPermission(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        List<SysUserRole> bindings = sysUserRoleMapper.selectListByQuery(QueryWrapper.create());
        for (SysUserRole binding : bindings) {
            Long userId = binding.getUserId();
            if (userId == null) {
                continue;
            }
            if (rbacService.hasPermission(userId, permissionCode)
                    || rbacService.hasPermission(userId, RbacConstants.PERM_ALL)) {
                ids.add(userId);
            }
        }
        return new ArrayList<>(ids);
    }
}
