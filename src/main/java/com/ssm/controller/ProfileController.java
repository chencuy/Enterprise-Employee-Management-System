package com.ssm.controller;

import com.ssm.dto.ApiResponse;
import com.ssm.dto.ChangePasswordRequest;
import com.ssm.dto.SessionUser;
import com.ssm.dto.SetLockPasswordRequest;
import com.ssm.dto.VerifyLockPasswordRequest;
import com.ssm.entity.Employee;
import com.ssm.service.AuthService;
import com.ssm.service.EmployeeService;
import com.ssm.service.MessageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final AuthService authService;
    private final EmployeeService employeeService;
    private final MessageService messageService;

    public ProfileController(AuthService authService, EmployeeService employeeService, MessageService messageService) {
        this.authService = authService;
        this.employeeService = employeeService;
        this.messageService = messageService;
    }

    @GetMapping
    public ApiResponse<Employee> profile(HttpSession session) {
        return ApiResponse.ok(employeeService.profile(authService.currentUser(session)));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Integer> unreadCount(HttpSession session) {
        return ApiResponse.ok(messageService.unreadCount(authService.currentUser(session)));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordRequest request, HttpSession session) {
        SessionUser user = authService.currentUser(session);
        employeeService.changePassword(request.oldPassword, request.newPassword, user);
        return ApiResponse.ok();
    }

    @PostMapping("/lock-password")
    public ApiResponse<Void> setLockPassword(@RequestBody SetLockPasswordRequest request, HttpSession session) {
        SessionUser user = authService.currentUser(session);
        employeeService.setLockPassword(request.lockPassword, user);
        authService.markScreenUnlocked(session);
        return ApiResponse.ok();
    }

    @DeleteMapping("/lock-password")
    public ApiResponse<Void> clearLockPassword(@RequestBody VerifyLockPasswordRequest request, HttpSession session) {
        SessionUser user = authService.currentUser(session);
        employeeService.clearLockPassword(request.lockPassword, user);
        authService.markScreenUnlocked(session);
        return ApiResponse.ok();
    }

    @PostMapping("/verify-lock-password")
    public ApiResponse<Boolean> verifyLockPassword(@RequestBody VerifyLockPasswordRequest request, HttpSession session) {
        SessionUser user = authService.currentUser(session);
        boolean verified = employeeService.verifyLockPassword(request.lockPassword, user);
        authService.markScreenUnlocked(session);
        return ApiResponse.ok(verified);
    }

    @PostMapping("/lock-screen")
    public ApiResponse<Void> lockScreen(HttpSession session) {
        authService.lockScreen(session);
        return ApiResponse.ok();
    }

    @PostMapping("/lock-password-reset-request")
    public ApiResponse<Void> requestLockPasswordReset(HttpSession session) {
        SessionUser user = authService.currentUser(session);
        employeeService.requestLockPasswordReset(user);
        return ApiResponse.ok();
    }

    @PostMapping("/lock-password-auto-clear")
    public ApiResponse<Boolean> autoClearLockPasswords(HttpSession session) {
        SessionUser user = authService.currentUser(session);
        boolean cleared = employeeService.autoClearExpiredLockPassword(user);
        if (cleared) {
            authService.markScreenUnlocked(session);
        }
        return ApiResponse.ok(cleared);
    }
}
