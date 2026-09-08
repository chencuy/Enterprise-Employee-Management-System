package com.ssm.entity;

import java.time.LocalDateTime;

public class Department {
    public Long id;
    public String name;
    public String description;
    public Long managerId;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public String managerName;
    public Integer employeeCount;
}
