package com.linkx.server.config.aspect;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.DataScope;
import com.linkx.server.common.DataScopeContext;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RbacConstants;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.RbacService;
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

/**
 * 数据权限切面。
 * <p>
 * 拦截标注 {@link DataScope} 的 Service 方法，根据当前登录用户角色判定数据可见范围，
 * 写入 {@link DataScopeContext}（ThreadLocal）供 Service 方法读取并拼接过滤条件：
 * <ul>
 *   <li>admin 角色：{@code DataScopeContext.getUserId()} 返回 null，表示不限制</li>
 *   <li>普通用户：返回当前 userId，Service 据此过滤本人数据</li>
 * </ul>
 * 方法执行完毕（无论成功与否）清除 ThreadLocal，避免线程池复用导致的脏读。
 * </p>
 * <p>
 * 未登录时 fail-closed 抛 401，禁止以 admin 级可见性放行。
 * 现有 Service 可通过 {@code @DataScope} + {@link DataScopeContext#getUserId()} 接入数据权限。
 * </p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    private final RbacService rbacService;
    private final JwtUtils jwtUtils;

    /**
     * 数据权限切入点：方法上标注 @DataScope
     */
    @Pointcut("@annotation(com.linkx.server.common.DataScope)")
    public void dataScopePointcut() {
    }

    @Around("dataScopePointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Long userId = currentUserId();
        if (userId == null) {
            // fail-closed：拦截器被绕过或非 Web 上下文时拒绝，禁止以 admin 级可见性执行
            throw new CustomException(401, "未登录或登录已过期");
        }
        boolean isAdmin = rbacService.hasRole(userId, RbacConstants.ROLE_ADMIN);
        // admin: 不限制（null）；普通用户: 仅可见本人数据
        DataScopeContext.setUserId(isAdmin ? null : userId);
        try {
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
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
