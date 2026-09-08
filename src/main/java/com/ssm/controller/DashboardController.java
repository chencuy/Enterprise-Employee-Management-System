package com.ssm.controller;

import com.ssm.dto.ApiResponse;
import com.ssm.dto.DashboardStats;
import com.ssm.service.AuthService;
import com.ssm.service.DashboardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final AuthService authService;
    private final DashboardService dashboardService;

    public DashboardController(AuthService authService, DashboardService dashboardService) {
        this.authService = authService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/admin")
    public ApiResponse<DashboardStats> adminStats(HttpSession session) {
        return ApiResponse.ok(dashboardService.adminStats(authService.currentUser(session)));
    }
}
