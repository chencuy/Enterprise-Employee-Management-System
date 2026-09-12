package com.ssm.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ApprovalRequest {
    public Long id;
    public String type;
    public Long applicantId;
    public Long targetEmployeeId;
    public Long targetDepartmentId;
    public BigDecimal amount;
    public LocalDate startDate;
    public LocalDate endDate;
    public String fileName;
    public String profilePhone;
    public String profileEmail;
    public String profileAvatarPath;
    public String reason;
    public String status;
    public Long reviewerId;
    public String reviewComment;
    public LocalDateTime reviewedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public String applicantName;
    public String applicantRole;
    public Long applicantDepartmentId;
    public String applicantDepartmentName;
    public String targetEmployeeName;
    public String targetDepartmentName;
    public String reviewerName;
}
