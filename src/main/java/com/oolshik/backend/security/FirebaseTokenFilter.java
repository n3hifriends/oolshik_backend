package com.oolshik.backend.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FirebaseTokenFilter extends OncePerRequestFilter {

    private final FirebaseAuth firebaseAuth;
    private final String projectId;
    private final boolean checkRevoked;

    public FirebaseTokenFilter(FirebaseAuth firebaseAuth, String projectId, boolean checkRevoked) {
        this.firebaseAuth = Objects.requireNonNull(firebaseAuth);
        this.projectId = Objects.requireNonNull(projectId);
        this.checkRevoked = checkRevoked;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }

        String idToken = auth.substring(7).trim();
        try {
            FirebaseToken decoded = checkRevoked
                    ? firebaseAuth.verifyIdToken(idToken, true)
                    : firebaseAuth.verifyIdToken(idToken);

            String expectedIss = "https://securetoken.google.com/" + projectId;
            if (!expectedIss.equals(decoded.getIssuer())) {
                unauthorized(res, "invalid_issuer");
                return;
            }
            Object aud = decoded.getClaims().get("aud");
            if (aud == null || !projectId.equals(aud.toString())) {
                unauthorized(res, "invalid_audience");
                return;
            }

            String uid = decoded.getUid();
            String phone = (String) decoded.getClaims().get("phone_number");
            String email = decoded.getEmail();

            Collection<SimpleGrantedAuthority> authorities = extractAuthorities(decoded);

            FirebaseUserPrincipal principal = new FirebaseUserPrincipal(uid, phone, email);
            var authToken = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            chain.doFilter(req, res);

        } catch (FirebaseAuthException e) {
            SecurityContextHolder.clearContext();
            unauthorized(res, e.getAuthErrorCode() != null ? e.getAuthErrorCode().name() : "invalid_token");
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            unauthorized(res, "invalid_token");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return p.startsWith("/actuator")
                || p.startsWith("/swagger")
                || p.startsWith("/v3/api-docs")
                || p.startsWith("/api/public")
                || p.startsWith("/api/auth/echo");
    }

    private void unauthorized(HttpServletResponse res, String code) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json");
        res.getWriter().write("{\"ok\":false,\"error\":\"" + code + "\"}");
    }

    @SuppressWarnings("unchecked")
    private Collection<SimpleGrantedAuthority> extractAuthorities(FirebaseToken token) {
        Object roles = token.getClaims().get("roles"); // if you add custom claims later
        if (roles instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    public record FirebaseUserPrincipal(String uid, String phone, String email) {
    }
}