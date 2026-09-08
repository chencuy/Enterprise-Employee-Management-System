package com.ssm.config;

import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.cors.allowed-origins:http://localhost:8080}")
    private String allowedOrigins;
    @Value("${app.security.content-security-policy:default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'}")
    private String contentSecurityPolicy;
    @Value("${app.security.hsts-enabled:false}")
    private boolean hstsEnabled;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(parseAllowedOrigins())
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-CSRF-Token")
                .allowCredentials(true);
    }

    @Bean
    public FilterRegistrationBean<Filter> securityHeadersFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter((request, response, chain) -> {
            var httpResponse = (jakarta.servlet.http.HttpServletResponse) response;
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("X-Frame-Options", "DENY");
            httpResponse.setHeader("Referrer-Policy", "same-origin");
            httpResponse.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
            httpResponse.setHeader("Content-Security-Policy", contentSecurityPolicy);
            if (hstsEnabled) {
                httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }
            chain.doFilter(request, response);
        });
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    private String[] parseAllowedOrigins() {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        if (Arrays.asList(origins).contains("*")) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS 不能为 *，带 Cookie 的接口必须指定可信来源");
        }
        return origins;
    }
}
