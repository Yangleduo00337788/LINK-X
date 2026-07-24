package com.linkx.server.config.aspect;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.controller.vo.UserInfoVO;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.service.AuditLogService;
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

import java.lang.reflect.Method;

/**
 * 审计日志切面
 * 自动记录敏感操作的审计日志
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final JwtUtils jwtUtils;
    private final LinkxProperties linkxProperties;

    /**
     * 审计操作注解切入点
     */
    @Pointcut("@annotation(com.linkx.server.config.aspect.AuditAction)")
    public void auditPointcut() {}

    /**
     * 环绕通知：记录审计日志
     */
    @Around("auditPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditAction auditAction = method.getAnnotation(AuditAction.class);

        Long userId = null;
        String username = null;

        // 尝试从 request attribute 获取用户信息
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr instanceof Long) {
            userId = (Long) userIdAttr;
        }

        // 如果没有 userId，尝试从 Authorization header 解析
        if (userId == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    userId = jwtUtils.getUserIdFromToken(token);
                } catch (Exception e) {
                    // Token 无效或过期，忽略
                }
            }
        }

        String ip = ClientIpResolver.resolve(request, linkxProperties);
        String userAgent = request.getHeader("User-Agent");

        String operationType = auditAction.operationType();
        String description = auditAction.description();

        // 登录/注册时尚无 Token，先从请求体取用户名
        if (username == null) {
            username = extractUsernameFromArgs(joinPoint.getArgs());
        }

        Object result = null;
        boolean success = true;
        String reason = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            success = false;
            reason = e.getMessage();
            throw e;
        } finally {
            // 登录成功后从响应补全 userId / username
            if (success && result != null) {
                Long fromResult = extractUserIdFromResult(result);
                if (fromResult != null) {
                    userId = fromResult;
                }
                String nameFromResult = extractUsernameFromResult(result);
                if (nameFromResult != null && !nameFromResult.isBlank()) {
                    username = nameFromResult;
                }
            }
            try {
                auditLogService.log(
                        SysAuditLog.OperationType.valueOf(operationType),
                        description,
                        userId,
                        username,
                        ip,
                        userAgent,
                        success,
                        reason
                );
            } catch (Exception e) {
                log.warn("记录审计日志失败: {}", e.getMessage());
            }
        }
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private static String extractUsernameFromArgs(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof LoginDTO login) {
                return login.getUsername();
            }
            if (arg instanceof RegisterDTO register) {
                return register.getUsername();
            }
        }
        return null;
    }

    private static Long extractUserIdFromResult(Object result) {
        if (!(result instanceof Result<?> r) || r.getData() == null) {
            return null;
        }
        Object data = r.getData();
        if (data instanceof TokenVO token && token.getUser() != null) {
            return token.getUser().getId();
        }
        return null;
    }

    private static String extractUsernameFromResult(Object result) {
        if (!(result instanceof Result<?> r) || r.getData() == null) {
            return null;
        }
        Object data = r.getData();
        if (data instanceof TokenVO token && token.getUser() != null) {
            UserInfoVO user = token.getUser();
            return user.getUsername();
        }
        return null;
    }
}
