package com.ssm.service;

import com.ssm.dto.AttendanceAnomalyQuery;
import com.ssm.dto.AttendanceBulkCheckRequest;
import com.ssm.dto.AttendanceForceAbsentRequest;
import com.ssm.dto.AttendanceQuery;
import com.ssm.dto.AttendanceSettingsRequest;
import com.ssm.dto.AttendanceSettingsResponse;
import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.AttendanceAnomaly;
import com.ssm.entity.AttendanceRecord;
import com.ssm.entity.AttendanceSettings;
import com.ssm.entity.Employee;
import com.ssm.mapper.AttendanceMapper;
import com.ssm.mapper.AttendanceSettingsMapper;
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
    private static final int MAX_REMARK_LENGTH = 400;
    private static final Set<String> ANOMALY_TYPES = Set.of("LATE", "ABSENT", "UNSIGNED");

    private final AttendanceMapper attendanceMapper;
    private final AttendanceSettingsMapper attendanceSettingsMapper;
    private final EmployeeMapper employeeMapper;
    private final AuditLogService auditLogService;
    private final LeaveStatusService leaveStatusService;
    private final SqlSession sqlSession;
    private final LocalTime defaultLateAfter;
    private final LocalTime defaultCheckInStart;
    private final LocalTime defaultCheckInEnd;
    private final Set<DayOfWeek> workDays;
    private final Set<LocalDate> holidays;
    private final Set<LocalDate> extraWorkdays;

    public AttendanceService(
            AttendanceMapper attendanceMapper,
            AttendanceSettingsMapper attendanceSettingsMapper,
            EmployeeMapper employeeMapper,
            AuditLogService auditLogService,
            LeaveStatusService leaveStatusService,
            SqlSession sqlSession,
            @Value("${app.attendance.late-after:09:00}") String lateAfter,
            @Value("${app.attendance.check-in-start:07:00}") String checkInStart,
            @Value("${app.attendance.check-in-end:10:00}") String checkInEnd,
            @Value("${app.attendance.work-days:MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY}") String workDays,
            @Value("${app.attendance.holidays:}") String holidays,
            @Value("${app.attendance.extra-workdays:}") String extraWorkdays
    ) {
        this.attendanceMapper = attendanceMapper;
        this.attendanceSettingsMapper = attendanceSettingsMapper;
        this.employeeMapper = employeeMapper;
        this.auditLogService = auditLogService;
        this.leaveStatusService = leaveStatusService;
        this.sqlSession = sqlSession;
        this.defaultLateAfter = parseConfigTime("app.attendance.late-after", lateAfter);
        this.defaultCheckInStart = parseConfigTime("app.attendance.check-in-start", checkInStart);
        this.defaultCheckInEnd = parseConfigTime("app.attendance.check-in-end", checkInEnd);
        if (!this.defaultCheckInStart.isBefore(this.defaultCheckInEnd)) {
            throw new IllegalStateException("app.attendance.check-in-start 必须早于 app.attendance.check-in-end");
        }
        if (this.defaultLateAfter.isBefore(this.defaultCheckInStart) || this.defaultLateAfter.isAfter(this.defaultCheckInEnd)) {
            throw new IllegalStateException("app.attendance.late-after 必须位于签到时间范围内");
        }
        this.workDays = parseWorkDays(workDays);
        this.holidays = parseDateSet("app.attendance.holidays", holidays);
        this.extraWorkdays = parseDateSet("app.attendance.extra-workdays", extraWorkdays);
    }

    public PageResult<AttendanceRecord> page(AttendanceQuery query, SessionUser user) {
        leaveStatusService.refreshAllLeaveStatuses();
        AttendanceQuery safeQuery = scopedQuery(query, user);
        int page = Math.max(safeQuery.page, 1);
        int size = safeQuery.normalizedSize();
        long total = attendanceMapper.count(safeQuery);
        List<AttendanceRecord> records = attendanceMapper.page(safeQuery, (page - 1) * size, size);
        return new PageResult<>(total, page, size, records);
    }

    public AttendanceRecord today(SessionUser user) {
        leaveStatusService.refreshEmployeeLeaveStatus(user.id);
        return attendanceMapper.findByEmployeeAndDate(user.id, LocalDate.now());
    }

    public int unreadCount(SessionUser user) {
        leaveStatusService.refreshEmployeeLeaveStatus(user.id);
        if ("ADMIN".equals(user.role)) {
            return 0;
        }
        if (!"WORKING".equals(user.status)) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        if (!isWorkday(today)) {
            return 0;
        }
        return attendanceMapper.countByEmployeeAndDate(user.id, today) > 0 ? 0 : 1;
    }

    public LocalTime checkInStart() {
        return currentSettings().checkInStart();
    }

    public LocalTime checkInEnd() {
        return currentSettings().checkInEnd();
    }

    public AttendanceSettingsResponse settings() {
        return toSettingsResponse(currentSettings());
    }

    @Transactional
    public AttendanceRecord checkIn(SessionUser user) {
        leaveStatusService.refreshEmployeeLeaveStatus(user.id);
        Employee currentEmployee = employeeMapper.findById(user.id);
        if (currentEmployee == null || !"WORKING".equals(currentEmployee.status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "非在职状态无需签到");
        }
        if ("ADMIN".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员无需签到");
        }
        LocalDate today = LocalDate.now();
        if (leaveStatusService.isOnApprovedLeave(user.id, today)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "休假期间无需签到");
        }
        if (!isWorkday(today)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "非工作日无需签到");
        }
        EffectiveAttendanceSettings settings = currentSettings();
        AttendanceRecord existing = attendanceMapper.findByEmployeeAndDate(user.id, today);
        if (existing != null && existing.checkInAt != null) {
            return existing;
        }
        if (existing != null && "FORCE_ABSENT".equals(existing.source)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该日已被管理员强制缺勤");
        }
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        if (now.isBefore(settings.checkInStart()) || now.isAfter(settings.checkInEnd())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "签到时间为 " + settings.checkInStart() + " - " + settings.checkInEnd() + "，当前不在签到时间范围内");
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
        leaveStatusService.refreshAllLeaveStatuses();
        AttendanceAnomalyQuery safeQuery = scopedAnomalyQuery(query, user);
        EffectiveAttendanceSettings settings = currentSettings();
        List<Employee> employees = employeeMapper.findAttendanceCandidates(safeQuery.departmentId, safeQuery.employeeId);
        if (employees.isEmpty()) {
            return new PageResult<>(0, safeQuery.page, safeQuery.normalizedSize(), List.of());
        }
        List<Long> employeeIds = employees.stream().map(employee -> employee.id).toList();
        Map<String, AttendanceRecord> records = attendanceMapper
                .findByEmployeesAndRange(employeeIds, safeQuery.startDate, safeQuery.endDate)
                .stream()
                .collect(Collectors.toMap(record -> attendanceKey(record.employeeId, record.attendanceDate), Function.identity(), (left, right) -> left));
        Set<String> leaveKeys = leaveStatusService.leaveKeysForRange(employeeIds, safeQuery.startDate, safeQuery.endDate);

        List<AttendanceAnomaly> anomalies = new ArrayList<>();
        for (LocalDate date = safeQuery.endDate; !date.isBefore(safeQuery.startDate); date = date.minusDays(1)) {
            for (Employee employee : employees) {
                if (leaveKeys.contains(attendanceKey(employee.id, date)) || isCurrentManualLeave(employee, date)) {
                    continue;
                }
                AttendanceRecord record = records.get(attendanceKey(employee.id, date));
                AttendanceAnomaly anomaly = buildAnomaly(employee, date, record, settings);
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
        leaveStatusService.refreshAllLeaveStatuses();
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
            Set<Long> leaveEmployeeIds = leaveStatusService.findEmployeesOnApprovedLeave(request.employeeIds, attendanceDate);
            for (Long employeeId : request.employeeIds.stream().filter(Objects::nonNull).distinct().toList()) {
                Employee employee = employeeMapper.findById(employeeId);
                if (employee == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选员工不存在");
                }
                if (!"WORKING".equals(employee.status) || "ADMIN".equals(employee.role)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选员工不可设置全勤：" + employee.name);
                }
                if (leaveEmployeeIds.contains(employee.id)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选员工休假当天不可设置全勤：" + employee.name);
                }
                employeeIds.add(employee.id);
            }
        }
        Set<Long> leaveEmployeeIds = leaveStatusService.findEmployeesOnApprovedLeave(employeeIds, attendanceDate);
        employeeIds.removeAll(leaveEmployeeIds);
        if (employeeIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择至少一个在职员工或部门");
        }
        EffectiveAttendanceSettings settings = currentSettings();
        String remark = "管理员一键全勤：" + user.name;
        if (request != null && !AuthService.isBlank(request.remark)) {
            String trimmed = request.remark.trim();
            remark += "；" + limitRemark(trimmed);
        }
        for (Long employeeId : employeeIds) {
            AttendanceRecord record = new AttendanceRecord();
            record.employeeId = employeeId;
            record.attendanceDate = attendanceDate;
            record.checkInAt = attendanceDate.atTime(settings.lateAfter());
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

    @Transactional
    public AttendanceSettingsResponse updateSettings(AttendanceSettingsRequest request, SessionUser user) {
        requireAdmin(user);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写考勤设置");
        }
        LocalTime checkInStart = parseRequestTime("有效签到开始时间", request.checkInStart);
        LocalTime checkInEnd = parseRequestTime("有效签到结束时间", request.checkInEnd);
        LocalTime lateAfter = parseRequestTime("迟到判定时间", request.lateAfter);
        validateSettings(checkInStart, checkInEnd, lateAfter);

        AttendanceSettings settings = new AttendanceSettings();
        settings.id = 1L;
        settings.checkInStart = checkInStart;
        settings.checkInEnd = checkInEnd;
        settings.lateAfter = lateAfter;
        attendanceSettingsMapper.upsert(settings);
        auditLogService.record(
                user,
                "考勤管理",
                "设置签到时间",
                "attendance_settings",
                1L,
                "考勤规则",
                "有效签到时间：" + checkInStart + " - " + checkInEnd + "，迟到阈值：" + lateAfter
        );
        return settings();
    }

    @Transactional
    public AttendanceRecord forceAbsent(AttendanceForceAbsentRequest request, SessionUser user) {
        leaveStatusService.refreshAllLeaveStatuses();
        requireAdmin(user);
        if (request == null || request.employeeId == null || request.attendanceDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择员工和考勤日期");
        }
        if (request.attendanceDate.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能处理未来日期的考勤");
        }
        Employee employee = employeeMapper.findById(request.employeeId);
        if (employee == null || !"WORKING".equals(employee.status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "员工不存在或非在职状态");
        }
        if ("ADMIN".equals(employee.role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "管理员无需签到，不能强制缺勤");
        }
        if (leaveStatusService.isOnApprovedLeave(employee.id, request.attendanceDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "员工休假当天不能强制缺勤");
        }
        AttendanceRecord existing = attendanceMapper.findByEmployeeAndDate(request.employeeId, request.attendanceDate);
        if (existing == null || existing.checkInAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能对已签到员工强制缺勤");
        }

        String remark = "管理员强制缺勤：" + user.name;
        if (!AuthService.isBlank(request.remark)) {
            remark += "；" + limitRemark(request.remark);
        }
        AttendanceRecord record = new AttendanceRecord();
        record.employeeId = request.employeeId;
        record.attendanceDate = request.attendanceDate;
        record.source = "FORCE_ABSENT";
        record.remark = remark;
        attendanceMapper.upsertForceAbsent(record);
        AttendanceRecord saved = attendanceMapper.findByEmployeeAndDate(request.employeeId, request.attendanceDate);
        auditLogService.record(
                user,
                "考勤管理",
                "强制缺勤",
                "attendance",
                saved == null ? null : saved.id,
                employee.name,
                "考勤日期：" + request.attendanceDate + "，员工：" + employee.name
        );
        return saved;
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
            if (employee == null || "ADMIN".equals(employee.role) || "RESIGNED".equals(employee.status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "员工不存在或不可考勤");
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

    private AttendanceAnomaly buildAnomaly(
            Employee employee,
            LocalDate date,
            AttendanceRecord record,
            EffectiveAttendanceSettings settings
    ) {
        if (!isWorkday(date)) {
            return null;
        }
        if (record == null || record.checkInAt == null) {
            AttendanceAnomaly anomaly = baseAnomaly(employee, date, record, settings);
            if (record != null && "FORCE_ABSENT".equals(record.source)) {
                anomaly.type = "ABSENT";
                anomaly.detail = "管理员已强制缺勤";
            } else if (date.equals(LocalDate.now())) {
                anomaly.type = "UNSIGNED";
                anomaly.detail = "今日尚未签到";
            } else {
                anomaly.type = "ABSENT";
                anomaly.detail = "该日期无签到记录";
            }
            return anomaly;
        }
        if (record.checkInAt.toLocalTime().isAfter(settings.lateAfter())) {
            AttendanceAnomaly anomaly = baseAnomaly(employee, date, record, settings);
            anomaly.type = "LATE";
            anomaly.detail = "签到时间晚于 " + settings.lateAfter();
            return anomaly;
        }
        return null;
    }

    private boolean isCurrentManualLeave(Employee employee, LocalDate date) {
        return employee != null
                && "LEAVE".equals(employee.status)
                && date.equals(LocalDate.now())
                && (employee.leaveEndDate == null || !employee.leaveEndDate.isBefore(date));
    }

    private AttendanceAnomaly baseAnomaly(
            Employee employee,
            LocalDate date,
            AttendanceRecord record,
            EffectiveAttendanceSettings settings
    ) {
        AttendanceAnomaly anomaly = new AttendanceAnomaly();
        anomaly.employeeId = employee.id;
        anomaly.employeeName = employee.name;
        anomaly.username = employee.username;
        anomaly.departmentId = employee.departmentId;
        anomaly.departmentName = employee.departmentName;
        anomaly.attendanceDate = date;
        anomaly.checkInAt = record == null ? null : record.checkInAt;
        anomaly.lateAfter = settings.lateAfter();
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

    private LocalTime parseRequestTime(String fieldName, String value) {
        if (AuthService.isBlank(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + "不能为空");
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + "必须是 HH:mm 格式");
        }
    }

    private void validateSettings(LocalTime checkInStart, LocalTime checkInEnd, LocalTime lateAfter) {
        if (!checkInStart.isBefore(checkInEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "有效签到开始时间必须早于结束时间");
        }
        if (lateAfter.isBefore(checkInStart) || lateAfter.isAfter(checkInEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "迟到判定时间必须位于有效签到时间范围内");
        }
    }

    private EffectiveAttendanceSettings currentSettings() {
        AttendanceSettings saved = attendanceSettingsMapper.find();
        if (saved == null || saved.checkInStart == null || saved.checkInEnd == null || saved.lateAfter == null) {
            return defaultSettings();
        }
        if (!saved.checkInStart.isBefore(saved.checkInEnd)
                || saved.lateAfter.isBefore(saved.checkInStart)
                || saved.lateAfter.isAfter(saved.checkInEnd)) {
            return defaultSettings();
        }
        return new EffectiveAttendanceSettings(saved.checkInStart, saved.checkInEnd, saved.lateAfter);
    }

    private EffectiveAttendanceSettings defaultSettings() {
        return new EffectiveAttendanceSettings(defaultCheckInStart, defaultCheckInEnd, defaultLateAfter);
    }

    private AttendanceSettingsResponse toSettingsResponse(EffectiveAttendanceSettings settings) {
        return new AttendanceSettingsResponse(
                settings.checkInStart().toString(),
                settings.checkInEnd().toString(),
                settings.lateAfter().toString()
        );
    }

    private void requireAdmin(SessionUser user) {
        if (!"ADMIN".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以执行该操作");
        }
    }

    private String limitRemark(String value) {
        if (AuthService.isBlank(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_REMARK_LENGTH ? trimmed.substring(0, MAX_REMARK_LENGTH) : trimmed;
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
        if ("FORCE_ABSENT".equals(source)) {
            return "强制缺勤";
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

    private record EffectiveAttendanceSettings(LocalTime checkInStart, LocalTime checkInEnd, LocalTime lateAfter) {
    }
}
