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
import com.ssm.service.ApprovalService;
import com.ssm.service.AvatarStorageService;
import com.ssm.dto.ApprovalRequestForm;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final AuthService authService;
    private final EmployeeService employeeService;
    private final MessageService messageService;
    private final ApprovalService approvalService;
    private final AvatarStorageService avatarStorageService;

    public ProfileController(AuthService authService, EmployeeService employeeService, MessageService messageService, ApprovalService approvalService,
                             AvatarStorageService avatarStorageService) {
        this.authService = authService;
        this.employeeService = employeeService;
        this.messageService = messageService;
        this.approvalService = approvalService;
        this.avatarStorageService = avatarStorageService;
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

    @PostMapping(value = "/update-request", consumes = "multipart/form-data")
    public ApiResponse<com.ssm.entity.ApprovalRequest> updateRequest(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) MultipartFile avatar,
            HttpSession session
    ) {
        SessionUser user = authService.currentUser(session);
        String avatarPath = null;
        boolean directUpdate = "ADMIN".equals(user.role);
        if (avatar != null && !avatar.isEmpty()) {
            if (avatar.getSize() > 2 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像不能超过 2MB");
            String type = avatar.getContentType() == null ? "" : avatar.getContentType().toLowerCase();
            if (!("image/png".equals(type) || "image/jpeg".equals(type))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像仅支持 PNG 或 JPG");
            try {
                byte[] head = avatar.getInputStream().readNBytes(8);
                boolean png = head.length >= 8 && head[0] == (byte)0x89 && head[1] == 0x50 && head[2] == 0x4e && head[3] == 0x47;
                boolean jpg = head.length >= 3 && (head[0] & 0xff) == 0xff && (head[1] & 0xff) == 0xd8 && (head[2] & 0xff) == 0xff;
                if (!png && !jpg) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像内容格式无效");
                avatarPath = avatarStorageService.store(avatar, user.id, !directUpdate, png);
            } catch (java.io.IOException ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "头像保存失败");
            }
        }
        if (directUpdate) {
            try {
                employeeService.updateOwnProfile(user, phone, email, avatarPath);
                if (avatarPath != null) {
                    avatarStorageService.cleanupOldActive(user.id, avatarPath);
                }
                return ApiResponse.ok(null);
            } catch (RuntimeException ex) {
                if (avatarPath != null) {
                    avatarStorageService.delete(avatarPath);
                }
                throw ex;
            }
        }
        ApprovalRequestForm form = new ApprovalRequestForm();
        form.type = "PROFILE_UPDATE";
        form.profilePhone = phone;
        form.profileEmail = email;
        form.profileAvatarPath = avatarPath;
        form.reason = "员工申请更新个人资料";
        try {
            return ApiResponse.ok(approvalService.create(form, user));
        } catch (RuntimeException ex) {
            if (avatarPath != null) {
                avatarStorageService.delete(avatarPath);
            }
            throw ex;
        }
    }

    @GetMapping("/avatar/{name}")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> avatar(@org.springframework.web.bind.annotation.PathVariable String name, HttpSession session) {
        SessionUser user = authService.currentUser(session);
        if (name == null || !avatarStorageService.isActivePath(name)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "头像不存在");
        Employee current = employeeService.profile(user);
        if (current.avatarPath == null || !current.avatarPath.equals(name)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "头像不存在");
        Path path = avatarStorageService.resolveActive(name);
        if (path == null || !Files.isReadable(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "头像不存在");
        try {
            String type = name.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
            return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(type)).body(new org.springframework.core.io.FileSystemResource(path));
        } catch (Exception ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "头像不存在"); }
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
