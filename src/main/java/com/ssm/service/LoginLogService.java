package com.ssm.service;

import com.ssm.dto.LoginLogQuery;
import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.Employee;
import com.ssm.entity.LoginLog;
import com.ssm.mapper.LoginLogMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class LoginLogService {
    private static final int MAX_TEXT = 300;

    private final LoginLogMapper loginLogMapper;

    public LoginLogService(LoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    public PageResult<LoginLog> page(LoginLogQuery query, SessionUser user) {
        if (!"ADMIN".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权查看登录日志");
        }
        LoginLogQuery safeQuery = normalize(query);
        int page = Math.max(safeQuery.page, 1);
        int size = safeQuery.normalizedSize();
        long total = loginLogMapper.count(safeQuery);
        List<LoginLog> records = loginLogMapper.page(safeQuery, (page - 1) * size, size);
        return new PageResult<>(total, page, size, records);
    }

    public void record(String username, Employee employee, String ipAddress, String result, String detail, boolean kickedOffline) {
        LoginLog log = new LoginLog();
        log.employeeId = employee == null ? null : employee.id;
        log.username = limit(username, 60, "未知账号");
        log.employeeName = employee == null ? null : limit(employee.name, 80, null);
        log.ipAddress = limit(ipAddress, 64, "-");
        log.result = limit(result, 30, "UNKNOWN");
        log.detail = limit(detail, MAX_TEXT, null);
        log.kickedOffline = kickedOffline;
        loginLogMapper.insert(log);
    }

    private LoginLogQuery normalize(LoginLogQuery query) {
        LoginLogQuery safeQuery = query == null ? new LoginLogQuery() : query;
        safeQuery.username = normalizeText(safeQuery.username, 60);
        safeQuery.ipAddress = normalizeText(safeQuery.ipAddress, 64);
        safeQuery.result = normalizeText(safeQuery.result, 30);
        if (safeQuery.startDate != null && safeQuery.endDate != null && safeQuery.startDate.isAfter(safeQuery.endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        safeQuery.page = Math.max(safeQuery.page, 1);
        safeQuery.size = safeQuery.normalizedSize();
        return safeQuery;
    }

    private String normalizeText(String value, int max) {
        if (AuthService.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private String limit(String value, int max, String fallback) {
        if (AuthService.isBlank(value)) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
