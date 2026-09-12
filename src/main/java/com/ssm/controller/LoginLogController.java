package com.ssm.controller;

import com.ssm.dto.ApiResponse;
import com.ssm.dto.LoginLogQuery;
import com.ssm.dto.PageResult;
import com.ssm.entity.LoginLog;
import com.ssm.service.AuthService;
import com.ssm.service.LoginLogService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/login-logs")
public class LoginLogController {
    private final AuthService authService;
    private final LoginLogService loginLogService;

    public LoginLogController(AuthService authService, LoginLogService loginLogService) {
        this.authService = authService;
        this.loginLogService = loginLogService;
    }

    @GetMapping
    public ApiResponse<PageResult<LoginLog>> page(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "ipAddress", required = false) String ipAddress,
            @RequestParam(name = "result", required = false) String result,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            HttpSession session
    ) {
        LoginLogQuery query = new LoginLogQuery();
        query.username = username;
        query.ipAddress = ipAddress;
        query.result = result;
        query.startDate = startDate;
        query.endDate = endDate;
        query.page = page;
        query.size = size;
        return ApiResponse.ok(loginLogService.page(query, authService.currentUser(session)));
    }

    @GetMapping("/me")
    public ApiResponse<PageResult<LoginLog>> currentUserPage(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            HttpSession session
    ) {
        return ApiResponse.ok(loginLogService.currentUserPage(page, size, authService.currentUser(session)));
    }
}
