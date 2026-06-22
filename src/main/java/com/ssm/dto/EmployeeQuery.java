package com.ssm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class EmployeeQuery {
    public String name;
    public String nameMode;
    public String gender;
    public Long departmentId;
    public String status;
    public BigDecimal minSalary;
    public BigDecimal maxSalary;
    public LocalDate hireDateStart;
    public LocalDate hireDateEnd;
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
