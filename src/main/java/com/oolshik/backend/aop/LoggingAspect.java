package com.oolshik.backend.aop;

import com.oolshik.backend.logging.Sensitive;
import com.oolshik.backend.security.AuthenticatedUserPrincipal;
import com.oolshik.backend.util.MaskingUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    // Controllers, Services, Repositories under our base package
    @Pointcut("within(com.oolshik.backend..*)")
    public void applicationPackagePointcut() {}

    // Only public methods
    @Pointcut("execution(public * *(..))")
    public void publicMethodPointcut() {}

    @Around("applicationPackagePointcut() && publicMethodPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String sig = joinPoint.getSignature().toShortString();
        String args = sanitizeArguments(method, joinPoint.getArgs());
        String cid = MDC.get("cid");
        long startNanos = System.nanoTime();
        log.info("[{}] ▶ {} args={}", cid, sig, args);
        try {
            Object result = joinPoint.proceed();
            long durMs = (System.nanoTime() - startNanos) / 1_000_000;
            String resStr = (result == null) ? "null" : result.getClass().getSimpleName();
            log.info("[{}] ◀ {} ok in {}ms -> {}", cid, sig, durMs, resStr);
            return result;
        } catch (Throwable ex) {
            long durMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.error("[{}] ✖ {} failed in {}ms: {}", cid, sig, durMs, ex.toString(), ex);
            throw ex;
        }
    }

    private String sanitizeArguments(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        Parameter[] parameters = method.getParameters();
        List<String> sanitized = new ArrayList<>(args.length);
        for (int i = 0; i < args.length; i++) {
            Parameter parameter = i < parameters.length ? parameters[i] : null;
            String name = parameter != null ? parameter.getName() : "arg" + i;
            sanitized.add(name + "=" + sanitizeValue(args[i], name, parameter));
        }
        return sanitized.toString();
    }

    private Object sanitizeValue(Object value, String name, Parameter parameter) {
        if (parameter != null && parameter.isAnnotationPresent(Sensitive.class)) {
            return redactByName(name, value);
        }
        if (value == null) {
            return null;
        }
        if (value instanceof AuthenticatedUserPrincipal principal) {
            return Map.of(
                    "identityProvider", principal.identityProvider(),
                    "providerUserId", principal.providerUserId() != null ? "[redacted]" : null,
                    "phone", MaskingUtils.maskPhone(principal.phone()),
                    "email", redactGeneric(principal.email())
            );
        }
        if (value instanceof CharSequence sequence) {
            return sanitizeScalar(name, sequence.toString());
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof UUID || value instanceof Enum<?>) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                sanitized.put(key, sanitizeValue(entry.getValue(), key, null));
            }
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> sanitized = new ArrayList<>(collection.size());
            for (Object item : collection) {
                sanitized.add(sanitizeValue(item, name, null));
            }
            return sanitized;
        }
        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            List<Object> sanitized = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                sanitized.add(sanitizeValue(Array.get(value, i), name, null));
            }
            return sanitized;
        }
        if (value.getClass().isRecord()) {
            return sanitizeRecord(value);
        }
        if (isApplicationObject(value)) {
            return value.getClass().getSimpleName();
        }
        return sanitizeScalar(name, String.valueOf(value));
    }

    private Object sanitizeRecord(Object value) {
        try {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (RecordComponent component : value.getClass().getRecordComponents()) {
                Object componentValue = component.getAccessor().invoke(value);
                sanitized.put(
                        component.getName(),
                        component.isAnnotationPresent(Sensitive.class)
                                ? redactByName(component.getName(), componentValue)
                                : sanitizeValue(componentValue, component.getName(), null)
                );
            }
            return sanitized;
        } catch (Exception ex) {
            return value.getClass().getSimpleName();
        }
    }

    private Object sanitizeScalar(String name, String value) {
        if (value == null) {
            return null;
        }
        if (isSensitiveName(name)) {
            return redactByName(name, value);
        }
        if (value.matches("^\\+?[0-9]{10,15}$")) {
            return MaskingUtils.maskPhone(value);
        }
        if (value.matches("^\\d{4,8}$")) {
            return MaskingUtils.redactOtp();
        }
        if (value.startsWith("Bearer ") || value.length() > 48) {
            return "[redacted]";
        }
        return value;
    }

    private Object redactByName(String name, Object value) {
        if (name != null && name.toLowerCase().contains("phone")) {
            return MaskingUtils.maskPhone(value == null ? null : String.valueOf(value));
        }
        return "[redacted]";
    }

    private String redactGeneric(String value) {
        return value == null || value.isBlank() ? null : "[redacted]";
    }

    private boolean isSensitiveName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.contains("phone")
                || lower.contains("otp")
                || lower.contains("code")
                || lower.contains("token")
                || lower.contains("password")
                || lower.contains("secret")
                || lower.contains("authorization");
    }

    private boolean isApplicationObject(Object value) {
        Package pkg = value.getClass().getPackage();
        return pkg != null && pkg.getName().startsWith("com.oolshik.backend");
    }
}
