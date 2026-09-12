package com.ssm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ApprovalRequestForm {
    public String type;
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
    public String reviewComment;
}
