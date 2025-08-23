package com.oolshik.backend.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Arrays;

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
        String sig = joinPoint.getSignature().toShortString();
        String args = Arrays.toString(joinPoint.getArgs());
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
}
