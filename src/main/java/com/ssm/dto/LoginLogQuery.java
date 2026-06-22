package com.ssm.dto;

import java.time.LocalDate;

public class LoginLogQuery {
    public String username;
    public String ipAddress;
    public String result;
    public LocalDate startDate;
    public LocalDate endDate;
    public int page = 1;
    public int size = 5;

    public int normalizedSize() {
        return Math.min(Math.max(size, 1), 5);
    }
}
