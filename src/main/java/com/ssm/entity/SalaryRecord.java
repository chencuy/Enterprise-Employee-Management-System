package com.ssm.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SalaryRecord {
    public Long id;
    public Long employeeId;
    public BigDecimal amount;
    public String changeType;
    public String remark;
    public Long operatorId;
    public LocalDateTime createdAt;

    public String employeeName;
    public String operatorName;
    public BigDecimal currentSalary;
}
