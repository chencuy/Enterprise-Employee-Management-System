package com.ssm.controller;

import com.ssm.dto.ApiResponse;
import com.ssm.dto.AdminVerificationRequest;
import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.Department;
import com.ssm.service.AuthService;
import com.ssm.service.DepartmentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
    private final AuthService authService;
    private final DepartmentService departmentService;

    public DepartmentController(AuthService authService, DepartmentService departmentService) {
        this.authService = authService;
        this.departmentService = departmentService;
    }

    @GetMapping
    public ApiResponse<List<Department>> list(HttpSession session) {
        SessionUser user = authService.currentUser(session);
        return ApiResponse.ok(departmentService.findAll(user));
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<Department>> page(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "managerName", required = false) String managerName,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            HttpSession session
    ) {
        SessionUser user = authService.currentUser(session);
        return ApiResponse.ok(departmentService.page(name, managerName, page, size, user));
    }

    @PostMapping
    public ApiResponse<Department> create(@RequestBody Department department, HttpSession session) {
        return ApiResponse.ok(departmentService.create(department, authService.currentUser(session)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Department> update(@PathVariable(name = "id") Long id, @RequestBody Department department, HttpSession session) {
        return ApiResponse.ok(departmentService.update(id, department, authService.currentUser(session)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable(name = "id") Long id,
            @RequestBody(required = false) AdminVerificationRequest request,
            HttpSession session
    ) {
        SessionUser user = authService.currentUser(session);
        authService.requireCurrentPassword(user, request == null ? null : request.adminPassword);
        departmentService.delete(id, user);
        return ApiResponse.ok();
    }
}
