package com.ssm.service;

import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.Department;
import com.ssm.entity.Employee;
import com.ssm.mapper.DepartmentMapper;
import com.ssm.mapper.EmployeeMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
public class DepartmentService {
    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;
    private final AuthService authService;
    private final AuditLogService auditLogService;

    public DepartmentService(
            DepartmentMapper departmentMapper,
            EmployeeMapper employeeMapper,
            AuthService authService,
            AuditLogService auditLogService
    ) {
        this.departmentMapper = departmentMapper;
        this.employeeMapper = employeeMapper;
        this.authService = authService;
        this.auditLogService = auditLogService;
    }

    public List<Department> findAll(SessionUser user) {
        if ("ADMIN".equals(user.role) || "SUPERVISOR".equals(user.role)) {
            return departmentMapper.findAll();
        }
        if (user.departmentId != null) {
            Department department = departmentMapper.findById(user.departmentId);
            return department == null ? List.of() : List.of(department);
        }
        return List.of();
    }

    public PageResult<Department> page(String name, String managerName, int page, int size, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 5);
        int offset = (safePage - 1) * safeSize;
        String safeName = normalizeFilterText(name);
        String safeManagerName = normalizeFilterText(managerName);
        return new PageResult<>(
                departmentMapper.count(safeName, safeManagerName),
                safePage,
                safeSize,
                departmentMapper.page(safeName, safeManagerName, offset, safeSize)
        );
    }

    public Department create(Department department, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        normalize(department);
        validateManager(department);
        departmentMapper.insert(department);
        Department created = departmentMapper.findById(department.id);
        auditLogService.record(user, "部门管理", "新增部门", "department", created.id, created.name, created.description);
        return created;
    }

    public Department update(Long id, Department department, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        if (departmentMapper.findById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "部门不存在");
        }
        department.id = id;
        normalize(department);
        validateManager(department);
        departmentMapper.update(department);
        Department updated = departmentMapper.findById(id);
        auditLogService.record(user, "部门管理", "修改部门", "department", updated.id, updated.name, updated.description);
        return updated;
    }

    public void delete(Long id, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        Department existing = departmentMapper.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "部门不存在");
        }
        if (departmentMapper.countEmployees(id) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门下存在员工，不能删除");
        }
        departmentMapper.delete(id);
        auditLogService.record(user, "部门管理", "删除部门", "department", existing.id, existing.name, "删除部门");
    }

    private void normalize(Department department) {
        if (department == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门信息不能为空");
        }
        if (AuthService.isBlank(department.name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门名称不能为空");
        }
        department.name = department.name.trim();
        if (department.name.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门名称不能超过 80 字");
        }
        if (AuthService.isBlank(department.description)) {
            department.description = null;
            return;
        }
        department.description = department.description.trim();
        if (department.description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门说明不能超过 255 字");
        }
    }

    private void validateManager(Department department) {
        if (department.managerId == null) {
            return;
        }
        Employee manager = employeeMapper.findById(department.managerId);
        if (manager == null || !"SUPERVISOR".equals(manager.role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门主管必须是主管角色员工");
        }
        if (department.id == null && manager.departmentId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门主管已绑定其他部门");
        }
        if (department.id != null && !Objects.equals(manager.departmentId, department.id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门主管必须属于该部门");
        }
    }

    private String normalizeFilterText(String value) {
        if (AuthService.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询条件过长");
        }
        return trimmed;
    }
}
