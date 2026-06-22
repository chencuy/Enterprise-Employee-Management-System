package com.ssm.controller;

import com.ssm.dto.ApiResponse;
import com.ssm.dto.LeaveRequestForm;
import com.ssm.dto.PageResult;
import com.ssm.entity.LeaveRequest;
import com.ssm.service.AuthService;
import com.ssm.service.LeaveRequestService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaves")
public class LeaveRequestController {
    private final AuthService authService;
    private final LeaveRequestService leaveRequestService;

    public LeaveRequestController(AuthService authService, LeaveRequestService leaveRequestService) {
        this.authService = authService;
        this.leaveRequestService = leaveRequestService;
    }

    @GetMapping
    public ApiResponse<PageResult<LeaveRequest>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            HttpSession session
    ) {
        return ApiResponse.ok(leaveRequestService.page(page, size, authService.currentUser(session)));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(HttpSession session) {
        return ApiResponse.ok(leaveRequestService.unreadCount(authService.currentUser(session)));
    }

    @PostMapping
    public ApiResponse<LeaveRequest> create(@RequestBody LeaveRequestForm form, HttpSession session) {
        return ApiResponse.ok(leaveRequestService.create(form, authService.currentUser(session)));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<LeaveRequest> approve(@PathVariable(name = "id") Long id, @RequestBody(required = false) LeaveRequestForm form, HttpSession session) {
        return ApiResponse.ok(leaveRequestService.approve(id, form, authService.currentUser(session)));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<LeaveRequest> reject(@PathVariable(name = "id") Long id, @RequestBody(required = false) LeaveRequestForm form, HttpSession session) {
        return ApiResponse.ok(leaveRequestService.reject(id, form, authService.currentUser(session)));
    }
}
