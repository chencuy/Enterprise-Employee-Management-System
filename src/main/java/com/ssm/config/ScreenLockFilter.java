package com.ssm.config;

import com.ssm.dto.SessionUser;
import com.ssm.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ScreenLockFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED_WHEN_LOCKED = Set.of(
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/me",
            "/api/profile/verify-lock-password",
            "/api/profile/lock-screen"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || "OPTIONS".equalsIgnoreCase(request.getMethod()) || ALLOWED_WHEN_LOCKED.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(AuthService.SESSION_USER);
        if (value instanceof SessionUser user
                && user.hasLockPassword
                && !Boolean.TRUE.equals(session.getAttribute(AuthService.SCREEN_UNLOCKED))) {
            response.setStatus(HttpStatus.LOCKED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"当前会话已锁屏，请先解锁\",\"data\":null}");
            return;
        }
        chain.doFilter(request, response);
    }
}
