package com.linkx.server.config.aspect;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RequireStepUp;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.admin.AdminStepUpService;
import com.linkx.server.service.admin.impl.AdminStepUpServiceImpl;
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
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 高危操作二次验证切面（在权限校验之后执行）。
 */
@Slf4j
@Aspect
@Component
@Order(200)
@RequiredArgsConstructor
public class StepUpRequiredAspect {

    private final AdminStepUpService adminStepUpService;
    private final JwtUtils jwtUtils;

    @Pointcut("@annotation(com.linkx.server.common.RequireStepUp)")
    public void stepUpPointcut() {
    }

    @Around("stepUpPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!adminStepUpService.isEnabled()) {
            return joinPoint.proceed();
        }
        RequireStepUp annotation = resolveAnnotation(joinPoint);
        if (annotation == null) {
            return joinPoint.proceed();
        }
        Long userId = currentUserId();
        if (userId == null) {
            throw new CustomException(401, "未登录或登录已过期");
        }
        String action = annotation.value();
        HttpServletRequest request = currentRequest();
        String token = request == null ? null : request.getHeader(AdminStepUpServiceImpl.HEADER);
        if (!StringUtils.hasText(token)) {
            throw stepUpRequired(userId, action);
        }
        if (!adminStepUpService.consumeToken(userId, token, action)) {
            throw stepUpRequired(userId, action);
        }
        return joinPoint.proceed();
    }

    private CustomException stepUpRequired(Long userId, String action) {
        var options = adminStepUpService.options(userId, action);
        if (options.getMethods() == null || options.getMethods().isEmpty()) {
            throw new CustomException(403, "高危操作需二次验证，请先启用 TOTP 或绑定邮箱");
        }
        return new CustomException(
                AdminStepUpServiceImpl.CODE_STEP_UP_REQUIRED,
                "需要二次验证",
                options);
    }

    private RequireStepUp resolveAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getAnnotation(RequireStepUp.class);
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
