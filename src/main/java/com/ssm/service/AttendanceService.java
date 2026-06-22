package com.ssm.service;

import com.ssm.dto.AttendanceAnomalyQuery;
import com.ssm.dto.AttendanceBulkCheckRequest;
import com.ssm.dto.AttendanceQuery;
import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.AttendanceAnomaly;
import com.ssm.entity.AttendanceRecord;
import com.ssm.entity.Employee;
import com.ssm.mapper.AttendanceMapper;
import com.ssm.mapper.EmployeeMapper;
import com.ssm.util.CsvExportUtils;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AttendanceService {
    private static final int MAX_ANOMALY_DAYS = 31;
    private static final Set<String> ANOMALY_TYPES = Set.of("LATE", "ABSENT", "UNSIGNED");

    private final AttendanceMapper attendanceMapper;
    private final EmployeeMapper employeeMapper;
    private final AuditLogService auditLogService;
    private final SqlSession sqlSession;
    private final LocalTime lateAfter;
    private final LocalTime checkInStart;
    private final LocalTime checkInEnd;
    private final Set<DayOfWeek> workDays;
    private final Set<LocalDate> holidays;
    private final Set<LocalDate> extraWorkdays;

    public AttendanceService(
            AttendanceMapper attendanceMapper,
            EmployeeMapper employeeMapper,
            AuditLogService auditLogService,
            SqlSession sqlSession,
            @Value("${app.attendance.late-after:09:00}") String lateAfter,
            @Value("${app.attendance.check-in-start:07:00}") String checkInStart,
            @Value("${app.attendance.check-in-end:10:00}") String checkInEnd,
            @Value("${app.attendance.work-days:MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY}") String workDays,
            @Value("${app.attendance.holidays:}") String holidays,
            @Value("${app.attendance.extra-workdays:}") String extraWorkdays
    ) {
        this.attendanceMapper = attendanceMapper;
        this.employeeMapper = employeeMapper;
        this.auditLogService = auditLogService;
        this.sqlSession = sqlSession;
        this.lateAfter = parseConfigTime("app.attendance.late-after", lateAfter);
        this.checkInStart = parseConfigTime("app.attendance.check-in-start", checkInStart);
        this.checkInEnd = parseConfigTime("app.attendance.check-in-end", checkInEnd);
        if (!this.checkInStart.isBefore(this.checkInEnd)) {
            throw new IllegalStateException("app.attendance.check-in-start 必须早于 app.attendance.check-in-end");
        }
        if (this.lateAfter.isBefore(this.checkInStart) || this.lateAfter.isAfter(this.checkInEnd)) {
            throw new IllegalStateException("app.attendance.late-after 必须位于签到时间范围内");
        }
        this.workDays = parseWorkDays(workDays);
        this.holidays = parseDateSet("app.attendance.holidays", holidays);
        this.extraWorkdays = parseDateSet("app.attendance.extra-workdays", extraWorkdays);
    }

    public PageResult<AttendanceRecord> page(AttendanceQuery query, SessionUser user) {
        AttendanceQuery safeQuery = scopedQuery(query, user);
        int page = Math.max(safeQuery.page, 1);
        int size = safeQuery.normalizedSize();
        long total = attendanceMapper.count(safeQuery);
        List<AttendanceRecord> records = attendanceMapper.page(safeQuery, (page - 1) * size, size);
        return new PageResult<>(total, page, size, records);
    }

    public AttendanceRecord today(SessionUser user) {
        return attendanceMapper.findByEmployeeAndDate(user.id, LocalDate.now());
    }

    public int unreadCount(SessionUser user) {
        LocalDate today = LocalDate.now();
        if (!isWorkday(today)) {
            return 0;
        }
        return attendanceMapper.countByEmployeeAndDate(user.id, today) > 0 ? 0 : 1;
    }

    public LocalTime checkInStart() {
        return checkInStart;
    }

    public LocalTime checkInEnd() {
        return checkInEnd;
    }

    @Transactional
    public AttendanceRecord checkIn(SessionUser user) {
        LocalDate today = LocalDate.now();
        if (!isWorkday(today)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "非工作日无需签到");
        }
        AttendanceRecord existing = attendanceMapper.findByEmployeeAndDate(user.id, today);
        if (existing != null && existing.checkInAt != null) {
            return existing;
        }
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        if (now.isBefore(checkInStart) || now.isAfter(checkInEnd)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "签到时间为 " + checkInStart + " - " + checkInEnd + "，当前不在签到时间范围内");
        }
        AttendanceRecord record = new AttendanceRecord();
        record.employeeId = user.id;
        record.attendanceDate = today;
        record.checkInAt = LocalDateTime.now();
        record.source = "SIGN_IN";
        record.remark = "系统签到";
        attendanceMapper.upsertCheckIn(record);
        AttendanceRecord saved = attendanceMapper.findByEmployeeAndDate(user.id, today);
        auditLogService.record(user, "考勤管理", "签到", "attendance", saved.id, user.name, "签到日期：" + today);
        return saved;
    }

    public PageResult<AttendanceAnomaly> anomalies(AttendanceAnomalyQuery query, SessionUser user) {
        AttendanceAnomalyQuery safeQuery = scopedAnomalyQuery(query, user);
        List<Employee> employees = employeeMapper.findAttendanceCandidates(safeQuery.departmentId, safeQuery.employeeId);
        if (employees.isEmpty()) {
            return new PageResult<>(0, safeQuery.page, safeQuery.normalizedSize(), List.of());
        }
        List<Long> employeeIds = employees.stream().map(employee -> employee.id).toList();
        Map<String, AttendanceRecord> records = attendanceMapper
                .findByEmployeesAndRange(employeeIds, safeQuery.startDate, safeQuery.endDate)
                .stream()
                .collect(Collectors.toMap(record -> attendanceKey(record.employeeId, record.attendanceDate), Function.identity(), (left, right) -> left));

        List<AttendanceAnomaly> anomalies = new ArrayList<>();
        for (LocalDate date = safeQuery.endDate; !date.isBefore(safeQuery.startDate); date = date.minusDays(1)) {
            for (Employee employee : employees) {
                AttendanceRecord record = records.get(attendanceKey(employee.id, date));
                AttendanceAnomaly anomaly = buildAnomaly(employee, date, record);
                if (anomaly != null && (safeQuery.type == null || safeQuery.type.equals(anomaly.type))) {
                    anomalies.add(anomaly);
                }
            }
        }
        int page = Math.max(safeQuery.page, 1);
        int size = safeQuery.normalizedSize();
        int from = Math.min((page - 1) * size, anomalies.size());
        int to = Math.min(from + size, anomalies.size());
        return new PageResult<>(anomalies.size(), page, size, anomalies.subList(from, to));
    }

    @Transactional
    public int bulkFullAttendance(AttendanceBulkCheckRequest request, SessionUser user) {
        if (!"ADMIN".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以执行一键全勤");
        }
        LocalDate attendanceDate = request == null || request.attendanceDate == null ? LocalDate.now() : request.attendanceDate;
        if (attendanceDate.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能给未来日期设置全勤");
        }
        if (!isWorkday(attendanceDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该日期不是工作日，无需设置全勤");
        }
        Set<Long> employeeIds = new LinkedHashSet<>();
        if (request != null && request.departmentIds != null && !request.departmentIds.isEmpty()) {
            List<Long> departmentIds = request.departmentIds.stream().filter(Objects::nonNull).distinct().toList();
            if (!departmentIds.isEmpty()) {
                employeeIds.addAll(employeeMapper.findWorkingIdsByDepartments(departmentIds));
            }
        }
        if (request != null && request.employeeIds != null) {
            for (Long employeeId : request.employeeIds.stream().filter(Objects::nonNull).distinct().toList()) {
                Employee employee = employeeMapper.findById(employeeId);
                if (employee == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选员工不存在");
                }
                if ("WORKING".equals(employee.status)) {
                    employeeIds.add(employee.id);
                }
            }
        }
        if (employeeIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择至少一个在职员工或部门");
        }
        String remark = "管理员一键全勤：" + user.name;
        if (request != null && !AuthService.isBlank(request.remark)) {
            String trimmed = request.remark.trim();
            remark += "；" + (trimmed.length() > 400 ? trimmed.substring(0, 400) : trimmed);
        }
        for (Long employeeId : employeeIds) {
            AttendanceRecord record = new AttendanceRecord();
            record.employeeId = employeeId;
            record.attendanceDate = attendanceDate;
            record.checkInAt = attendanceDate.atTime(lateAfter);
            record.source = "ADMIN_FILL";
            record.remark = remark;
            attendanceMapper.upsertAdminFill(record);
        }
        auditLogService.record(
                user,
                "考勤管理",
                "一键全勤",
                "attendance",
                null,
                "一键全勤",
                "日期：" + attendanceDate + "，人数：" + employeeIds.size()
        );
        return employeeIds.size();
    }

    public byte[] exportCsv(AttendanceQuery query, SessionUser user) {
        AttendanceQuery safeQuery = scopedQuery(query, user);
        List<AttendanceRecord> records = attendanceMapper.exportList(safeQuery);
        auditLogService.record(
                user,
                "考勤管理",
                "导出考勤",
                "attendance",
                null,
                "考勤导出",
                "导出记录数：" + records.size()
        );
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("姓名,账号,部门,角色,考勤日期,签到时间,来源,备注,记录时间\n");
        for (AttendanceRecord record : records) {
            csv.append(csvCell(record.employeeName)).append(',')
                    .append(csvCell(record.username)).append(',')
                    .append(csvCell(display(record.departmentName, "总部管理"))).append(',')
                    .append(csvCell(roleLabel(record.role))).append(',')
                    .append(csvCell(record.attendanceDate == null ? "" : record.attendanceDate.toString())).append(',')
                    .append(csvCell(record.checkInAt == null ? "" : record.checkInAt.toString().replace('T', ' '))).append(',')
                    .append(csvCell(sourceLabel(record.source))).append(',')
                    .append(csvCell(record.remark)).append(',')
                    .append(csvCell(record.createdAt == null ? "" : record.createdAt.toString().replace('T', ' ')))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public StreamingResponseBody exportCsvStream(AttendanceQuery query, SessionUser user) {
        AttendanceQuery safeQuery = scopedQuery(query, user);
        return outputStream -> {
            AtomicLong count = new AtomicLong();
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF');
                writer.write("姓名,账号,部门,角色,考勤日期,签到时间,来源,备注,记录时间\n");
                sqlSession.select(
                        AttendanceMapper.class.getName() + ".exportList",
                        Map.of("q", safeQuery),
                        context -> {
                            try {
                                AttendanceRecord record = (AttendanceRecord) context.getResultObject();
                                writer.write(csvCell(record.employeeName));
                                writer.write(',');
                                writer.write(csvCell(record.username));
                                writer.write(',');
                                writer.write(csvCell(display(record.departmentName, "总部管理")));
                                writer.write(',');
                                writer.write(csvCell(roleLabel(record.role)));
                                writer.write(',');
                                writer.write(csvCell(record.attendanceDate == null ? "" : record.attendanceDate.toString()));
                                writer.write(',');
                                writer.write(csvCell(record.checkInAt == null ? "" : record.checkInAt.toString().replace('T', ' ')));
                                writer.write(',');
                                writer.write(csvCell(sourceLabel(record.source)));
                                writer.write(',');
                                writer.write(csvCell(record.remark));
                                writer.write(',');
                                writer.write(csvCell(record.createdAt == null ? "" : record.createdAt.toString().replace('T', ' ')));
                                writer.write('\n');
                                count.incrementAndGet();
                            } catch (java.io.IOException exception) {
                                throw new UncheckedIOException(exception);
                            }
                        });
                auditLogService.record(
                        user,
                        "考勤管理",
                        "导出考勤",
                        "attendance",
                        null,
                        "考勤导出",
                        "导出记录数：" + count.get()
                );
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        };
    }

    private AttendanceQuery normalizeQuery(AttendanceQuery query) {
        AttendanceQuery safeQuery = query == null ? new AttendanceQuery() : query;
        if (safeQuery.startDate != null && safeQuery.endDate != null && safeQuery.startDate.isAfter(safeQuery.endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (safeQuery.employeeId != null && employeeMapper.findById(safeQuery.employeeId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "员工不存在");
        }
        safeQuery.page = Math.max(safeQuery.page, 1);
        safeQuery.size = safeQuery.normalizedSize();
        return safeQuery;
    }

    private AttendanceQuery scopedQuery(AttendanceQuery query, SessionUser user) {
        AttendanceQuery safeQuery = query == null ? new AttendanceQuery() : query;
        if ("ADMIN".equals(user.role)) {
            return normalizeQuery(safeQuery);
        }
        if ("EMPLOYEE".equals(user.role)) {
            safeQuery.employeeId = user.id;
            return normalizeQuery(safeQuery);
        }
        if ("SUPERVISOR".equals(user.role)) {
            if (safeQuery.employeeId == null) {
                safeQuery.employeeId = user.id;
            }
            ensureSupervisorCanAccess(safeQuery.employeeId, user);
            return normalizeQuery(safeQuery);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权访问考勤");
    }

    private AttendanceAnomalyQuery scopedAnomalyQuery(AttendanceAnomalyQuery query, SessionUser user) {
        if (!"ADMIN".equals(user.role) && !"SUPERVISOR".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权查看考勤异常");
        }
        AttendanceAnomalyQuery safeQuery = query == null ? new AttendanceAnomalyQuery() : query;
        if ("SUPERVISOR".equals(user.role)) {
            if (user.departmentId == null) {
                return emptyScopedAnomalyQuery(safeQuery);
            }
            if (safeQuery.departmentId != null && !Objects.equals(safeQuery.departmentId, user.departmentId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "主管只能查看本部门考勤异常");
            }
            safeQuery.departmentId = user.departmentId;
        }
        LocalDate today = LocalDate.now();
        if (safeQuery.startDate == null && safeQuery.endDate == null) {
            safeQuery.startDate = today;
            safeQuery.endDate = today;
        } else if (safeQuery.startDate == null) {
            safeQuery.startDate = safeQuery.endDate;
        } else if (safeQuery.endDate == null) {
            safeQuery.endDate = safeQuery.startDate;
        }
        if (safeQuery.startDate.isAfter(safeQuery.endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (safeQuery.endDate.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期不能晚于今天");
        }
        if (Duration.between(safeQuery.startDate.atStartOfDay(), safeQuery.endDate.plusDays(1).atStartOfDay()).toDays() > MAX_ANOMALY_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "异常查询日期范围不能超过 31 天");
        }
        if (!AuthService.isBlank(safeQuery.type)) {
            safeQuery.type = safeQuery.type.trim();
            if (!ANOMALY_TYPES.contains(safeQuery.type)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "考勤异常类型不正确");
            }
        } else {
            safeQuery.type = null;
        }
        if (safeQuery.employeeId != null) {
            Employee employee = employeeMapper.findById(safeQuery.employeeId);
            if (employee == null || !"WORKING".equals(employee.status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "员工不存在或非在职状态");
            }
            if (safeQuery.departmentId != null && !Objects.equals(employee.departmentId, safeQuery.departmentId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该员工考勤异常");
            }
        }
        safeQuery.page = Math.max(safeQuery.page, 1);
        safeQuery.size = safeQuery.normalizedSize();
        return safeQuery;
    }

    private AttendanceAnomalyQuery emptyScopedAnomalyQuery(AttendanceAnomalyQuery query) {
        AttendanceAnomalyQuery safeQuery = query == null ? new AttendanceAnomalyQuery() : query;
        safeQuery.employeeId = -1L;
        safeQuery.departmentId = -1L;
        safeQuery.startDate = LocalDate.now();
        safeQuery.endDate = LocalDate.now();
        safeQuery.page = Math.max(safeQuery.page, 1);
        safeQuery.size = safeQuery.normalizedSize();
        return safeQuery;
    }

    private AttendanceAnomaly buildAnomaly(Employee employee, LocalDate date, AttendanceRecord record) {
        if (!isWorkday(date)) {
            return null;
        }
        if (record == null || record.checkInAt == null) {
            AttendanceAnomaly anomaly = baseAnomaly(employee, date, record);
            if (date.equals(LocalDate.now())) {
                anomaly.type = "UNSIGNED";
                anomaly.detail = "今日尚未签到";
            } else {
                anomaly.type = "ABSENT";
                anomaly.detail = "该日期无签到记录";
            }
            return anomaly;
        }
        if (record.checkInAt.toLocalTime().isAfter(lateAfter)) {
            AttendanceAnomaly anomaly = baseAnomaly(employee, date, record);
            anomaly.type = "LATE";
            anomaly.detail = "签到时间晚于 " + lateAfter;
            return anomaly;
        }
        return null;
    }

    private AttendanceAnomaly baseAnomaly(Employee employee, LocalDate date, AttendanceRecord record) {
        AttendanceAnomaly anomaly = new AttendanceAnomaly();
        anomaly.employeeId = employee.id;
        anomaly.employeeName = employee.name;
        anomaly.username = employee.username;
        anomaly.departmentId = employee.departmentId;
        anomaly.departmentName = employee.departmentName;
        anomaly.attendanceDate = date;
        anomaly.checkInAt = record == null ? null : record.checkInAt;
        anomaly.lateAfter = lateAfter;
        return anomaly;
    }

    private String attendanceKey(Long employeeId, LocalDate date) {
        return employeeId + "|" + date;
    }

    private LocalTime parseConfigTime(String propertyName, String value) {
        try {
            return LocalTime.parse(value);
        } catch (Exception exception) {
            throw new IllegalStateException(propertyName + " 配置必须是 HH:mm 格式", exception);
        }
    }

    private Set<DayOfWeek> parseWorkDays(String value) {
        Set<DayOfWeek> days = new LinkedHashSet<>();
        if (!AuthService.isBlank(value)) {
            for (String item : value.split(",")) {
                String name = item.trim();
                if (!name.isEmpty()) {
                    try {
                        days.add(DayOfWeek.valueOf(name.toUpperCase()));
                    } catch (IllegalArgumentException exception) {
                        throw new IllegalStateException("app.attendance.work-days 包含无效星期：" + name, exception);
                    }
                }
            }
        }
        if (days.isEmpty()) {
            throw new IllegalStateException("app.attendance.work-days 至少要配置一个工作日");
        }
        return Set.copyOf(days);
    }

    private Set<LocalDate> parseDateSet(String propertyName, String value) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        if (!AuthService.isBlank(value)) {
            for (String item : value.split(",")) {
                String text = item.trim();
                if (!text.isEmpty()) {
                    try {
                        dates.add(LocalDate.parse(text));
                    } catch (Exception exception) {
                        throw new IllegalStateException(propertyName + " 包含无效日期：" + text, exception);
                    }
                }
            }
        }
        return Set.copyOf(dates);
    }

    public boolean isWorkday(LocalDate date) {
        if (extraWorkdays.contains(date)) {
            return true;
        }
        if (holidays.contains(date)) {
            return false;
        }
        return workDays.contains(date.getDayOfWeek());
    }

    private void ensureSupervisorCanAccess(Long employeeId, SessionUser user) {
        Employee employee = employeeMapper.findById(employeeId);
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "员工不存在");
        }
        if (Objects.equals(employee.id, user.id)) {
            return;
        }
        if (user.departmentId != null && Objects.equals(employee.departmentId, user.departmentId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "主管只能查看或导出自己及本部门员工的考勤");
    }

    private String csvCell(String value) {
        return CsvExportUtils.cell(value);
    }

    private String display(String value, String fallback) {
        return AuthService.isBlank(value) ? fallback : value.trim();
    }

    private String sourceLabel(String source) {
        if ("SIGN_IN".equals(source)) {
            return "签到";
        }
        if ("IMPORT".equals(source)) {
            return "导入";
        }
        if ("ADMIN_FILL".equals(source)) {
            return "管理员全勤";
        }
        return display(source, "-");
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
        return display(role, "-");
    }
}
