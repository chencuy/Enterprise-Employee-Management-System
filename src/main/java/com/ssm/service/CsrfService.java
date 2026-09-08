package com.ssm.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CsrfService {
    public static final String SESSION_CSRF_TOKEN = "CSRF_TOKEN";

    private final SecureRandom secureRandom = new SecureRandom();

    public String ensureToken(HttpSession session) {
        Object current = session.getAttribute(SESSION_CSRF_TOKEN);
        if (current instanceof String token && !token.isBlank()) {
            return token;
        }
        return rotateToken(session);
    }

    public String rotateToken(HttpSession session) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_CSRF_TOKEN, token);
        return token;
    }
}
