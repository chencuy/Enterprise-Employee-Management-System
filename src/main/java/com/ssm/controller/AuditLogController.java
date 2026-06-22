package com.ssm.controller;

import com.ssm.dto.ApiResponse;
import com.ssm.dto.PageResult;
import com.ssm.entity.AuditLog;
import com.ssm.service.AuditLogService;
import com.ssm.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    private final AuthService authService;
    private final AuditLogService auditLogService;

    public AuditLogController(AuthService authService, AuditLogService auditLogService) {
        this.authService = authService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<PageResult<AuditLog>> page(
            @RequestParam(name = "module", required = false) String module,
            @RequestParam(name = "operatorName", required = false) String operatorName,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            HttpSession session
    ) {
        return ApiResponse.ok(auditLogService.page(module, operatorName, page, size, authService.currentUser(session)));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(
            @RequestParam(name = "afterId", defaultValue = "0") Long afterId,
            HttpSession session
    ) {
        return ApiResponse.ok(auditLogService.unreadCount(afterId, authService.currentUser(session)));
    }
}
