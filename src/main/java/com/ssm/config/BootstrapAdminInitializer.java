package com.ssm.config;

import com.ssm.entity.Employee;
import com.ssm.mapper.EmployeeMapper;
import com.ssm.service.AuthService;
import com.ssm.service.PasswordService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final EmployeeMapper employeeMapper;
    private final PasswordService passwordService;
    private final String username;
    private final String password;
    private final String name;

    public BootstrapAdminInitializer(
            EmployeeMapper employeeMapper,
            PasswordService passwordService,
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.password:}") String password,
            @Value("${app.bootstrap-admin.name:系统管理员}") String name
    ) {
        this.employeeMapper = employeeMapper;
        this.passwordService = passwordService;
        this.username = username;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (AuthService.isBlank(username) || AuthService.isBlank(password)) {
            return;
        }
        if (employeeMapper.countByUsername(username.trim(), null) > 0) {
            return;
        }
        passwordService.validateRawPassword(password);
        Employee admin = new Employee();
        admin.username = username.trim();
        admin.password = passwordService.encode(password);
        admin.name = AuthService.isBlank(name) ? "系统管理员" : name.trim();
        admin.gender = "UNKNOWN";
        admin.salary = BigDecimal.ZERO;
        admin.role = "ADMIN";
        admin.status = "WORKING";
        admin.hireDate = LocalDate.now();
        employeeMapper.insert(admin);
    }
}
