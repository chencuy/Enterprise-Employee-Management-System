package com.ssm.dto;

import java.time.LocalDate;

public class AttendanceForceAbsentRequest {
    public Long employeeId;
    public LocalDate attendanceDate;
    public String remark;
    public String adminPassword;
}
