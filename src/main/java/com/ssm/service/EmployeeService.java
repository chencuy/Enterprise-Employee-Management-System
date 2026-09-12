package com.ssm.service;

import com.ssm.dto.EmployeeQuery;
import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.Department;
import com.ssm.entity.Employee;
import com.ssm.mapper.DepartmentMapper;
import com.ssm.mapper.EmployeeMapper;
import com.ssm.util.CsvExportUtils;
import org.apache.ibatis.session.SqlSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class EmployeeService {
    private static final int MAX_USERNAME_LENGTH = 60;
    private static final int MAX_NAME_LENGTH = 60;
    private static final BigDecimal MAX_SALARY = new BigDecimal("9999999999.99");
    private static final Set<String> ROLES = Set.of("ADMIN", "SUPERVISOR", "EMPLOYEE");
    private static final Set<String> STATUSES = Set.of("WORKING", "RESIGNED", "LEAVE");
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "UNKNOWN");

    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final PasswordService passwordService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final LeaveStatusService leaveStatusService;
    private final SqlSession sqlSession;

    public EmployeeService(
            EmployeeMapper employeeMapper,
            DepartmentMapper departmentMapper,
            PasswordService passwordService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            LeaveStatusService leaveStatusService,
            SqlSession sqlSession
    ) {
        this.employeeMapper = employeeMapper;
        this.departmentMapper = departmentMapper;
        this.passwordService = passwordService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.leaveStatusService = leaveStatusService;
        this.sqlSession = sqlSession;
    }

    public PageResult<Employee> page(EmployeeQuery query, SessionUser user) {
        leaveStatusService.refreshAllLeaveStatuses();
        if ("EMPLOYEE".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "普通员工无权访问员工管理");
        }
        if ("SUPERVISOR".equals(user.role)) {
            if (user.departmentId == null) {
                return new PageResult<>(0, Math.max(query.page, 1), query.normalizedSize(), Collections.emptyList());
            }
            query.departmentId = user.departmentId;
        }
        normalizeQuery(query);
        int page = Math.max(query.page, 1);
        int size = query.normalizedSize();
        long total = employeeMapper.count(query);
        List<Employee> records = employeeMapper.page(query, (page - 1) * size, size);
        return new PageResult<>(total, page, size, records);
    }

    public byte[] exportCsv(EmployeeQuery query, SessionUser user) {
        leaveStatusService.refreshAllLeaveStatuses();
        if (!"ADMIN".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以导出员工列表");
        }
        normalizeQuery(query);
        List<Employee> records = employeeMapper.exportList(query);
        auditLogService.record(user, "员工管理", "导出员工", "employee", null, "员工导出", "导出记录数：" + records.size());
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("姓名,账号,性别,手机,邮箱,部门,角色,状态,入职日期,薪资\n");
        for (Employee e : records) {
            csv.append(csvCell(e.name)).append(',')
                    .append(csvCell(e.username)).append(',')
                    .append(csvCell(genderLabel(e.gender))).append(',')
                    .append(csvCell(e.phone)).append(',')
                    .append(csvCell(e.email)).append(',')
                    .append(csvCell(displayText(e.departmentName))).append(',')
                    .append(csvCell(roleLabel(e.role))).append(',')
                    .append(csvCell(statusLabel(e.status))).append(',')
                    .append(csvCell(e.hireDate == null ? "" : e.hireDate.toString())).append(',')
                    .append(csvCell(e.salary == null ? "0" : e.salary.toPlainString()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public StreamingResponseBody exportCsvStream(EmployeeQuery query, SessionUser user) {
        leaveStatusService.refreshAllLeaveStatuses();
        if (!"ADMIN".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以导出员工列表");
        }
        if (query == null) {
            query = new EmployeeQuery();
        }
        normalizeQuery(query);
        EmployeeQuery safeQuery = query;
        return outputStream -> {
            AtomicLong count = new AtomicLong();
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF');
                writer.write("姓名,账号,性别,手机,邮箱,部门,角色,状态,入职日期,薪资\n");
                sqlSession.select(
                        EmployeeMapper.class.getName() + ".exportList",
                        Map.of("q", safeQuery),
                        context -> {
                            try {
                                Employee e = (Employee) context.getResultObject();
                                writer.write(csvCell(e.name));
                                writer.write(',');
                                writer.write(csvCell(e.username));
                                writer.write(',');
                                writer.write(csvCell(genderLabel(e.gender)));
                                writer.write(',');
                                writer.write(csvCell(e.phone));
                                writer.write(',');
                                writer.write(csvCell(e.email));
                                writer.write(',');
                                writer.write(csvCell(displayText(e.departmentName)));
                                writer.write(',');
                                writer.write(csvCell(roleLabel(e.role)));
                                writer.write(',');
                                writer.write(csvCell(statusLabel(e.status)));
                                writer.write(',');
                                writer.write(csvCell(e.hireDate == null ? "" : e.hireDate.toString()));
                                writer.write(',');
                                writer.write(csvCell(e.salary == null ? "0" : e.salary.toPlainString()));
                                writer.write('\n');
                                count.incrementAndGet();
                            } catch (java.io.IOException exception) {
                                throw new UncheckedIOException(exception);
                            }
                        });
                auditLogService.record(user, "员工管理", "导出员工", "employee", null, "员工导出", "导出记录数：" + count.get());
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        };
    }

    public Employee profile(SessionUser user) {
        leaveStatusService.refreshEmployeeLeaveStatus(user.id);
        Employee employee = employeeMapper.findById(user.id);
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "员工不存在");
        }
        return employee;
    }

    public Employee updateOwnProfile(SessionUser user, String phone, String email, String avatarPath) {
        Employee existing = profile(user);
        String nextPhone = AuthService.isBlank(phone) ? existing.phone : phone.trim();
        String nextEmail = AuthService.isBlank(email) ? existing.email : email.trim();
        String nextAvatarPath = avatarPath == null ? existing.avatarPath : avatarPath;
        if (Objects.equals(nextPhone, existing.phone)
                && Objects.equals(nextEmail, existing.email)
                && Objects.equals(nextAvatarPath, existing.avatarPath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "个人资料没有发生变化");
        }
        if (nextEmail != null && nextEmail.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱不能超过 120 个字符");
        }
        validateProfilePhone(nextPhone);
        validateProfileEmail(nextEmail);
        if (employeeMapper.updateProfile(user.id, nextPhone, nextEmail, nextAvatarPath) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "个人资料已被其他操作更新，请刷新后重试");
        }
        Employee updated = employeeMapper.findById(user.id);
        auditLogService.record(user, "个人设置", "修改个人资料", "employee", user.id, user.name, "管理员直接修改个人资料");
        return updated;
    }

    public List<Employee> options(SessionUser user) {
        leaveStatusService.refreshAllLeaveStatuses();
        if ("ADMIN".equals(user.role)) {
            return employeeMapper.findAllActiveExcept(-1L);
        }
        if ("SUPERVISOR".equals(user.role)) {
            if (user.departmentId == null) {
                return Collections.emptyList();
            }
            return employeeMapper.findDepartmentEmployees(user.departmentId, user.id);
        }
        Employee employee = employeeMapper.findById(user.id);
        return employee == null ? Collections.emptyList() : List.of(employee);
    }

    public List<Employee> attendanceOptions(SessionUser user) {
        leaveStatusService.refreshAllLeaveStatuses();
        if ("ADMIN".equals(user.role)) {
            return employeeMapper.findAllForAttendanceOptions();
        }
        if ("SUPERVISOR".equals(user.role)) {
            if (user.departmentId == null) {
                return Collections.emptyList();
            }
            return employeeMapper.findDepartmentAttendanceOptions(user.departmentId, user.id);
        }
        Employee employee = employeeMapper.findById(user.id);
        return employee == null ? Collections.emptyList() : List.of(employee);
    }

    public Employee findVisibleById(Long id, SessionUser user) {
        leaveStatusService.refreshEmployeeLeaveStatus(id);
        Employee employee = employeeMapper.findById(id);
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "员工不存在");
        }
        if ("ADMIN".equals(user.role) || Objects.equals(employee.id, user.id)) {
            return employee;
        }
        if ("SUPERVISOR".equals(user.role)
                && user.departmentId != null
                && Objects.equals(employee.departmentId, user.departmentId)) {
            return employee;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该员工");
    }

    public Employee create(Employee employee, SessionUser user) {
        if (!"ADMIN".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以注册新员工");
        }
        normalize(employee, null, user, true);
        if (employeeMapper.countByUsername(employee.username, null) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "登录账号已存在");
        }
        employeeMapper.insert(employee);
        Employee created = employeeMapper.findById(employee.id);
        auditLogService.record(user, "员工管理", "新增员工", "employee", created.id, created.name, "账号：" + created.username);
        return created;
    }

    public Employee update(Long id, Employee employee, SessionUser user) {
        ensureManagerRole(user);
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "员工信息不能为空");
        }
        Employee existing = employeeMapper.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "员工不存在");
        }
        ensureCanManage(existing, user);
        employee.id = id;
        normalize(employee, existing, user, false);
        ensureAdminContinuity(existing, employee);
        if (employeeMapper.countByUsername(employee.username, id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "登录账号已存在");
        }
        List<String> changes = buildChangeLines(existing, employee);
        employeeMapper.update(employee);
        Employee updated = employeeMapper.findById(id);
        notifyEmployeeChange(existing, updated, changes, user);
        auditLogService.record(
                user,
                "员工管理",
                "修改员工",
                "employee",
                updated.id,
                updated.name,
                changes.isEmpty() ? "保存员工信息，未检测到字段变化" : String.join("；", changes)
        );
        return updated;
    }

    public void changePassword(String oldPassword, String newPassword, SessionUser user) {
        if (AuthService.isBlank(oldPassword) || AuthService.isBlank(newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入旧密码和新密码");
        }
        Employee employee = employeeMapper.findById(user.id);
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "员工不存在");
        }
        if (!passwordService.matches(oldPassword, employee.password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "旧密码不正确");
        }
        if (oldPassword.equals(newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与旧密码相同");
        }
        validatePassword(newPassword);
        String encoded = passwordService.encode(newPassword);
        employeeMapper.updatePassword(user.id, encoded);
        auditLogService.record(user, "个人中心", "修改密码", "employee", user.id, user.name, "员工自主修改密码");
    }

    public void setLockPassword(String lockPassword, SessionUser user) {
        if (AuthService.isBlank(lockPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入锁屏密码");
        }
        if (lockPassword.length() < 4 || lockPassword.length() > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "锁屏密码长度应为 4-72 位");
        }
        String encoded = passwordService.encode(lockPassword);
        employeeMapper.updateLockPassword(user.id, encoded);
        auditLogService.record(user, "个人中心", "设置锁屏密码", "employee", user.id, user.name, "设置/修改锁屏密码");
    }

    public void clearLockPassword(String lockPassword, SessionUser user) {
        if (AuthService.isBlank(lockPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入锁屏密码");
        }
        Employee employee = employeeMapper.findById(user.id);
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "员工不存在");
        }
        if (employee.lockPassword == null || employee.lockPassword.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未设置锁屏密码");
        }
        if (!passwordService.matches(lockPassword, employee.lockPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "锁屏密码错误");
        }
        employeeMapper.clearLockPassword(user.id);
        auditLogService.record(user, "个人中心", "清除锁屏密码", "employee", user.id, user.name, "验证锁屏密码后清除");
    }

    public void requestLockPasswordReset(SessionUser user) {
        Employee employee = employeeMapper.findById(user.id);
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "员工不存在");
        }
        if (employee.lockPassword == null || employee.lockPassword.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未设置锁屏密码");
        }
        if (employee.lockPasswordResetAt != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已发起重置请求，请等待 7 天后自动解锁");
        }
        employeeMapper.requestLockPasswordReset(user.id);
        auditLogService.record(user, "个人中心", "忘记锁屏密码", "employee", user.id, user.name, "发起 7 天后自动解锁");
    }

    public boolean autoClearExpiredLockPasswords() {
        int cleared = employeeMapper.autoClearExpiredLockPasswords();
        return cleared > 0;
    }

    public boolean autoClearExpiredLockPassword(SessionUser user) {
        int cleared = employeeMapper.autoClearExpiredLockPassword(user.id);
        if (cleared > 0) {
            auditLogService.record(user, "个人中心", "自动清除锁屏密码", "employee", user.id, user.name, "锁屏密码重置等待期已到期");
        }
        return cleared > 0;
    }

    public void adminClearLockPassword(Long employeeId, SessionUser admin) {
        if (!"ADMIN".equals(admin.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以强制解除锁屏密码");
        }
        Employee employee = employeeMapper.findById(employeeId);
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "员工不存在");
        }
        if (employee.lockPassword == null || employee.lockPassword.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该员工未设置锁屏密码");
        }
        employeeMapper.clearLockPassword(employeeId);
        auditLogService.record(admin, "员工管理", "强制解除锁屏密码", "employee", employeeId, employee.name, "管理员强制解除锁屏密码");
    }

    public boolean verifyLockPassword(String lockPassword, SessionUser user) {
        if (AuthService.isBlank(lockPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入锁屏密码");
        }
        Employee employee = employeeMapper.findById(user.id);
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "员工不存在");
        }
        if (employee.lockPassword == null || employee.lockPassword.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未设置锁屏密码");
        }
        if (!passwordService.matches(lockPassword, employee.lockPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "锁屏密码错误");
        }
        return true;
    }

    public void delete(Long id, SessionUser user) {
        ensureManagerRole(user);
        if (Objects.equals(id, user.id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能删除当前登录账号");
        }
        Employee existing = employeeMapper.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "员工不存在");
        }
        ensureCanManage(existing, user);
        ensureAdminCanBeRemoved(existing);
        employeeMapper.delete(id);
        auditLogService.record(user, "员工管理", "删除员工", "employee", existing.id, existing.name, "删除账号：" + existing.username);
    }

    private void ensureManagerRole(SessionUser user) {
        if (!"ADMIN".equals(user.role) && !"SUPERVISOR".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权管理员工");
        }
    }

    private void ensureCanManage(Employee target, SessionUser user) {
        if ("ADMIN".equals(user.role)) {
            return;
        }
        boolean sameDepartment = user.departmentId != null && Objects.equals(target.departmentId, user.departmentId);
        if ("SUPERVISOR".equals(user.role) && sameDepartment && "EMPLOYEE".equals(target.role)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "主管只能管理本部门普通员工");
    }

    private void normalize(Employee employee, Employee existing, SessionUser user, boolean create) {
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "员工信息不能为空");
        }
        if (AuthService.isBlank(employee.name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "姓名不能为空");
        }
        employee.name = employee.name.trim();
        if (employee.name.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "姓名长度不能超过 60");
        }
        if (!"ADMIN".equals(user.role) && existing != null) {
            employee.username = existing.username;
            employee.password = existing.password;
        } else {
            if (AuthService.isBlank(employee.username)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "登录账号不能为空");
            }
            employee.username = employee.username.trim();
            if (employee.username.length() > MAX_USERNAME_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "登录账号长度不能超过 60");
            }
            if (AuthService.isBlank(employee.password)) {
                if (existing == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新建员工必须填写初始密码");
                }
                employee.password = existing.password;
            } else {
                validatePassword(employee.password);
                employee.password = passwordService.encode(employee.password);
            }
        }
        employee.phone = normalizeOptional(employee.phone, 30, "电话");
        employee.email = normalizeOptional(employee.email, 120, "邮箱");
        employee.gender = AuthService.isBlank(employee.gender) ? "UNKNOWN" : employee.gender.trim();
        if (!GENDERS.contains(employee.gender)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "性别参数不正确");
        }
        employee.salary = employee.salary == null ? BigDecimal.ZERO : employee.salary;
        if (employee.salary.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工资不能小于 0");
        }
        if (employee.salary.compareTo(MAX_SALARY) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工资金额过大");
        }
        employee.status = AuthService.isBlank(employee.status) ? "WORKING" : employee.status.trim();
        if (!STATUSES.contains(employee.status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "状态参数不正确");
        }
        employee.hireDate = employee.hireDate == null ? (existing == null ? LocalDate.now() : existing.hireDate) : employee.hireDate;
        if (!"LEAVE".equals(employee.status)) {
            employee.leaveEndDate = null;
        } else if (employee.leaveEndDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "休假状态必须填写休假结束日期");
        }
        if ("SUPERVISOR".equals(user.role)) {
            employee.departmentId = user.departmentId;
            employee.role = "EMPLOYEE";
            employee.salary = existing == null ? BigDecimal.ZERO : existing.salary;
        } else if (AuthService.isBlank(employee.role)) {
            employee.role = "EMPLOYEE";
        } else {
            employee.role = employee.role.trim();
        }
        if (!ROLES.contains(employee.role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色参数不正确");
        }
        if ("ADMIN".equals(employee.role)) {
            employee.departmentId = null;
        }
        if (employee.departmentId != null && departmentMapper.findById(employee.departmentId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门不存在");
        }
        if ("ADMIN".equals(user.role) && "SUPERVISOR".equals(employee.role) && employee.departmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门主管必须绑定部门");
        }
    }

    private void normalizeQuery(EmployeeQuery query) {
        if (query == null) {
            return;
        }
        query.name = normalizeFilterText(query.name);
        query.nameMode = "exact".equals(query.nameMode) ? "exact" : "fuzzy";
        query.gender = normalizeAllowedFilter(query.gender, GENDERS, "性别参数不正确");
        query.status = normalizeAllowedFilter(query.status, STATUSES, "状态参数不正确");
        if (query.minSalary != null && query.minSalary.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最低工资不能小于 0");
        }
        if (query.maxSalary != null && query.maxSalary.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最高工资不能小于 0");
        }
        if (query.minSalary != null && query.maxSalary != null && query.minSalary.compareTo(query.maxSalary) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最低工资不能大于最高工资");
        }
        if (query.hireDateStart != null && query.hireDateEnd != null && query.hireDateStart.isAfter(query.hireDateEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入职开始日期不能晚于结束日期");
        }
    }

    private String normalizeFilterText(String value) {
        if (AuthService.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询条件过长");
        }
        return trimmed;
    }

    private String normalizeAllowedFilter(String value, Set<String> allowed, String message) {
        if (AuthService.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!allowed.contains(trimmed)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return trimmed;
    }

    private String normalizeOptional(String value, int maxLength, String fieldName) {
        if (AuthService.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + "长度不能超过 " + maxLength);
        }
        if ("电话".equals(fieldName) && !trimmed.matches("^1[3-9]\\d{9}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号必须符合国内格式 1[3-9]xxxxxxxxx");
        }
        if ("邮箱".equals(fieldName) && !trimmed.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");
        }
        return trimmed;
    }

    private void validateProfilePhone(String value) {
        if (AuthService.isBlank(value)) return;
        String phone = value.trim();
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号必须符合国内格式 1[3-9]xxxxxxxxx");
        }
    }

    private void validateProfileEmail(String value) {
        if (AuthService.isBlank(value)) return;
        String email = value.trim();
        if (email.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱不能超过 120 个字符");
        }
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");
        }
    }

    private void validatePassword(String password) {
        try {
            passwordService.validateRawPassword(password);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private void ensureAdminContinuity(Employee existing, Employee updated) {
        if (isActiveAdmin(existing) && !isActiveAdmin(updated) && employeeMapper.countActiveAdmins() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能移除系统最后一个有效管理员");
        }
    }

    private void ensureAdminCanBeRemoved(Employee existing) {
        if (isActiveAdmin(existing) && employeeMapper.countActiveAdmins() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能删除系统最后一个有效管理员");
        }
    }

    private boolean isActiveAdmin(Employee employee) {
        return employee != null && "ADMIN".equals(employee.role) && !"RESIGNED".equals(employee.status);
    }

    private List<String> buildChangeLines(Employee before, Employee after) {
        List<String> changes = new ArrayList<>();
        addChange(changes, "姓名", before.name, after.name);
        addChange(changes, "电话", before.phone, after.phone);
        addChange(changes, "邮箱", before.email, after.email);
        addChange(changes, "性别", genderLabel(before.gender), genderLabel(after.gender));
        addChange(changes, "工资", moneyText(before.salary), moneyText(after.salary));
        addChange(changes, "部门", departmentLabel(before.departmentId, before.departmentName), departmentLabel(after.departmentId, after.departmentName));
        addChange(changes, "职位", roleLabel(before.role), roleLabel(after.role));
        addChange(changes, "状态", statusLabel(before.status), statusLabel(after.status));
        addChange(changes, "休假结束日期", dateText(before.leaveEndDate), dateText(after.leaveEndDate));
        addChange(changes, "入职日期", dateText(before.hireDate), dateText(after.hireDate));
        addChange(changes, "登录账号", before.username, after.username);
        if (!Objects.equals(before.password, after.password)) {
            changes.add("登录密码：已由管理员重置");
        }
        return changes;
    }

    private void notifyEmployeeChange(Employee before, Employee after, List<String> changes, SessionUser user) {
        if (!"ADMIN".equals(user.role) || after == null || changes.isEmpty() || !wasOrIsEmployeeOrSupervisor(before, after)) {
            return;
        }
        StringBuilder content = new StringBuilder();
        content.append("【员工资料变更通知】管理员 ")
                .append(user.name)
                .append(" 已修改您的员工资料。\n变更内容：");
        for (String change : changes) {
            content.append("\n- ").append(change);
        }
        notificationService.sendChangeNotice(user, after, content.toString());
    }

    private boolean wasOrIsEmployeeOrSupervisor(Employee before, Employee after) {
        return isEmployeeOrSupervisor(before) || isEmployeeOrSupervisor(after);
    }

    private boolean isEmployeeOrSupervisor(Employee employee) {
        return employee != null && ("EMPLOYEE".equals(employee.role) || "SUPERVISOR".equals(employee.role));
    }

    private void addChange(List<String> changes, String field, String before, String after) {
        String left = displayText(before);
        String right = displayText(after);
        if (!Objects.equals(left, right)) {
            changes.add(field + "：" + left + " -> " + right);
        }
    }

    private String departmentLabel(Long departmentId, String departmentName) {
        if (departmentId == null) {
            return "总部管理";
        }
        if (!AuthService.isBlank(departmentName)) {
            return departmentName;
        }
        Department department = departmentMapper.findById(departmentId);
        return department == null ? "未分配部门" : department.name;
    }

    private String roleLabel(String role) {
        if ("ADMIN".equals(role)) {
            return "管理员";
        }
        if ("SUPERVISOR".equals(role)) {
            return "部门主管";
        }
        if ("EMPLOYEE".equals(role)) {
            return "普通员工";
        }
        return displayText(role);
    }

    private String statusLabel(String status) {
        if ("WORKING".equals(status)) {
            return "在职";
        }
        if ("RESIGNED".equals(status)) {
            return "离职";
        }
        if ("LEAVE".equals(status)) {
            return "休假";
        }
        return displayText(status);
    }

    private String genderLabel(String gender) {
        if ("MALE".equals(gender)) {
            return "男";
        }
        if ("FEMALE".equals(gender)) {
            return "女";
        }
        if ("UNKNOWN".equals(gender)) {
            return "未填写";
        }
        return displayText(gender);
    }

    private String moneyText(BigDecimal value) {
        return "￥" + (value == null ? BigDecimal.ZERO : value).stripTrailingZeros().toPlainString();
    }

    private String dateText(LocalDate value) {
        return value == null ? "-" : value.toString();
    }

    private String displayText(String value) {
        return AuthService.isBlank(value) ? "-" : value.trim();
    }

    private String csvCell(String value) {
        return CsvExportUtils.cell(value);
    }

}
