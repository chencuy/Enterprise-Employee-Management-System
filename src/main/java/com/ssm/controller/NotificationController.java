package com.ssm.controller;

import com.ssm.dto.ApiResponse;
import com.ssm.dto.PageResult;
import com.ssm.entity.NotificationItem;
import com.ssm.service.AuthService;
import com.ssm.service.NotificationCenterService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final AuthService authService;
    private final NotificationCenterService notificationCenterService;

    public NotificationController(AuthService authService, NotificationCenterService notificationCenterService) {
        this.authService = authService;
        this.notificationCenterService = notificationCenterService;
    }

    @GetMapping
    public ApiResponse<PageResult<NotificationItem>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            HttpSession session
    ) {
        return ApiResponse.ok(notificationCenterService.page(authService.currentUser(session), page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(HttpSession session) {
        return ApiResponse.ok(notificationCenterService.unreadCount(authService.currentUser(session)));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable(name = "id") Long id, HttpSession session) {
        notificationCenterService.markRead(id, authService.currentUser(session));
        return ApiResponse.ok();
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead(HttpSession session) {
        notificationCenterService.markAllRead(authService.currentUser(session));
        return ApiResponse.ok();
    }
}
