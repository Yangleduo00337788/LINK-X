package com.linkx.server.config.aspect;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.RbacService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * 角色校验切面。
 * <p>
 * 拦截标注 {@link RequireRole} 的方法（含类级别注解），从当前请求解析登录用户 ID，
 * 调用 {@link RbacService#getUserRoleCodes(Long)} 获取角色编码，
 * 按注解声明的角色集合与逻辑关系（any/all）进行校验，不满足抛出 403。
 * </p>
 * <p>
 * 用户 ID 解析复用 {@link AuthUtils#getUserId}，与 LoginInterceptor 写入的 request attribute 对齐。
 * </p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RoleRequiredAspect {

    private final RbacService rbacService;
    private final JwtUtils jwtUtils;

    /**
     * 角色校验切入点：方法或类上标注 @RequireRole
     */
    @Pointcut("@annotation(com.linkx.server.common.RequireRole) "
            + "|| @within(com.linkx.server.common.RequireRole)")
    public void rolePointcut() {
    }

    @Around("rolePointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        RequireRole annotation = resolveAnnotation(joinPoint, RequireRole.class);
        if (annotation == null) {
            return joinPoint.proceed();
        }
        Long userId = currentUserId();
        if (userId == null) {
            throw new CustomException(401, "未登录或登录已过期");
        }
        List<String> userRoles = rbacService.getUserRoleCodes(userId);
        String[] required = annotation.adminPortal()
                ? AdminConstants.ADMIN_ROLES
                : annotation.value();
        if (required.length == 0) {
            log.warn("角色校验配置为空 userId={}, method={}", userId, joinPoint.getSignature());
            throw new CustomException(403, "无权限访问该资源");
        }
        boolean ok = annotation.logicalOr()
                ? Arrays.stream(required).anyMatch(userRoles::contains)
                : Arrays.stream(required).allMatch(userRoles::contains);
        if (!ok) {
            log.warn("角色校验失败 userId={}, required={}, actual={}", userId, Arrays.toString(required), userRoles);
            throw new CustomException(403, "无权限访问该资源");
        }
        return joinPoint.proceed();
    }

    /**
     * 解析方法或类上的注解（方法级优先）
     */
    private <A extends Annotation> A resolveAnnotation(ProceedingJoinPoint joinPoint, Class<A> type) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        A methodAnn = method.getAnnotation(type);
        if (methodAnn != null) {
            return methodAnn;
        }
        return joinPoint.getTarget().getClass().getAnnotation(type);
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
