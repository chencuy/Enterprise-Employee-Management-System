package com.ssm.service;

import com.ssm.dto.DashboardStats;
import com.ssm.dto.SessionUser;
import com.ssm.mapper.DashboardMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class DashboardService {
    private final DashboardMapper dashboardMapper;
    private final AuthService authService;
    private final AttendanceService attendanceService;
    private final LeaveStatusService leaveStatusService;

    public DashboardService(DashboardMapper dashboardMapper, AuthService authService, AttendanceService attendanceService, LeaveStatusService leaveStatusService) {
        this.dashboardMapper = dashboardMapper;
        this.authService = authService;
        this.attendanceService = attendanceService;
        this.leaveStatusService = leaveStatusService;
    }

    public DashboardStats adminStats(SessionUser user) {
        authService.requireRole(user, "ADMIN");
        leaveStatusService.refreshAllLeaveStatuses();
        DashboardStats stats = new DashboardStats();
        stats.employeeTotal = dashboardMapper.countEmployees();
        stats.departmentTotal = dashboardMapper.countDepartments();
        stats.resignedTotal = dashboardMapper.countResignedEmployees();
        stats.leaveTotal = dashboardMapper.countLeaveEmployees();
        stats.monthlySalaryExpense = dashboardMapper.sumCurrentMonthSalaryPay();
        if (stats.monthlySalaryExpense == null) {
            stats.monthlySalaryExpense = BigDecimal.ZERO;
        }
        stats.pendingApprovalTotal = dashboardMapper.countPendingLeaves();
        if (!attendanceService.isWorkday(LocalDate.now())) {
            stats.todayAttendanceSigned = 0;
            stats.todayAttendanceMissing = 0;
            return stats;
        }
        stats.todayAttendanceSigned = dashboardMapper.countTodayAttendanceSigned();
        long working = dashboardMapper.countWorkingEmployees();
        stats.todayAttendanceMissing = Math.max(0, working - stats.todayAttendanceSigned);
        return stats;
    }
}
