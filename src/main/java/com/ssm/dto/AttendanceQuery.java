package com.ssm.dto;

import java.time.LocalDate;

public class AttendanceQuery {
    public Long employeeId;
    public LocalDate startDate;
    public LocalDate endDate;
    public String adminPassword;
    public int page = 1;
    public int size = 5;

    public int normalizedSize() {
        return Math.min(Math.max(size, 1), 5);
    }
}
