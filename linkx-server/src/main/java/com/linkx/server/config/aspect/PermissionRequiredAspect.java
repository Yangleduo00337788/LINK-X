package com.linkx.server.config.aspect;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RequirePermission;
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
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 权限校验切面。
 * <p>
 * 拦截标注 {@link RequirePermission} 的方法（含类级别注解），从当前请求解析登录用户 ID，
 * 调用 {@link RbacService#hasPermission(Long, String)} 逐项校验权限编码，
 * 按注解声明的权限集合与逻辑关系（any/all）判定，不满足抛出 403。
 * 持有通配符权限 * 的用户自动通过全部权限校验（由 RbacService 内部处理）。
 * </p>
 */
@Slf4j
@Aspect
@Component
@Order(100)
@RequiredArgsConstructor
public class PermissionRequiredAspect {

    private final RbacService rbacService;
    private final JwtUtils jwtUtils;

    /**
     * 权限校验切入点：方法或类上标注 @RequirePermission
     */
    @Pointcut("@annotation(com.linkx.server.common.RequirePermission) "
            + "|| @within(com.linkx.server.common.RequirePermission)")
    public void permissionPointcut() {
    }

    @Around("permissionPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        RequirePermission annotation = resolveAnnotation(joinPoint, RequirePermission.class);
        if (annotation == null) {
            return joinPoint.proceed();
        }
        Long userId = currentUserId();
        if (userId == null) {
            throw new CustomException(401, "未登录或登录已过期");
        }
        String[] required = annotation.value();
        boolean ok = annotation.logicalOr()
                ? Arrays.stream(required).anyMatch(code -> rbacService.hasPermission(userId, code))
                : Arrays.stream(required).allMatch(code -> rbacService.hasPermission(userId, code));
        if (!ok) {
            log.warn("权限校验失败 userId={}, required={}", userId, Arrays.toString(required));
            throw new CustomException(403, "无权限访问该资源");
        }
        return joinPoint.proceed();
    }

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
