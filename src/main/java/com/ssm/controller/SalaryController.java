package com.ssm.controller;

import com.ssm.dto.ApiResponse;
import com.ssm.dto.PageResult;
import com.ssm.dto.SalaryActionRequest;
import com.ssm.dto.SalaryQuery;
import com.ssm.entity.SalaryRecord;
import com.ssm.service.AuthService;
import com.ssm.service.SalaryService;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/salaries")
public class SalaryController {
    private final AuthService authService;
    private final SalaryService salaryService;

    public SalaryController(AuthService authService, SalaryService salaryService) {
        this.authService = authService;
        this.salaryService = salaryService;
    }

    @GetMapping
    public ApiResponse<PageResult<SalaryRecord>> page(
            @RequestParam(name = "employeeName", required = false) String employeeName,
            @RequestParam(name = "changeType", required = false) String changeType,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            HttpSession session
    ) {
        SalaryQuery query = new SalaryQuery();
        query.employeeName = employeeName;
        query.changeType = changeType;
        query.startDate = startDate;
        query.endDate = endDate;
        query.page = page;
        query.size = size;
        return ApiResponse.ok(salaryService.page(query, authService.currentUser(session)));
    }

    @GetMapping("/history/{employeeId}")
    public ApiResponse<List<SalaryRecord>> history(@PathVariable(name = "employeeId") Long employeeId, HttpSession session) {
        return ApiResponse.ok(salaryService.history(employeeId, authService.currentUser(session)));
    }

    @PostMapping("/pay")
    public ApiResponse<SalaryRecord> pay(@RequestBody SalaryActionRequest request, HttpSession session) {
        return ApiResponse.ok(salaryService.pay(request, authService.currentUser(session)));
    }

    @PostMapping("/raise")
    public ApiResponse<SalaryRecord> raise(@RequestBody SalaryActionRequest request, HttpSession session) {
        return ApiResponse.ok(salaryService.raise(request, authService.currentUser(session)));
    }

    @PostMapping("/decrease")
    public ApiResponse<SalaryRecord> decrease(@RequestBody SalaryActionRequest request, HttpSession session) {
        var user = authService.currentUser(session);
        authService.requireCurrentPassword(user, request == null ? null : request.adminPassword);
        return ApiResponse.ok(salaryService.decrease(request, user));
    }

    @PostMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestBody(required = false) SalaryQuery query,
            HttpSession session
    ) {
        var user = authService.currentUser(session);
        authService.requireCurrentPassword(user, query == null ? null : query.adminPassword);
        String encodedFilename = java.net.URLEncoder.encode("薪资记录.csv", java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(salaryService.exportCsvStream(query, user));
    }
}
