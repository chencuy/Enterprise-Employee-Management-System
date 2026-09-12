package com.ssm.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.LayoutBase;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Compact JSON layout shared by console and rolling file logs. */
public class JsonLogLayout extends LayoutBase<ILoggingEvent> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String doLayout(ILoggingEvent event) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        record.put("level", event.getLevel().toString());
        record.put("logger", event.getLoggerName());
        record.put("thread", event.getThreadName());
        record.put("message", LogSanitizer.mask(event.getFormattedMessage()));

        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc != null) {
            putIfPresent(record, "requestId", mdc.get("requestId"));
            putIfPresent(record, "httpMethod", mdc.get("httpMethod"));
            putIfPresent(record, "httpPath", mdc.get("httpPath"));
            putIfPresent(record, "httpStatus", mdc.get("httpStatus"));
            putIfPresent(record, "durationMs", mdc.get("durationMs"));
            putIfPresent(record, "clientIp", mdc.get("clientIp"));
        }

        if (event.getThrowableProxy() != null) {
            record.put("exception", LogSanitizer.mask(ThrowableProxyUtil.asString(event.getThrowableProxy())));
        }
        try {
            return objectMapper.writeValueAsString(record) + System.lineSeparator();
        } catch (JsonProcessingException exception) {
            return "{\"level\":\"ERROR\",\"message\":\"log serialization failed\"}" + System.lineSeparator();
        }
    }

    private void putIfPresent(Map<String, Object> record, String key, String value) {
        if (value != null && !value.isBlank()) {
            record.put(key, LogSanitizer.mask(value));
        }
    }
}
