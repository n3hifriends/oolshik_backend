package com.oolshik.backend.web.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String cid = MDC.get("cid"); // set by CorrelationIdFilter
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String qs = request.getQueryString();
        String full = (qs == null) ? uri : (uri + "?" + qs);
        String auth = request.getHeader("Authorization");
        String authShort = (auth == null) ? "none" : (auth.startsWith("Bearer ") ? "bearer" : "other");

        log.info("[{}] ⇢ {} {} auth={}", cid, method, full, authShort);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long dur = System.currentTimeMillis() - start;
            log.info("[{}] ⇠ {} {} -> {} {}ms", cid, method, full, response.getStatus(), dur);
        }
    }
}
