package com.ssm.dto;

public class AttendanceSettingsResponse {
    public String checkInStart;
    public String checkInEnd;
    public String lateAfter;
    public String start;
    public String end;

    public AttendanceSettingsResponse(String checkInStart, String checkInEnd, String lateAfter) {
        this.checkInStart = checkInStart;
        this.checkInEnd = checkInEnd;
        this.lateAfter = lateAfter;
        this.start = checkInStart;
        this.end = checkInEnd;
    }
}
