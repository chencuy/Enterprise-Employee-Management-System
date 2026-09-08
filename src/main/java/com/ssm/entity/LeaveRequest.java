package com.ssm.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class LeaveRequest {
    public Long id;
    public Long employeeId;
    public LocalDate startDate;
    public LocalDate endDate;
    public String reason;
    public String status;
    public Long approverId;
    public String reviewComment;
    public LocalDateTime reviewedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public String employeeName;
    public String employeeRole;
    public Long departmentId;
    public String departmentName;
    public String approverName;
}
