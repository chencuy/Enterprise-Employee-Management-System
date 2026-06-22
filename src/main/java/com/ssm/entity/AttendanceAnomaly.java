package com.ssm.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class AttendanceAnomaly {
    public Long employeeId;
    public String employeeName;
    public String username;
    public Long departmentId;
    public String departmentName;
    public LocalDate attendanceDate;
    public String type;
    public LocalDateTime checkInAt;
    public LocalTime lateAfter;
    public String detail;
}
