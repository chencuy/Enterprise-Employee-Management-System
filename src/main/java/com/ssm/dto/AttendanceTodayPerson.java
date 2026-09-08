package com.ssm.dto;

import java.time.LocalDateTime;

public class AttendanceTodayPerson {
    public Long employeeId;
    public String employeeName;
    public String username;
    public Long departmentId;
    public String departmentName;
    public LocalDateTime checkInAt;
    public String source;

    public AttendanceTodayPerson(
            Long employeeId,
            String employeeName,
            String username,
            Long departmentId,
            String departmentName,
            LocalDateTime checkInAt,
            String source
    ) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.username = username;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.checkInAt = checkInAt;
        this.source = source;
    }
}
