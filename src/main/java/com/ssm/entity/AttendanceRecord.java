package com.ssm.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AttendanceRecord {
    public Long id;
    public Long employeeId;
    public LocalDate attendanceDate;
    public LocalDateTime checkInAt;
    public String source;
    public String originalName;
    public String storedName;
    public String contentType;
    public Long sizeBytes;
    public String remark;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public String employeeName;
    public String username;
    public String role;
    public String departmentName;
}
