package com.ssm.controller;

import com.ssm.dto.AnnouncementReadRequest;
import com.ssm.dto.AnnouncementReadStats;
import com.ssm.dto.AnnouncementRequest;
import com.ssm.dto.ApiResponse;
import com.ssm.dto.SessionUser;
import com.ssm.entity.Announcement;
import com.ssm.service.AnnouncementService;
import com.ssm.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    private final AuthService authService;
    private final AnnouncementService announcementService;

    public AnnouncementController(AuthService authService, AnnouncementService announcementService) {
        this.authService = authService;
        this.announcementService = announcementService;
    }

    @GetMapping
    public ApiResponse<List<Announcement>> list(HttpSession session) {
        SessionUser user = authService.currentUser(session);
        return ApiResponse.ok(announcementService.list(user));
    }

    @GetMapping("/pending")
    public ApiResponse<List<Announcement>> pending(HttpSession session) {
        SessionUser user = authService.currentUser(session);
        return ApiResponse.ok(announcementService.pending(user));
    }

    @PostMapping
    public ApiResponse<Announcement> create(@RequestBody AnnouncementRequest request, HttpSession session) {
        SessionUser user = authService.currentUser(session);
        return ApiResponse.ok(announcementService.create(request, user));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable(name = "id") Long id, @RequestBody AnnouncementReadRequest request, HttpSession session) {
        SessionUser user = authService.currentUser(session);
        announcementService.markRead(id, request, user);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/pin")
    public ApiResponse<Announcement> togglePin(@PathVariable(name = "id") Long id, HttpSession session) {
        SessionUser user = authService.currentUser(session);
        return ApiResponse.ok(announcementService.togglePin(id, user));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> unpublish(@PathVariable(name = "id") Long id, HttpSession session) {
        SessionUser user = authService.currentUser(session);
        announcementService.unpublish(id, user);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/read-stats")
    public ApiResponse<AnnouncementReadStats> readStats(@PathVariable(name = "id") Long id, HttpSession session) {
        SessionUser user = authService.currentUser(session);
        return ApiResponse.ok(announcementService.readStats(id, user));
    }
}
