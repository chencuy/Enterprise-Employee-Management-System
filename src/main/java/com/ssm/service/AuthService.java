package com.ssm.service;

import com.ssm.dto.LoginRequest;
import com.ssm.dto.SessionUser;
import com.ssm.entity.Employee;
import com.ssm.mapper.EmployeeMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

@Service
public class AuthService {
    public static final String SESSION_USER = "SESSION_USER";
    public static final String SCREEN_UNLOCKED = "SCREEN_UNLOCKED";

    private final EmployeeMapper employeeMapper;
    private final PasswordService passwordService;
    private final LoginAttemptService loginAttemptService;
    private final CsrfService csrfService;
    private final ActiveSessionService activeSessionService;
    private final LoginLogService loginLogService;
    private final LeaveStatusService leaveStatusService;
    private final boolean trustForwardedHeaders;

    public AuthService(
            EmployeeMapper employeeMapper,
            PasswordService passwordService,
            LoginAttemptService loginAttemptService,
            CsrfService csrfService,
            ActiveSessionService activeSessionService,
            LoginLogService loginLogService,
            LeaveStatusService leaveStatusService,
            @Value("${app.security.trust-forwarded-headers:false}") boolean trustForwardedHeaders
    ) {
        this.employeeMapper = employeeMapper;
        this.passwordService = passwordService;
        this.loginAttemptService = loginAttemptService;
        this.csrfService = csrfService;
        this.activeSessionService = activeSessionService;
        this.loginLogService = loginLogService;
        this.leaveStatusService = leaveStatusService;
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public SessionUser login(LoginRequest request, HttpServletRequest servletRequest) {
        if (request == null || isBlank(request.username) || isBlank(request.password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入账号和密码");
        }
        String username = request.username.trim();
        String ipAddress = clientIp(servletRequest);
        String attemptKey = username.toLowerCase() + "|" + ipAddress;
        try {
            loginAttemptService.assertNotLocked(attemptKey);
        } catch (ResponseStatusException exception) {
            loginLogService.record(username, null, ipAddress, "LOCKED", exception.getReason(), false);
            throw exception;
        }
        Employee employee = employeeMapper.findByUsername(username);
        if (employee != null) {
            leaveStatusService.refreshEmployeeLeaveStatus(employee.id);
            employee = employeeMapper.findByUsername(username);
        }
        if (employee == null || !passwordService.matches(request.password, employee.password)) {
            loginAttemptService.recordFailure(attemptKey);
            loginLogService.record(username, employee, ipAddress, "FAILED", "账号或密码错误", false);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        if ("RESIGNED".equals(employee.status)) {
            loginAttemptService.recordFailure(attemptKey);
            loginLogService.record(username, employee, ipAddress, "RESIGNED", "离职账号不可登录", false);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "离职账号不可登录");
        }
        loginAttemptService.clear(attemptKey);
        HttpSession session = servletRequest.getSession();
        servletRequest.changeSessionId();
        String csrfToken = csrfService.rotateToken(session);
        boolean hasLockPassword = employee.lockPassword != null && !employee.lockPassword.isEmpty();
        session.setAttribute(SCREEN_UNLOCKED, !hasLockPassword);
        SessionUser user = toSessionUser(employee, csrfToken, session);
        session.setAttribute(SESSION_USER, user);
        boolean kickedPrevious = activeSessionService.register(employee.id, session);
        loginLogService.record(
                username,
                employee,
                ipAddress,
                "SUCCESS",
                kickedPrevious ? "登录成功，已挤掉上一设备会话" : "登录成功",
                kickedPrevious
        );
        return user;
    }

    public SessionUser currentUser(HttpSession session) {
        Object value = session.getAttribute(SESSION_USER);
        if (value instanceof SessionUser user) {
            if (!activeSessionService.isCurrent(user.id, session)) {
                session.invalidate();
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号已在其他设备登录，请重新登录");
            }
            leaveStatusService.refreshEmployeeLeaveStatus(user.id);
            Employee employee = employeeMapper.findById(user.id);
            if (employee == null) {
                session.invalidate();
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
            }
            if ("RESIGNED".equals(employee.status)) {
                session.invalidate();
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "离职账号不可登录");
            }
            syncScreenLockFlag(employee, session);
            SessionUser refreshed = toSessionUser(employee, csrfService.ensureToken(session), session);
            session.setAttribute(SESSION_USER, refreshed);
            return refreshed;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    }

    public void logout(HttpSession session) {
        Object value = session.getAttribute(SESSION_USER);
        if (value instanceof SessionUser user) {
            activeSessionService.unregister(user.id, session);
        }
        session.invalidate();
    }

    public void markScreenUnlocked(HttpSession session) {
        session.setAttribute(SCREEN_UNLOCKED, true);
        refreshSessionUser(session);
    }

    public void lockScreen(HttpSession session) {
        SessionUser user = currentUser(session);
        if (!user.hasLockPassword) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未设置锁屏密码");
        }
        session.setAttribute(SCREEN_UNLOCKED, false);
        refreshSessionUser(session);
    }

    public boolean isScreenUnlocked(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute(SCREEN_UNLOCKED));
    }

    public void requireRole(SessionUser user, String role) {
        if (!role.equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权执行此操作");
        }
    }

    public void requireCurrentPassword(SessionUser user, String password) {
        if (isBlank(password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请再次输入当前管理员密码");
        }
        Employee employee = employeeMapper.findById(user.id);
        if (employee == null || !passwordService.matches(password, employee.password)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员密码验证失败");
        }
    }

    public void requireAnyRole(SessionUser user, String... roles) {
        if (Arrays.stream(roles).noneMatch(role -> role.equals(user.role))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权执行此操作");
        }
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String clientIp(HttpServletRequest request) {
        if (!trustForwardedHeaders) {
            return request.getRemoteAddr();
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (!isBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void syncScreenLockFlag(Employee employee, HttpSession session) {
        boolean hasLockPassword = employee.lockPassword != null && !employee.lockPassword.isEmpty();
        if (!hasLockPassword) {
            session.setAttribute(SCREEN_UNLOCKED, true);
        } else if (session.getAttribute(SCREEN_UNLOCKED) == null) {
            session.setAttribute(SCREEN_UNLOCKED, false);
        }
    }

    private void refreshSessionUser(HttpSession session) {
        Object value = session.getAttribute(SESSION_USER);
        if (value instanceof SessionUser user) {
            Employee employee = employeeMapper.findById(user.id);
            if (employee != null) {
                session.setAttribute(SESSION_USER, toSessionUser(employee, csrfService.ensureToken(session), session));
            }
        }
    }

    private SessionUser toSessionUser(Employee employee, String csrfToken, HttpSession session) {
        boolean hasLockPassword = employee.lockPassword != null && !employee.lockPassword.isEmpty();
        return new SessionUser(
                employee.id,
                employee.name,
                employee.role,
                employee.status,
                employee.departmentId,
                employee.departmentName,
                csrfToken,
                hasLockPassword,
                employee.lockPasswordResetAt == null ? null : employee.lockPasswordResetAt.toString(),
                hasLockPassword && !isScreenUnlocked(session)
        );
    }
}
