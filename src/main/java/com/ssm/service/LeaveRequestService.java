package com.ssm.service;

import com.ssm.dto.LeaveRequestForm;
import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.Employee;
import com.ssm.entity.LeaveRequest;
import com.ssm.mapper.EmployeeMapper;
import com.ssm.mapper.LeaveRequestMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Service
public class LeaveRequestService {
    private static final int PAGE_SIZE_LIMIT = 5;
    private static final int MAX_REASON_LENGTH = 500;

    private final LeaveRequestMapper leaveRequestMapper;
    private final EmployeeMapper employeeMapper;
    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public LeaveRequestService(
            LeaveRequestMapper leaveRequestMapper,
            EmployeeMapper employeeMapper,
            AuthService authService,
            AuditLogService auditLogService,
            NotificationService notificationService
    ) {
        this.leaveRequestMapper = leaveRequestMapper;
        this.employeeMapper = employeeMapper;
        this.authService = authService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    public PageResult<LeaveRequest> page(int page, int size, SessionUser user) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), PAGE_SIZE_LIMIT);
        int offset = (safePage - 1) * safeSize;
        if ("ADMIN".equals(user.role)) {
            return new PageResult<>(leaveRequestMapper.countAll(), safePage, safeSize, leaveRequestMapper.pageAll(offset, safeSize));
        }
        if ("SUPERVISOR".equals(user.role)) {
            if (user.departmentId == null) {
                return new PageResult<>(0, safePage, safeSize, List.of());
            }
            return new PageResult<>(
                    leaveRequestMapper.countForDepartment(user.departmentId),
                    safePage,
                    safeSize,
                    leaveRequestMapper.pageForDepartment(user.departmentId, offset, safeSize)
            );
        }
        return new PageResult<>(
                leaveRequestMapper.countForEmployee(user.id),
                safePage,
                safeSize,
                leaveRequestMapper.pageForEmployee(user.id, offset, safeSize)
        );
    }

    public long unreadCount(SessionUser user) {
        if ("ADMIN".equals(user.role)) {
            return leaveRequestMapper.countPendingAll();
        }
        if ("SUPERVISOR".equals(user.role)) {
            if (user.departmentId == null) {
                return 0;
            }
            return leaveRequestMapper.countPendingForDepartment(user.departmentId, user.id);
        }
        return leaveRequestMapper.countPendingForEmployee(user.id);
    }

    @Transactional
    public LeaveRequest create(LeaveRequestForm form, SessionUser user) {
        if ("ADMIN".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员不需要提交休假申请");
        }
        if (form == null || form.startDate == null || form.endDate == null || AuthService.isBlank(form.reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写休假起止日期和申请原因");
        }
        if (form.startDate.isAfter(form.endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "休假开始日期不能晚于结束日期");
        }
        if (form.startDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "休假开始日期不能早于今天");
        }
        String reason = form.reason.trim();
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "申请原因不能超过 500 字");
        }
        LeaveRequest request = new LeaveRequest();
        request.employeeId = user.id;
        request.startDate = form.startDate;
        request.endDate = form.endDate;
        request.reason = reason;
        request.status = "PENDING";
        leaveRequestMapper.insert(request);
        LeaveRequest created = leaveRequestMapper.findById(request.id);
        auditLogService.record(
                user,
                "休假申请",
                "提交",
                "leave_request",
                created.id,
                created.employeeName,
                created.startDate + " 至 " + created.endDate + "，共 " + leaveDays(created) + " 天"
        );
        return created;
    }

    @Transactional
    public LeaveRequest approve(Long id, LeaveRequestForm form, SessionUser user) {
        LeaveRequest request = requireVisibleRequest(id, user, true);
        ensurePending(request);
        Employee employee = employeeMapper.findById(request.employeeId);
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "申请人不存在");
        }
        request.status = "APPROVED";
        request.approverId = user.id;
        request.reviewComment = normalizeComment(form == null ? null : form.reviewComment);
        leaveRequestMapper.review(request);
        employeeMapper.updateLeaveState(request.employeeId, "LEAVE", request.endDate);
        LeaveRequest updated = leaveRequestMapper.findById(id);
        notificationService.sendChangeNotice(user, employee,
                "【休假申请通知】您的休假申请已通过。\n休假时间：" + request.startDate + " 至 " + request.endDate
                        + (AuthService.isBlank(request.reviewComment) ? "" : "\n审批意见：" + request.reviewComment));
        auditLogService.record(
                user,
                "休假申请",
                "审批通过",
                "leave_request",
                updated.id,
                updated.employeeName,
                "休假时间：" + updated.startDate + " 至 " + updated.endDate
        );
        return updated;
    }

    @Transactional
    public LeaveRequest reject(Long id, LeaveRequestForm form, SessionUser user) {
        LeaveRequest request = requireVisibleRequest(id, user, true);
        ensurePending(request);
        Employee employee = employeeMapper.findById(request.employeeId);
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "申请人不存在");
        }
        request.status = "REJECTED";
        request.approverId = user.id;
        request.reviewComment = normalizeComment(form == null ? null : form.reviewComment);
        leaveRequestMapper.review(request);
        LeaveRequest updated = leaveRequestMapper.findById(id);
        notificationService.sendChangeNotice(user, employee,
                "【休假申请通知】您的休假申请已驳回。\n休假时间：" + request.startDate + " 至 " + request.endDate
                        + (AuthService.isBlank(request.reviewComment) ? "" : "\n审批意见：" + request.reviewComment));
        auditLogService.record(
                user,
                "休假申请",
                "驳回",
                "leave_request",
                updated.id,
                updated.employeeName,
                AuthService.isBlank(updated.reviewComment) ? "未填写审批意见" : updated.reviewComment
        );
        return updated;
    }

    private LeaveRequest requireVisibleRequest(Long id, SessionUser user, boolean forReview) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "休假申请不存在");
        }
        LeaveRequest request = leaveRequestMapper.findById(id);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "休假申请不存在");
        }
        if ("ADMIN".equals(user.role)) {
            return request;
        }
        if ("SUPERVISOR".equals(user.role)
                && user.departmentId != null
                && Objects.equals(user.departmentId, request.departmentId)) {
            if (forReview) {
                if (Objects.equals(user.id, request.employeeId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能审批自己的休假申请");
                }
                if (!"EMPLOYEE".equals(request.employeeRole)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "主管只能审批本部门普通员工的休假申请");
                }
            }
            return request;
        }
        if (!forReview && Objects.equals(user.id, request.employeeId)) {
            return request;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权处理该休假申请");
    }

    private void ensurePending(LeaveRequest request) {
        if (!"PENDING".equals(request.status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该申请已处理，不能重复审批");
        }
    }

    private String normalizeComment(String value) {
        if (AuthService.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "审批意见不能超过 500 字");
        }
        return trimmed;
    }

    private long leaveDays(LeaveRequest request) {
        return ChronoUnit.DAYS.between(request.startDate, request.endDate) + 1;
    }
}
