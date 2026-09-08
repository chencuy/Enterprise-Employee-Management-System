package com.ssm.entity;

import java.time.LocalDateTime;

public class AuditLog {
    public Long id;
    public Long operatorId;
    public String operatorName;
    public String operatorRole;
    public String module;
    public String action;
    public String targetType;
    public Long targetId;
    public String targetName;
    public String detail;
    public LocalDateTime createdAt;
}
