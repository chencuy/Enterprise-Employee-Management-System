package com.ssm.config;

import java.util.regex.Pattern;

/** Masks credentials and request tokens before they reach an application log. */
public final class LogSanitizer {
    private static final Pattern AUTHORIZATION_HEADER = Pattern.compile(
            "(?im)(\\bAuthorization\\s*:\s*)(?:Bearer\\s+)?[^\\r\\n,;]+$");
    private static final Pattern COOKIE_HEADER = Pattern.compile(
            "(?im)(\\b(?:Cookie|Set-Cookie)\\s*:\s*)[^\\r\\n]+$");
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i)(\\\"?(?:password|passwd|pwd|captcha(?:Token|Answer)?|cookie|authorization|csrf(?:Token)?|x-csrf-token|token)\\\"?\\s*[=:]\\s*)(\\\"?)([^\\\"\\s,;}&]+)(\\\"?)");

    private LogSanitizer() {
    }

    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String masked = AUTHORIZATION_HEADER.matcher(value).replaceAll("$1***");
        masked = COOKIE_HEADER.matcher(masked).replaceAll("$1***");
        return SENSITIVE_FIELD.matcher(masked).replaceAll("$1$2***$4");
    }
}
