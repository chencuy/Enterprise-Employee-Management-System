package com.ssm.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestLoggingFilterTest {
    @Test
    void returnsSuppliedValidRequestIdAndClearsMdc() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profile");
        request.addHeader("X-Request-ID", "client-request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> response.setStatus(204));

        assertEquals("client-request-123", response.getHeader("X-Request-ID"));
        assertNull(MDC.get("requestId"));
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profile");
        request.addHeader("X-Request-ID", "unsafe request id\r\nInjected: value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> response.setStatus(200));

        String requestId = response.getHeader("X-Request-ID");
        assertFalse(requestId.contains("unsafe"));
        assertFalse(requestId.contains("\r"));
        assertFalse(requestId.contains("\n"));
    }
}
