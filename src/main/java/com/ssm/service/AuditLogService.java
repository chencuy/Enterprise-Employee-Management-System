package com.ssm.service;

import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.AuditLog;
import com.ssm.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogService {
    private static final int PAGE_SIZE_LIMIT = 5;

    private final AuditLogMapper auditLogMapper;
    private final AuthService authService;

    public AuditLogService(AuditLogMapper auditLogMapper, AuthService authService) {
        this.auditLogMapper = auditLogMapper;
        this.authService = authService;
    }

    public PageResult<AuditLog> page(String module, String operatorName, int page, int size, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), PAGE_SIZE_LIMIT);
        String safeModule = normalizeFilter(module);
        String safeOperatorName = normalizeFilter(operatorName);
        long total = auditLogMapper.count(safeModule, safeOperatorName);
        List<AuditLog> records = auditLogMapper.page(safeModule, safeOperatorName, (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(total, safePage, safeSize, records);
    }

    public long unreadCount(Long afterId, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        long safeAfterId = afterId == null || afterId < 0 ? 0 : afterId;
        return auditLogMapper.countAfterId(safeAfterId);
    }

    public void record(SessionUser operator, String module, String action, String targetType, Long targetId, String targetName, String detail) {
        if (operator == null || AuthService.isBlank(module) || AuthService.isBlank(action)) {
            return;
        }
        AuditLog log = new AuditLog();
        log.operatorId = operator.id;
        log.operatorName = limit(operator.name, 80, "未知用户");
        log.operatorRole = limit(operator.role, 30, "UNKNOWN");
        log.module = limit(module, 40, "系统");
        log.action = limit(action, 40, "操作");
        log.targetType = limit(targetType, 40, null);
        log.targetId = targetId;
        log.targetName = limit(targetName, 160, null);
        log.detail = limit(detail, 1000, null);
        auditLogMapper.insert(log);
    }

    private String normalizeFilter(String value) {
        if (AuthService.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
    }

    private String limit(String value, int max, String fallback) {
        if (AuthService.isBlank(value)) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
