package com.ssm.controller;

import com.ssm.dto.ApiResponse;
import com.ssm.dto.AdminVerificationRequest;
import com.ssm.dto.EmployeeQuery;
import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.Employee;
import com.ssm.service.AuthService;
import com.ssm.service.EmployeeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {
    private final AuthService authService;
    private final EmployeeService employeeService;

    public EmployeeController(AuthService authService, EmployeeService employeeService) {
        this.authService = authService;
        this.employeeService = employeeService;
    }

    @GetMapping
    public ApiResponse<PageResult<Employee>> page(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "nameMode", required = false, defaultValue = "fuzzy") String nameMode,
            @RequestParam(name = "gender", required = false) String gender,
            @RequestParam(name = "departmentId", required = false) Long departmentId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "minSalary", required = false) BigDecimal minSalary,
            @RequestParam(name = "maxSalary", required = false) BigDecimal maxSalary,
            @RequestParam(name = "hireDateStart", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hireDateStart,
            @RequestParam(name = "hireDateEnd", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hireDateEnd,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            HttpSession session
    ) {
        SessionUser user = authService.currentUser(session);
        EmployeeQuery query = new EmployeeQuery();
        query.name = name;
        query.nameMode = nameMode;
        query.gender = gender;
        query.departmentId = departmentId;
        query.status = status;
        query.minSalary = minSalary;
        query.maxSalary = maxSalary;
        query.hireDateStart = hireDateStart;
        query.hireDateEnd = hireDateEnd;
        query.page = page;
        query.size = size;
        return ApiResponse.ok(employeeService.page(query, user));
    }

    @GetMapping("/options")
    public ApiResponse<List<Employee>> options(HttpSession session) {
        return ApiResponse.ok(employeeService.options(authService.currentUser(session)));
    }

    @GetMapping("/attendance-options")
    public ApiResponse<List<Employee>> attendanceOptions(HttpSession session) {
        return ApiResponse.ok(employeeService.attendanceOptions(authService.currentUser(session)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Employee> detail(@PathVariable(name = "id") Long id, HttpSession session) {
        return ApiResponse.ok(employeeService.findVisibleById(id, authService.currentUser(session)));
    }

    @PostMapping
    public ApiResponse<Employee> create(@RequestBody Employee employee, HttpSession session) {
        return ApiResponse.ok(employeeService.create(employee, authService.currentUser(session)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Employee> update(@PathVariable(name = "id") Long id, @RequestBody Employee employee, HttpSession session) {
        return ApiResponse.ok(employeeService.update(id, employee, authService.currentUser(session)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable(name = "id") Long id,
            @RequestBody(required = false) AdminVerificationRequest request,
            HttpSession session
    ) {
        SessionUser user = authService.currentUser(session);
        authService.requireCurrentPassword(user, request == null ? null : request.adminPassword);
        employeeService.delete(id, user);
        return ApiResponse.ok();
    }

    @PostMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestBody(required = false) EmployeeQuery query,
            HttpSession session
    ) {
        SessionUser user = authService.currentUser(session);
        authService.requireCurrentPassword(user, query == null ? null : query.adminPassword);
        String encodedFilename = java.net.URLEncoder.encode("员工列表.csv", java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(employeeService.exportCsvStream(query, user));
    }

    @DeleteMapping("/{id}/lock-password")
    public ApiResponse<Void> adminClearLockPassword(
            @PathVariable(name = "id") Long id,
            @RequestBody(required = false) AdminVerificationRequest request,
            HttpSession session
    ) {
        SessionUser user = authService.currentUser(session);
        authService.requireCurrentPassword(user, request == null ? null : request.adminPassword);
        employeeService.adminClearLockPassword(id, user);
        return ApiResponse.ok();
    }
}
