package com.ssm.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Component
public class ProductionSecurityValidator {
    private static final String DEFAULT_CAPTCHA_KEY = "ems-default-captcha-key-change-me";

    private final Environment environment;
    private final boolean requireProductionHardening;
    private final String captchaHmacKey;
    private final boolean sessionCookieSecure;
    private final boolean hstsEnabled;
    private final String dbUrl;
    private final String dbPassword;

    public ProductionSecurityValidator(
            Environment environment,
            @Value("${app.security.require-production-hardening:false}") boolean requireProductionHardening,
            @Value("${app.security.captcha-hmac-key:}") String captchaHmacKey,
            @Value("${server.servlet.session.cookie.secure:false}") boolean sessionCookieSecure,
            @Value("${app.security.hsts-enabled:false}") boolean hstsEnabled,
            @Value("${spring.datasource.url:}") String dbUrl,
            @Value("${spring.datasource.password:}") String dbPassword
    ) {
        this.environment = environment;
        this.requireProductionHardening = requireProductionHardening;
        this.captchaHmacKey = captchaHmacKey;
        this.sessionCookieSecure = sessionCookieSecure;
        this.hstsEnabled = hstsEnabled;
        this.dbUrl = dbUrl;
        this.dbPassword = dbPassword;
    }

    @PostConstruct
    public void validate() {
        if (!requiresHardening()) {
            return;
        }
        require(captchaHmacKey != null
                        && !DEFAULT_CAPTCHA_KEY.equals(captchaHmacKey)
                        && captchaHmacKey.length() >= 32,
                "生产环境必须配置长度至少 32 位的 CAPTCHA_HMAC_KEY，且不能使用默认值");
        require(sessionCookieSecure, "生产环境必须设置 SESSION_COOKIE_SECURE=true");
        require(hstsEnabled, "生产环境必须设置 HSTS_ENABLED=true");
        require(dbPassword != null && !dbPassword.isBlank() && !"123456".equals(dbPassword),
                "生产环境必须配置安全的 DB_PASSWORD，不能使用默认密码");
        String normalizedUrl = dbUrl == null ? "" : dbUrl.toLowerCase(Locale.ROOT);
        require(!normalizedUrl.contains("usessl=false"),
                "生产环境 DB_URL 不能配置 useSSL=false");
        require(!normalizedUrl.contains("allowpublickeyretrieval=true"),
                "生产环境 DB_URL 不能配置 allowPublicKeyRetrieval=true");
    }

    private boolean requiresHardening() {
        return requireProductionHardening
                || Arrays.stream(environment.getActiveProfiles()).anyMatch(profile -> "prod".equalsIgnoreCase(profile));
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
