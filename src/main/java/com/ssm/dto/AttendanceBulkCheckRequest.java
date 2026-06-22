package com.ssm.dto;

import java.time.LocalDate;
import java.util.List;

public class AttendanceBulkCheckRequest {
    public List<Long> employeeIds;
    public List<Long> departmentIds;
    public LocalDate attendanceDate;
    public String remark;
}
