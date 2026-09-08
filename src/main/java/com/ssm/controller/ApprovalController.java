package com.ssm.controller;

import com.ssm.dto.ApiResponse;
import com.ssm.dto.ApprovalRequestForm;
import com.ssm.dto.ApprovalRequestQuery;
import com.ssm.dto.PageResult;
import com.ssm.entity.ApprovalRequest;
import com.ssm.service.ApprovalService;
import com.ssm.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {
    private final AuthService authService;
    private final ApprovalService approvalService;

    public ApprovalController(AuthService authService, ApprovalService approvalService) {
        this.authService = authService;
        this.approvalService = approvalService;
    }

    @GetMapping
    public ApiResponse<PageResult<ApprovalRequest>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            HttpSession session
    ) {
        return ApiResponse.ok(approvalService.page(page, size, authService.currentUser(session)));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(HttpSession session) {
        return ApiResponse.ok(approvalService.unreadCount(authService.currentUser(session)));
    }

    @PostMapping
    public ApiResponse<ApprovalRequest> create(@RequestBody ApprovalRequestForm form, HttpSession session) {
        return ApiResponse.ok(approvalService.create(form, authService.currentUser(session)));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<ApprovalRequest> approve(
            @PathVariable(name = "id") Long id,
            @RequestBody(required = false) ApprovalRequestForm form,
            HttpSession session
    ) {
        return ApiResponse.ok(approvalService.approve(id, form, authService.currentUser(session)));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<ApprovalRequest> reject(
            @PathVariable(name = "id") Long id,
            @RequestBody(required = false) ApprovalRequestForm form,
            HttpSession session
    ) {
        return ApiResponse.ok(approvalService.reject(id, form, authService.currentUser(session)));
    }

    @PostMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestBody(required = false) ApprovalRequestQuery query,
            HttpSession session
    ) {
        var user = authService.currentUser(session);
        authService.requireCurrentPassword(user, query == null ? null : query.adminPassword);
        String encodedFilename = java.net.URLEncoder.encode("审批记录.csv", java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(approvalService.exportCsvStream(query, user));
    }
}
