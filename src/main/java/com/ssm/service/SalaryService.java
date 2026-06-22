package com.ssm.service;

import com.ssm.dto.PageResult;
import com.ssm.dto.SalaryActionRequest;
import com.ssm.dto.SalaryQuery;
import com.ssm.dto.SessionUser;
import com.ssm.entity.Employee;
import com.ssm.entity.SalaryRecord;
import com.ssm.mapper.EmployeeMapper;
import com.ssm.mapper.SalaryRecordMapper;
import com.ssm.util.CsvExportUtils;
import org.apache.ibatis.session.SqlSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SalaryService {
    private static final int MAX_REMARK_LENGTH = 500;
    private static final BigDecimal MAX_SALARY_AMOUNT = new BigDecimal("9999999999.99");
    private static final Set<String> CHANGE_TYPES = Set.of("PAY", "RAISE", "DECREASE");

    private final SalaryRecordMapper salaryRecordMapper;
    private final EmployeeMapper employeeMapper;
    private final AuthService authService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final SqlSession sqlSession;

    public SalaryService(
            SalaryRecordMapper salaryRecordMapper,
            EmployeeMapper employeeMapper,
            AuthService authService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            SqlSession sqlSession
    ) {
        this.salaryRecordMapper = salaryRecordMapper;
        this.employeeMapper = employeeMapper;
        this.authService = authService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.sqlSession = sqlSession;
    }

    public PageResult<SalaryRecord> page(SalaryQuery query, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        normalizeQuery(query);
        int page = Math.max(query.page, 1);
        int size = query.normalizedSize();
        long total = salaryRecordMapper.count(query);
        List<SalaryRecord> records = salaryRecordMapper.page(query, (page - 1) * size, size);
        return new PageResult<>(total, page, size, records);
    }

    public byte[] exportCsv(SalaryQuery query, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        normalizeQuery(query);
        List<SalaryRecord> records = salaryRecordMapper.exportList(query);
        auditLogService.record(user, "工资管理", "导出薪资记录", "salary", null, "薪资导出", "导出记录数：" + records.size());
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("员工姓名,变动类型,金额,备注,操作人,操作时间\n");
        for (SalaryRecord r : records) {
            csv.append(csvCell(r.employeeName)).append(',')
                    .append(csvCell(changeTypeLabel(r.changeType))).append(',')
                    .append(csvCell(r.amount == null ? "0" : r.amount.toPlainString())).append(',')
                    .append(csvCell(r.remark)).append(',')
                    .append(csvCell(r.operatorName)).append(',')
                    .append(csvCell(r.createdAt == null ? "" : r.createdAt.toString().replace('T', ' ')))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public StreamingResponseBody exportCsvStream(SalaryQuery query, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        if (query == null) {
            query = new SalaryQuery();
        }
        normalizeQuery(query);
        SalaryQuery safeQuery = query;
        return outputStream -> {
            AtomicLong count = new AtomicLong();
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF');
                writer.write("员工姓名,变动类型,金额,备注,操作人,操作时间\n");
                sqlSession.select(
                        SalaryRecordMapper.class.getName() + ".exportList",
                        Map.of("q", safeQuery),
                        context -> {
                            try {
                                SalaryRecord r = (SalaryRecord) context.getResultObject();
                                writer.write(csvCell(r.employeeName));
                                writer.write(',');
                                writer.write(csvCell(changeTypeLabel(r.changeType)));
                                writer.write(',');
                                writer.write(csvCell(r.amount == null ? "0" : r.amount.toPlainString()));
                                writer.write(',');
                                writer.write(csvCell(r.remark));
                                writer.write(',');
                                writer.write(csvCell(r.operatorName));
                                writer.write(',');
                                writer.write(csvCell(r.createdAt == null ? "" : r.createdAt.toString().replace('T', ' ')));
                                writer.write('\n');
                                count.incrementAndGet();
                            } catch (java.io.IOException exception) {
                                throw new UncheckedIOException(exception);
                            }
                        });
                auditLogService.record(user, "工资管理", "导出薪资记录", "salary", null, "薪资导出", "导出记录数：" + count.get());
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        };
    }

    public List<SalaryRecord> history(Long employeeId, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        return salaryRecordMapper.history(employeeId);
    }

    @Transactional
    public SalaryRecord pay(SalaryActionRequest request, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择员工");
        }
        Employee employee = requireEmployee(request.employeeId);
        SalaryRecord record = new SalaryRecord();
        record.employeeId = employee.id;
        record.amount = employee.salary;
        record.changeType = "PAY";
        record.remark = AuthService.isBlank(request.remark) ? "工资发放，自动填充当前工资" : normalizeRemark(request.remark);
        record.operatorId = user.id;
        salaryRecordMapper.insert(record);
        auditLogService.record(
                user,
                "工资管理",
                "发放工资",
                "employee",
                employee.id,
                employee.name,
                "发放金额：" + formatAmount(record.amount)
        );
        return record;
    }

    @Transactional
    public SalaryRecord raise(SalaryActionRequest request, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择员工并填写金额");
        }
        BigDecimal amount = requirePositiveAmount(request.amount);
        Employee employee = requireEmployee(request.employeeId);
        BigDecimal before = employee.salary == null ? BigDecimal.ZERO : employee.salary;
        BigDecimal after = before.add(amount);
        ensureSalaryLimit(after);
        employeeMapper.updateSalary(employee.id, after);
        SalaryRecord record = insertChange(employee, amount, "RAISE", request.remark, user, before, after);
        notifySalaryChange(employee, user, "涨薪", amount, before, after, record.remark);
        auditLogService.record(
                user,
                "工资管理",
                "涨薪",
                "employee",
                employee.id,
                employee.name,
                "调整金额：" + formatAmount(amount) + "，调整后：" + formatAmount(after)
        );
        return record;
    }

    @Transactional
    public SalaryRecord decrease(SalaryActionRequest request, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择员工并填写金额");
        }
        BigDecimal amount = requirePositiveAmount(request.amount);
        Employee employee = requireEmployee(request.employeeId);
        BigDecimal before = employee.salary == null ? BigDecimal.ZERO : employee.salary;
        BigDecimal after = before.subtract(amount);
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "降薪后工资不能小于 0");
        }
        ensureSalaryLimit(after);
        employeeMapper.updateSalary(employee.id, after);
        SalaryRecord record = insertChange(employee, amount, "DECREASE", request.remark, user, before, after);
        notifySalaryChange(employee, user, "降薪", amount, before, after, record.remark);
        auditLogService.record(
                user,
                "工资管理",
                "降薪",
                "employee",
                employee.id,
                employee.name,
                "调整金额：" + formatAmount(amount) + "，调整后：" + formatAmount(after)
        );
        return record;
    }

    private SalaryRecord insertChange(Employee employee, BigDecimal amount, String type, String remark, SessionUser user, BigDecimal before, BigDecimal after) {
        SalaryRecord record = new SalaryRecord();
        record.employeeId = employee.id;
        record.amount = amount;
        record.changeType = type;
        String defaultRemark = "工资从 " + before + " 调整为 " + after;
        String normalizedRemark = normalizeRemark(remark);
        record.remark = AuthService.isBlank(normalizedRemark) ? defaultRemark : normalizeRemark(normalizedRemark + "；" + defaultRemark);
        record.operatorId = user.id;
        salaryRecordMapper.insert(record);
        return record;
    }

    private Employee requireEmployee(Long employeeId) {
        if (employeeId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择员工");
        }
        Employee employee = employeeMapper.findById(employeeId);
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "员工不存在");
        }
        return employee;
    }

    private BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "金额必须大于 0");
        }
        if (amount.compareTo(MAX_SALARY_AMOUNT) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "金额过大");
        }
        return amount;
    }

    private void ensureSalaryLimit(BigDecimal salary) {
        if (salary.compareTo(MAX_SALARY_AMOUNT) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工资金额过大");
        }
    }

    private void normalizeQuery(SalaryQuery query) {
        if (query == null) {
            return;
        }
        query.employeeName = normalizeFilterText(query.employeeName);
        if (!AuthService.isBlank(query.changeType)) {
            query.changeType = query.changeType.trim();
            if (!CHANGE_TYPES.contains(query.changeType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工资变动类型不正确");
            }
        } else {
            query.changeType = null;
        }
        if (query.startDate != null && query.endDate != null && query.startDate.isAfter(query.endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始日期不能晚于结束日期");
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

    private String normalizeRemark(String remark) {
        if (AuthService.isBlank(remark)) {
            return null;
        }
        String trimmed = remark.trim();
        if (trimmed.length() > MAX_REMARK_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "备注不能超过 500 字");
        }
        return trimmed;
    }

    private void notifySalaryChange(Employee employee, SessionUser user, String action, BigDecimal amount, BigDecimal before, BigDecimal after, String remark) {
        if (!isEmployeeOrSupervisor(employee)) {
            return;
        }
        StringBuilder content = new StringBuilder();
        content.append("【工资变动通知】管理员 ")
                .append(user.name)
                .append(" 已对您的工资进行")
                .append(action)
                .append("操作。\n");
        content.append("变动类型：").append(action).append("\n");
        content.append("变动金额：").append(formatAmount(amount)).append("\n");
        content.append("调整前工资：").append(formatAmount(before)).append("\n");
        content.append("调整后工资：").append(formatAmount(after));
        if (!AuthService.isBlank(remark)) {
            content.append("\n备注：").append(remark);
        }
        notificationService.sendChangeNotice(user, employee, content.toString(), "SALARY", "工资变动通知");
    }

    private boolean isEmployeeOrSupervisor(Employee employee) {
        return employee != null && ("EMPLOYEE".equals(employee.role) || "SUPERVISOR".equals(employee.role));
    }

    private String formatAmount(BigDecimal value) {
        return "￥" + (value == null ? BigDecimal.ZERO : value).stripTrailingZeros().toPlainString();
    }

    private String changeTypeLabel(String type) {
        if ("PAY".equals(type)) return "工资发放";
        if ("RAISE".equals(type)) return "薪资调升";
        if ("DECREASE".equals(type)) return "薪资调降";
        return type == null ? "" : type;
    }

    private String csvCell(String value) {
        return CsvExportUtils.cell(value);
    }
}
