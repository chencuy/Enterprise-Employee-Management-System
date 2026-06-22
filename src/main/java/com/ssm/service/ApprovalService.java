package com.ssm.service;

import com.ssm.dto.ApprovalRequestForm;
import com.ssm.dto.ApprovalRequestQuery;
import com.ssm.dto.PageResult;
import com.ssm.dto.SalaryActionRequest;
import com.ssm.dto.SessionUser;
import com.ssm.entity.ApprovalRequest;
import com.ssm.entity.Department;
import com.ssm.entity.Employee;
import com.ssm.mapper.ApprovalRequestMapper;
import com.ssm.mapper.DepartmentMapper;
import com.ssm.mapper.EmployeeMapper;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ApprovalService {
    private static final int PAGE_SIZE_LIMIT = 5;
    private static final int MAX_TEXT_LENGTH = 500;
    private static final Set<String> TYPES = Set.of("LEAVE", "TRANSFER", "SALARY_RAISE", "RESIGNATION", "FILE_REQUEST");
    private static final Set<String> SUPERVISOR_REVIEW_TYPES = Set.of("LEAVE", "FILE_REQUEST");

    private final ApprovalRequestMapper approvalRequestMapper;
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final SalaryService salaryService;
    private final AuditLogService auditLogService;
    private final NotificationCenterService notificationCenterService;
    private final SqlSession sqlSession;

    public ApprovalService(
            ApprovalRequestMapper approvalRequestMapper,
            EmployeeMapper employeeMapper,
            DepartmentMapper departmentMapper,
            SalaryService salaryService,
            AuditLogService auditLogService,
            NotificationCenterService notificationCenterService,
            SqlSession sqlSession
    ) {
        this.approvalRequestMapper = approvalRequestMapper;
        this.employeeMapper = employeeMapper;
        this.departmentMapper = departmentMapper;
        this.salaryService = salaryService;
        this.auditLogService = auditLogService;
        this.notificationCenterService = notificationCenterService;
        this.sqlSession = sqlSession;
    }

    public PageResult<ApprovalRequest> page(int page, int size, SessionUser user) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), PAGE_SIZE_LIMIT);
        int offset = (safePage - 1) * safeSize;
        if ("ADMIN".equals(user.role)) {
            return new PageResult<>(approvalRequestMapper.countAll(), safePage, safeSize, approvalRequestMapper.pageAll(offset, safeSize));
        }
        if ("SUPERVISOR".equals(user.role)) {
            if (user.departmentId == null) {
                return new PageResult<>(approvalRequestMapper.countForApplicant(user.id), safePage, safeSize, approvalRequestMapper.pageForApplicant(user.id, offset, safeSize));
            }
            return new PageResult<>(
                    approvalRequestMapper.countForSupervisor(user.departmentId, user.id),
                    safePage,
                    safeSize,
                    approvalRequestMapper.pageForSupervisor(user.departmentId, user.id, offset, safeSize)
            );
        }
        return new PageResult<>(
                approvalRequestMapper.countForApplicant(user.id),
                safePage,
                safeSize,
                approvalRequestMapper.pageForApplicant(user.id, offset, safeSize)
        );
    }

    public long unreadCount(SessionUser user) {
        if ("ADMIN".equals(user.role)) {
            return approvalRequestMapper.countPendingAll();
        }
        if ("SUPERVISOR".equals(user.role)) {
            if (user.departmentId == null) {
                return approvalRequestMapper.countPendingForApplicant(user.id);
            }
            return approvalRequestMapper.countPendingForSupervisor(user.departmentId, user.id);
        }
        return approvalRequestMapper.countPendingForApplicant(user.id);
    }

    public byte[] exportCsv(ApprovalRequestQuery query, SessionUser user) {
        if (!"ADMIN".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以导出审批记录");
        }
        if (query == null) {
            query = new ApprovalRequestQuery();
        }
        if (query.startDate != null && query.endDate != null && query.startDate.isAfter(query.endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        List<ApprovalRequest> records = approvalRequestMapper.exportList(query);
        auditLogService.record(user, "审批中心", "导出审批记录", "approval", null, "审批导出", "导出记录数：" + records.size());
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("类型,申请人,申请时间,状态,审批人,审批时间,审批意见\n");
        for (ApprovalRequest r : records) {
            csv.append(csvCell(approvalLabel(r.type))).append(',')
                    .append(csvCell(r.applicantName)).append(',')
                    .append(csvCell(r.createdAt == null ? "" : r.createdAt.toString().replace('T', ' '))).append(',')
                    .append(csvCell(statusLabel(r.status))).append(',')
                    .append(csvCell(r.reviewerName)).append(',')
                    .append(csvCell(r.reviewedAt == null ? "" : r.reviewedAt.toString().replace('T', ' '))).append(',')
                    .append(csvCell(r.reviewComment))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public StreamingResponseBody exportCsvStream(ApprovalRequestQuery query, SessionUser user) {
        if (!"ADMIN".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以导出审批记录");
        }
        ApprovalRequestQuery safeQuery = query == null ? new ApprovalRequestQuery() : query;
        if (safeQuery.startDate != null && safeQuery.endDate != null && safeQuery.startDate.isAfter(safeQuery.endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        return outputStream -> {
            AtomicLong count = new AtomicLong();
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF');
                writer.write("类型,申请人,申请时间,状态,审批人,审批时间,审批意见\n");
                sqlSession.select(
                        ApprovalRequestMapper.class.getName() + ".exportList",
                        Map.of("q", safeQuery),
                        context -> {
                            try {
                                ApprovalRequest r = (ApprovalRequest) context.getResultObject();
                                writer.write(csvCell(approvalLabel(r.type)));
                                writer.write(',');
                                writer.write(csvCell(r.applicantName));
                                writer.write(',');
                                writer.write(csvCell(r.createdAt == null ? "" : r.createdAt.toString().replace('T', ' ')));
                                writer.write(',');
                                writer.write(csvCell(statusLabel(r.status)));
                                writer.write(',');
                                writer.write(csvCell(r.reviewerName));
                                writer.write(',');
                                writer.write(csvCell(r.reviewedAt == null ? "" : r.reviewedAt.toString().replace('T', ' ')));
                                writer.write(',');
                                writer.write(csvCell(r.reviewComment));
                                writer.write('\n');
                                count.incrementAndGet();
                            } catch (java.io.IOException exception) {
                                throw new UncheckedIOException(exception);
                            }
                        });
                auditLogService.record(user, "审批中心", "导出审批记录", "approval", null, "审批导出", "导出记录数：" + count.get());
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        };
    }

    @Transactional
    public ApprovalRequest create(ApprovalRequestForm form, SessionUser user) {
        if ("ADMIN".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员可直接处理业务，无需提交审批申请");
        }
        ApprovalRequest request = normalizeCreate(form, user);
        approvalRequestMapper.insert(request);
        ApprovalRequest created = approvalRequestMapper.findById(request.id);
        notifyReviewers(created, user);
        auditLogService.record(
                user,
                "审批中心",
                "提交审批",
                "approval",
                created.id,
                created.applicantName,
                approvalLabel(created.type) + "：" + created.reason
        );
        return created;
    }

    @Transactional
    public ApprovalRequest approve(Long id, ApprovalRequestForm form, SessionUser user) {
        ApprovalRequest request = requireVisible(id, user, true);
        ensurePending(request);
        applyApproval(request, user);
        request.status = "APPROVED";
        request.reviewerId = user.id;
        request.reviewComment = normalizeText(form == null ? null : form.reviewComment, false, "审批意见");
        approvalRequestMapper.review(request);
        ApprovalRequest updated = approvalRequestMapper.findById(id);
        notifyApplicant(updated, "审批已通过", "您的" + approvalLabel(updated.type) + "申请已通过。");
        auditLogService.record(user, "审批中心", "审批通过", "approval", updated.id, updated.applicantName, approvalLabel(updated.type));
        return updated;
    }

    @Transactional
    public ApprovalRequest reject(Long id, ApprovalRequestForm form, SessionUser user) {
        ApprovalRequest request = requireVisible(id, user, true);
        ensurePending(request);
        request.status = "REJECTED";
        request.reviewerId = user.id;
        request.reviewComment = normalizeText(form == null ? null : form.reviewComment, false, "审批意见");
        approvalRequestMapper.review(request);
        ApprovalRequest updated = approvalRequestMapper.findById(id);
        notifyApplicant(updated, "审批已驳回", "您的" + approvalLabel(updated.type) + "申请已被驳回。");
        auditLogService.record(user, "审批中心", "审批驳回", "approval", updated.id, updated.applicantName, approvalLabel(updated.type));
        return updated;
    }

    private ApprovalRequest normalizeCreate(ApprovalRequestForm form, SessionUser user) {
        if (form == null || AuthService.isBlank(form.type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择审批类型");
        }
        String type = form.type.trim();
        if (!TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "审批类型不正确");
        }
        ApprovalRequest request = new ApprovalRequest();
        request.type = type;
        request.applicantId = user.id;
        request.targetEmployeeId = user.id;
        request.status = "PENDING";
        request.reason = normalizeText(form.reason, true, "申请原因");

        if ("LEAVE".equals(type)) {
            if (form.startDate == null || form.endDate == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "休假申请必须填写开始日期和结束日期");
            }
            if (form.startDate.isBefore(LocalDate.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "休假开始日期不能早于今天");
            }
            if (form.startDate.isAfter(form.endDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "休假开始日期不能晚于结束日期");
            }
            request.startDate = form.startDate;
            request.endDate = form.endDate;
        }
        if ("TRANSFER".equals(type)) {
            if (form.targetDepartmentId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "调岗申请必须选择目标部门");
            }
            Department department = departmentMapper.findById(form.targetDepartmentId);
            if (department == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标部门不存在");
            }
            request.targetDepartmentId = department.id;
        }
        if ("SALARY_RAISE".equals(type)) {
            if (form.amount == null || form.amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "涨薪申请必须填写有效金额");
            }
            request.amount = form.amount;
        }
        if ("FILE_REQUEST".equals(type)) {
            request.fileName = normalizeShortText(form.fileName, false, "文件名称", 255);
        }
        return request;
    }

    private ApprovalRequest requireVisible(Long id, SessionUser user, boolean forReview) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "审批申请不存在");
        }
        ApprovalRequest request = approvalRequestMapper.findById(id);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "审批申请不存在");
        }
        if ("ADMIN".equals(user.role)) {
            return request;
        }
        if ("SUPERVISOR".equals(user.role)
                && user.departmentId != null
                && Objects.equals(user.departmentId, request.applicantDepartmentId)) {
            if (forReview) {
                ensureSupervisorCanReview(request, user);
            }
            return request;
        }
        if (!forReview && Objects.equals(user.id, request.applicantId)) {
            return request;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权处理该审批申请");
    }

    private void ensureSupervisorCanReview(ApprovalRequest request, SessionUser user) {
        if (Objects.equals(user.id, request.applicantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能审批自己的申请");
        }
        if (!SUPERVISOR_REVIEW_TYPES.contains(request.type)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该审批类型需要管理员处理");
        }
        if (!"EMPLOYEE".equals(request.applicantRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "主管只能审批本部门普通员工的申请");
        }
    }

    private void ensurePending(ApprovalRequest request) {
        if (!"PENDING".equals(request.status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该审批已处理，不能重复操作");
        }
    }

    private void applyApproval(ApprovalRequest request, SessionUser user) {
        Employee applicant = employeeMapper.findById(request.applicantId);
        if (applicant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "申请人不存在");
        }
        if ("LEAVE".equals(request.type)) {
            employeeMapper.updateLeaveState(applicant.id, "LEAVE", request.endDate);
            return;
        }
        if ("TRANSFER".equals(request.type)) {
            if (!"ADMIN".equals(user.role)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "调岗申请需要管理员审批");
            }
            employeeMapper.updateDepartment(applicant.id, request.targetDepartmentId);
            return;
        }
        if ("SALARY_RAISE".equals(request.type)) {
            if (!"ADMIN".equals(user.role)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "涨薪申请需要管理员审批");
            }
            SalaryActionRequest action = new SalaryActionRequest();
            action.employeeId = applicant.id;
            action.amount = request.amount;
            action.remark = "审批中心通过涨薪申请：" + request.reason;
            salaryService.raise(action, user);
            return;
        }
        if ("RESIGNATION".equals(request.type)) {
            if (!"ADMIN".equals(user.role)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "离职申请需要管理员审批");
            }
            employeeMapper.updateLeaveState(applicant.id, "RESIGNED", null);
        }
    }

    private void notifyReviewers(ApprovalRequest request, SessionUser user) {
        String title = "新的审批申请";
        String content = user.name + " 提交了" + approvalLabel(request.type) + "申请。";
        List<Long> adminIds = employeeMapper.findAdmins(user.id).stream().map(employee -> employee.id).toList();
        notificationCenterService.sendToMany(adminIds, "APPROVAL", title, content, "approval", request.id, "approvals");
        Employee applicant = employeeMapper.findById(user.id);
        if (SUPERVISOR_REVIEW_TYPES.contains(request.type)
                && "EMPLOYEE".equals(user.role)
                && applicant != null
                && applicant.managerId != null
                && !Objects.equals(applicant.managerId, user.id)) {
            notificationCenterService.send(applicant.managerId, "APPROVAL", title, content, "approval", request.id, "approvals");
        }
    }

    private void notifyApplicant(ApprovalRequest request, String title, String content) {
        String message = content + (AuthService.isBlank(request.reviewComment) ? "" : "\n审批意见：" + request.reviewComment);
        notificationCenterService.send(request.applicantId, "APPROVAL", title, message, "approval", request.id, "approvals");
    }

    private String normalizeText(String value, boolean required, String field) {
        return normalizeShortText(value, required, field, MAX_TEXT_LENGTH);
    }

    private String normalizeShortText(String value, boolean required, String field, int maxLength) {
        if (AuthService.isBlank(value)) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + "不能为空");
            }
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + "不能超过 " + maxLength + " 字");
        }
        return trimmed;
    }

    private String approvalLabel(String type) {
        if ("LEAVE".equals(type)) {
            return "休假";
        }
        if ("TRANSFER".equals(type)) {
            return "调岗";
        }
        if ("SALARY_RAISE".equals(type)) {
            return "涨薪";
        }
        if ("RESIGNATION".equals(type)) {
            return "离职";
        }
        if ("FILE_REQUEST".equals(type)) {
            return "文件";
        }
        return "审批";
    }

    private String statusLabel(String status) {
        if ("PENDING".equals(status)) return "待审批";
        if ("APPROVED".equals(status)) return "已通过";
        if ("REJECTED".equals(status)) return "已驳回";
        return status == null ? "" : status;
    }

    private String csvCell(String value) {
        return CsvExportUtils.cell(value);
    }
}
