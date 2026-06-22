package com.ssm.mapper;

import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

public interface DashboardMapper {
    @Select("select count(1) from employees")
    long countEmployees();

    @Select("select count(1) from departments")
    long countDepartments();

    @Select("select count(1) from employees where status = 'RESIGNED'")
    long countResignedEmployees();

    @Select("select count(1) from employees where status = 'LEAVE'")
    long countLeaveEmployees();

    @Select("""
            select coalesce(sum(amount), 0)
            from salary_records
            where change_type = 'PAY'
              and created_at >= date_format(curdate(), '%Y-%m-01')
              and created_at < date_add(date_format(curdate(), '%Y-%m-01'), interval 1 month)
            """)
    BigDecimal sumCurrentMonthSalaryPay();

    @Select("select count(1) from approval_requests where status = 'PENDING'")
    long countPendingLeaves();

    @Select("select count(1) from employees where status = 'WORKING'")
    long countWorkingEmployees();

    @Select("""
            select count(distinct ar.employee_id)
            from attendance_records ar
            join employees e on ar.employee_id = e.id
            where ar.attendance_date = curdate()
              and e.status = 'WORKING'
            """)
    long countTodayAttendanceSigned();
}
