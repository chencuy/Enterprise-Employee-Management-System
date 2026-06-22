package com.ssm.mapper;

import com.ssm.entity.LeaveRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface LeaveRequestMapper {
    String BASE_SELECT = """
            select lr.*,
                   e.name as employee_name,
                   e.role as employee_role,
                   e.department_id as department_id,
                   d.name as department_name,
                   a.name as approver_name
            from leave_requests lr
            left join employees e on lr.employee_id = e.id
            left join departments d on e.department_id = d.id
            left join employees a on lr.approver_id = a.id
            """;

    @Select("""
            select lr.*,
                   e.name as employee_name,
                   e.role as employee_role,
                   e.department_id as department_id,
                   d.name as department_name,
                   a.name as approver_name
            from leave_requests lr
            left join employees e on lr.employee_id = e.id
            left join departments d on e.department_id = d.id
            left join employees a on lr.approver_id = a.id
            where lr.id = #{id}
            """)
    LeaveRequest findById(@Param("id") Long id);

    @Select("""
            select lr.*,
                   e.name as employee_name,
                   e.role as employee_role,
                   e.department_id as department_id,
                   d.name as department_name,
                   a.name as approver_name
            from leave_requests lr
            left join employees e on lr.employee_id = e.id
            left join departments d on e.department_id = d.id
            left join employees a on lr.approver_id = a.id
            order by lr.created_at desc, lr.id desc
            limit #{size} offset #{offset}
            """)
    List<LeaveRequest> pageAll(@Param("offset") int offset, @Param("size") int size);

    @Select("select count(1) from leave_requests")
    long countAll();

    @Select("select count(1) from leave_requests where status = 'PENDING'")
    long countPendingAll();

    @Select("""
            select lr.*,
                   e.name as employee_name,
                   e.role as employee_role,
                   e.department_id as department_id,
                   d.name as department_name,
                   a.name as approver_name
            from leave_requests lr
            left join employees e on lr.employee_id = e.id
            left join departments d on e.department_id = d.id
            left join employees a on lr.approver_id = a.id
            where lr.employee_id = #{employeeId}
            order by lr.created_at desc, lr.id desc
            limit #{size} offset #{offset}
            """)
    List<LeaveRequest> pageForEmployee(@Param("employeeId") Long employeeId, @Param("offset") int offset, @Param("size") int size);

    @Select("select count(1) from leave_requests where employee_id = #{employeeId}")
    long countForEmployee(@Param("employeeId") Long employeeId);

    @Select("select count(1) from leave_requests where employee_id = #{employeeId} and status = 'PENDING'")
    long countPendingForEmployee(@Param("employeeId") Long employeeId);

    @Select("""
            select lr.*,
                   e.name as employee_name,
                   e.role as employee_role,
                   e.department_id as department_id,
                   d.name as department_name,
                   a.name as approver_name
            from leave_requests lr
            left join employees e on lr.employee_id = e.id
            left join departments d on e.department_id = d.id
            left join employees a on lr.approver_id = a.id
            where e.department_id = #{departmentId}
            order by lr.created_at desc, lr.id desc
            limit #{size} offset #{offset}
            """)
    List<LeaveRequest> pageForDepartment(@Param("departmentId") Long departmentId, @Param("offset") int offset, @Param("size") int size);

    @Select("""
            select count(1)
            from leave_requests lr
            join employees e on lr.employee_id = e.id
            where e.department_id = #{departmentId}
            """)
    long countForDepartment(@Param("departmentId") Long departmentId);

    @Select("""
            select count(1)
            from leave_requests lr
            join employees e on lr.employee_id = e.id
            where e.department_id = #{departmentId}
              and lr.status = 'PENDING'
              and lr.employee_id <> #{reviewerId}
            """)
    long countPendingForDepartment(@Param("departmentId") Long departmentId, @Param("reviewerId") Long reviewerId);

    @Insert("""
            insert into leave_requests (employee_id, start_date, end_date, reason, status)
            values (#{employeeId}, #{startDate}, #{endDate}, #{reason}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LeaveRequest request);

    @Update("""
            update leave_requests
            set status = #{status},
                approver_id = #{approverId},
                review_comment = #{reviewComment},
                reviewed_at = now(),
                updated_at = now()
            where id = #{id}
            """)
    int review(LeaveRequest request);
}
