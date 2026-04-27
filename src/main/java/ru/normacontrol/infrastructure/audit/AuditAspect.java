package ru.normacontrol.infrastructure.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aspect that writes audit entries around annotated methods.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    /**
     * Audit method invocation and outcome.
     *
     * @param joinPoint current join point
     * @param auditLogged annotation instance
     * @return target method result
     * @throws Throwable rethrows target exception
     */
    @Around("@annotation(auditLogged)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLogged auditLogged) throws Throwable {
        UUID userId = extractUserId();
        Object result = null;
        Throwable failure = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            UUID resourceId = extractResourceId(joinPoint.getArgs(), result);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("method", joinPoint.getSignature().toShortString());
            details.put("argumentsCount", joinPoint.getArgs().length);

            if (failure == null) {
                auditService.log(userId, auditLogged.action(), auditLogged.resourceType(), resourceId, true, details);
            } else {
                details.put("exception", failure.getClass().getSimpleName());
                auditService.log(userId, auditLogged.action(), auditLogged.resourceType(), resourceId, false, details, failure.getMessage());
            }
        }
    }

    private UUID extractUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private UUID extractResourceId(Object[] args, Object result) {
        UUID fromResult = extractIdFromObject(result);
        if (fromResult != null) {
            return fromResult;
        }
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid;
            }
        }
        return null;
    }

    private UUID extractIdFromObject(Object value) {
        if (value == null) {
            return null;
        }
        try {
            Method method = value.getClass().getMethod("getId");
            Object id = method.invoke(value);
            if (id instanceof UUID uuid) {
                return uuid;
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        return null;
    }
}
