package com.ssm.controller;

import com.ssm.dto.ApiResponse;
import com.ssm.dto.AttendanceAnomalyQuery;
import com.ssm.dto.AttendanceBulkCheckRequest;
import com.ssm.dto.AttendanceForceAbsentRequest;
import com.ssm.dto.AttendanceQuery;
import com.ssm.dto.AttendanceSettingsRequest;
import com.ssm.dto.AttendanceSettingsResponse;
import com.ssm.dto.CheckInRequest;
import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.AttendanceAnomaly;
import com.ssm.entity.AttendanceRecord;
import com.ssm.service.AttendanceService;
import com.ssm.service.AuthService;
import com.ssm.service.CaptchaService;
import com.ssm.service.IpRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {
    private final AuthService authService;
    private final AttendanceService attendanceService;
    private final CaptchaService captchaService;
    private final IpRateLimitService ipRateLimitService;
    private final boolean trustForwardedHeaders;

    public AttendanceController(
            AuthService authService,
            AttendanceService attendanceService,
            CaptchaService captchaService,
            IpRateLimitService ipRateLimitService,
            @Value("${app.security.trust-forwarded-headers:false}") boolean trustForwardedHeaders
    ) {
        this.authService = authService;
        this.attendanceService = attendanceService;
        this.captchaService = captchaService;
        this.ipRateLimitService = ipRateLimitService;
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    @GetMapping
    public ApiResponse<PageResult<AttendanceRecord>> page(
            @RequestParam(name = "employeeId", required = false) Long employeeId,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            HttpSession session
    ) {
        AttendanceQuery query = new AttendanceQuery();
        query.employeeId = employeeId;
        query.startDate = startDate;
        query.endDate = endDate;
        query.page = page;
        query.size = size;
        return ApiResponse.ok(attendanceService.page(query, authService.currentUser(session)));
    }

    @GetMapping("/today")
    public ApiResponse<AttendanceRecord> today(HttpSession session) {
        return ApiResponse.ok(attendanceService.today(authService.currentUser(session)));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Integer> unreadCount(HttpSession session) {
        return ApiResponse.ok(attendanceService.unreadCount(authService.currentUser(session)));
    }

    @GetMapping("/check-in-window")
    public ApiResponse<AttendanceSettingsResponse> checkInWindow() {
        return ApiResponse.ok(attendanceService.settings());
    }

    @GetMapping("/settings")
    public ApiResponse<AttendanceSettingsResponse> settings(HttpSession session) {
        authService.requireRole(authService.currentUser(session), "ADMIN");
        return ApiResponse.ok(attendanceService.settings());
    }

    @PutMapping("/settings")
    public ApiResponse<AttendanceSettingsResponse> updateSettings(
            @RequestBody AttendanceSettingsRequest request,
            HttpSession session
    ) {
        SessionUser user = authService.currentUser(session);
        authService.requireRole(user, "ADMIN");
        authService.requireCurrentPassword(user, request == null ? null : request.adminPassword);
        return ApiResponse.ok(attendanceService.updateSettings(request, user));
    }

    @PostMapping("/force-absent")
    public ApiResponse<AttendanceRecord> forceAbsent(
            @RequestBody AttendanceForceAbsentRequest request,
            HttpSession session
    ) {
        SessionUser user = authService.currentUser(session);
        authService.requireRole(user, "ADMIN");
        authService.requireCurrentPassword(user, request == null ? null : request.adminPassword);
        return ApiResponse.ok(attendanceService.forceAbsent(request, user));
    }

    @PostMapping("/check-in")
    public ApiResponse<AttendanceRecord> checkIn(
            @RequestBody(required = false) CheckInRequest request,
            HttpSession session,
            HttpServletRequest servletRequest
    ) {
        String ip = clientIp(servletRequest);
        ipRateLimitService.assertAllowed(ip);
        try {
            if (request == null || request.captchaToken == null || request.captchaToken.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先完成验证码");
            }
            if (!captchaService.verify(request.captchaToken, request.captchaAnswer)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码错误，请重新输入");
            }
            return ApiResponse.ok(attendanceService.checkIn(authService.currentUser(session)));
        } finally {
            ipRateLimitService.record(ip);
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (!trustForwardedHeaders) {
            return request.getRemoteAddr();
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/anomalies")
    public ApiResponse<PageResult<AttendanceAnomaly>> anomalies(
            @RequestParam(name = "employeeId", required = false) Long employeeId,
            @RequestParam(name = "departmentId", required = false) Long departmentId,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            HttpSession session
    ) {
        AttendanceAnomalyQuery query = new AttendanceAnomalyQuery();
        query.employeeId = employeeId;
        query.departmentId = departmentId;
        query.type = type;
        query.startDate = startDate;
        query.endDate = endDate;
        query.page = page;
        query.size = size;
        return ApiResponse.ok(attendanceService.anomalies(query, authService.currentUser(session)));
    }

    @PostMapping("/bulk-check-in")
    public ApiResponse<Integer> bulkCheckIn(@RequestBody(required = false) AttendanceBulkCheckRequest request, HttpSession session) {
        return ApiResponse.ok(attendanceService.bulkFullAttendance(request, authService.currentUser(session)));
    }

    @PostMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestBody(required = false) AttendanceQuery query,
            HttpSession session
    ) {
        SessionUser user = authService.currentUser(session);
        if ("ADMIN".equals(user.role)) {
            authService.requireCurrentPassword(user, query == null ? null : query.adminPassword);
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("attendance.csv", StandardCharsets.UTF_8)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(attendanceService.exportCsvStream(query, user));
    }
}
