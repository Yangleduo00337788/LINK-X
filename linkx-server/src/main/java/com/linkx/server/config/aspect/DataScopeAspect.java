package com.linkx.server.config.aspect;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.DataScope;
import com.linkx.server.common.DataScopeContext;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RbacConstants;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.DataScopeType;
import com.linkx.server.entity.SysDept;
import com.linkx.server.entity.SysRole;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysDeptMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.RbacService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据权限切面：按角色 {@code data_scope} 写入 {@link DataScopeContext}。
 * <ul>
 *   <li>超管 / data_scope=全部：不限制</li>
 *   <li>仅本人：仅当前用户</li>
 *   <li>本部门及下级：同部门树下的用户（无部门时回退仅本人）</li>
 * </ul>
 * 多角色取最宽范围。未登录 fail-closed。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    private final RbacService rbacService;
    private final JwtUtils jwtUtils;
    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;

    @Pointcut("@annotation(com.linkx.server.common.DataScope)")
    public void dataScopePointcut() {
    }

    @Around("dataScopePointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Long userId = currentUserId();
        if (userId == null) {
            throw new CustomException(401, "未登录或登录已过期");
        }
        resolveAndSet(userId);
        try {
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }

    private void resolveAndSet(Long userId) {
        List<String> roleCodes = rbacService.getUserRoleCodes(userId);
        boolean superAdmin = roleCodes.contains(RbacConstants.ROLE_ADMIN)
                || roleCodes.contains(RbacConstants.ROLE_SUPER_ADMIN)
                || roleCodes.contains(AdminConstants.ROLE_ADMIN)
                || roleCodes.contains(AdminConstants.ROLE_SUPER_ADMIN);
        if (superAdmin) {
            DataScopeContext.setUnrestricted();
            return;
        }
        // 非管理端门户：始终仅本人（客户端调用 @DataScope 的路径）
        if (!AdminConstants.hasAdminPortalRole(roleCodes)) {
            DataScopeContext.setAllowedUserIds(Set.of(userId));
            return;
        }

        List<SysRole> roles = rbacService.getUserRoles(userId);
        int scope = DataScopeType.SELF;
        if (roles != null) {
            for (SysRole role : roles) {
                if (!AdminConstants.hasAdminPortalRole(List.of(role.getRoleCode()))) {
                    continue;
                }
                Integer ds = role.getDataScope();
                if (!DataScopeType.isValid(ds)) {
                    ds = DataScopeType.ALL;
                }
                scope = DataScopeType.widest(scope, ds);
            }
        }
        if (scope == DataScopeType.ALL) {
            DataScopeContext.setUnrestricted();
            return;
        }
        if (scope == DataScopeType.SELF) {
            DataScopeContext.setAllowedUserIds(Set.of(userId));
            return;
        }
        // DEPT：本部门及下级；无部门时回退仅本人
        SysUser user = sysUserMapper.selectOneById(userId);
        Long deptId = user != null ? user.getDeptId() : null;
        if (deptId == null) {
            DataScopeContext.setAllowedUserIds(Set.of(userId));
            return;
        }
        Set<Long> deptIds = collectDeptTreeIds(deptId);
        Set<Long> allowed = sysUserMapper.selectListByQuery(
                        QueryWrapper.create().where(SysUser::getDeptId).in(deptIds))
                .stream()
                .map(SysUser::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        allowed.add(userId);
        DataScopeContext.setAllowedUserIds(allowed);
    }

    private Set<Long> collectDeptTreeIds(Long rootId) {
        List<SysDept> all = sysDeptMapper.selectListByQuery(
                QueryWrapper.create().where(SysDept::getStatus).eq(1));
        Set<Long> result = new HashSet<>();
        Queue<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        result.add(rootId);
        while (!queue.isEmpty()) {
            Long parent = queue.poll();
            for (SysDept d : all) {
                if (parent.equals(d.getParentId()) && result.add(d.getId())) {
                    queue.add(d.getId());
                }
            }
        }
        return result;
    }

    private Long currentUserId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        return AuthUtils.getUserId(request, jwtUtils);
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attr != null ? attr.getRequest() : null;
    }
}
