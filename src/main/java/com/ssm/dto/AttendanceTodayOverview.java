package com.ssm.dto;

import java.time.LocalDate;
import java.util.List;

public class AttendanceTodayOverview {
    public LocalDate date;
    public List<AttendanceTodayPerson> signed;
    public List<AttendanceTodayPerson> unsigned;

    public AttendanceTodayOverview(LocalDate date, List<AttendanceTodayPerson> signed, List<AttendanceTodayPerson> unsigned) {
        this.date = date;
        this.signed = signed;
        this.unsigned = unsigned;
    }
}
