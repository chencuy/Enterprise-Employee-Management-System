package com.ssm.config;

import com.ssm.service.CsrfService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Component
public class CsrfFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (requiresCsrfCheck(request)) {
            HttpSession session = request.getSession(false);
            String expected = session == null ? null : (String) session.getAttribute(CsrfService.SESSION_CSRF_TOKEN);
            String actual = request.getHeader("X-CSRF-Token");
            if (!sameToken(expected, actual)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":\"CSRF 校验失败，请刷新页面后重试\",\"data\":null}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean requiresCsrfCheck(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return path.startsWith("/api/")
                && !SAFE_METHODS.contains(method)
                && !"/api/auth/login".equals(path);
    }

    private boolean sameToken(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
