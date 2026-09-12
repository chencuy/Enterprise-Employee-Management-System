package com.ssm.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSanitizerTest {
    @Test
    void masksSensitiveJsonAndFormFields() {
        String masked = LogSanitizer.mask("password=secret captchaToken=answer csrfToken=csrf-value");

        assertFalse(masked.contains("secret"));
        assertFalse(masked.contains("answer"));
        assertFalse(masked.contains("csrf-value"));
        assertTrue(masked.contains("***"));
    }

    @Test
    void masksAuthorizationAndCookieHeaders() {
        String masked = LogSanitizer.mask(
                "Authorization: Bearer bearer-secret\nCookie: SESSION=cookie-secret; csrfToken=csrf-secret\nSet-Cookie: SESSION=response-secret");

        assertFalse(masked.contains("bearer-secret"));
        assertFalse(masked.contains("cookie-secret"));
        assertFalse(masked.contains("csrf-secret"));
        assertFalse(masked.contains("response-secret"));
        assertTrue(masked.contains("Authorization: ***"));
        assertTrue(masked.contains("Cookie: ***"));
        assertTrue(masked.contains("Set-Cookie: ***"));
    }
}
