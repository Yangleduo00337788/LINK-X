package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.AdminAudienceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAudienceServiceImpl implements AdminAudienceService {

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
        return rbacService.listUserIdsWithPermission(permissionCode);
    }
}
