package com.ssm.entity;

import java.time.LocalDateTime;

public class LoginLog {
    public Long id;
    public Long employeeId;
    public String username;
    public String employeeName;
    public String ipAddress;
    public String result;
    public String detail;
    public Boolean kickedOffline;
    public LocalDateTime createdAt;
}
