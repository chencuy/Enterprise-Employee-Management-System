package com.ssm.dto;

import java.time.LocalDate;

public class SalaryQuery {
    public String employeeName;
    public String changeType;
    public LocalDate startDate;
    public LocalDate endDate;
    public String adminPassword;
    public int page = 1;
    public int size = 5;

    public int offset() {
        return (Math.max(page, 1) - 1) * Math.max(size, 1);
    }

    public int normalizedSize() {
        return Math.min(Math.max(size, 1), 5);
    }
}
