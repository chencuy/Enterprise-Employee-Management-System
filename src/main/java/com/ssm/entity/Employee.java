package com.ssm.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Employee {
    public Long id;
    public String username;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String password;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String lockPassword;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public LocalDateTime lockPasswordResetAt;
    public String name;
    public String phone;
    public String email;
    public String gender;
    public BigDecimal salary;
    public Long departmentId;
    public String role;
    public String status;
    public LocalDate leaveEndDate;
    public LocalDate hireDate;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public String departmentName;
    public Long managerId;
    public String managerName;
    public Boolean hasLockPassword;
}
