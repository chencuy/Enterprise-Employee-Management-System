package com.ssm.mapper;

import com.ssm.dto.ApprovalRequestQuery;
import com.ssm.entity.ApprovalRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ApprovalRequestMapper {
    String SELECT_COLUMNS = """
            ar.*,
            a.name as applicant_name,
            a.role as applicant_role,
            a.department_id as applicant_department_id,
            ad.name as applicant_department_name,
            t.name as target_employee_name,
            td.name as target_department_name,
            r.name as reviewer_name
            """;

    String FROM_CLAUSE = """
            from approval_requests ar
            left join employees a on ar.applicant_id = a.id
            left join departments ad on a.department_id = ad.id
            left join employees t on ar.target_employee_id = t.id
            left join departments td on ar.target_department_id = td.id
            left join employees r on ar.reviewer_id = r.id
            """;

    @Select("""
            select
            """ + SELECT_COLUMNS + FROM_CLAUSE + """
            where ar.id = #{id}
            """)
    ApprovalRequest findById(@Param("id") Long id);

    @Select("""
            select
            """ + SELECT_COLUMNS + FROM_CLAUSE + """
            order by ar.created_at desc, ar.id desc
            limit #{size} offset #{offset}
            """)
    List<ApprovalRequest> pageAll(@Param("offset") int offset, @Param("size") int size);

    @Select("select count(1) from approval_requests")
    long countAll();

    @Select("select count(1) from approval_requests where status = 'PENDING'")
    long countPendingAll();

    @Select("""
            select
            """ + SELECT_COLUMNS + FROM_CLAUSE + """
            where ar.applicant_id = #{employeeId}
            order by ar.created_at desc, ar.id desc
            limit #{size} offset #{offset}
            """)
    List<ApprovalRequest> pageForApplicant(@Param("employeeId") Long employeeId, @Param("offset") int offset, @Param("size") int size);

    @Select("select count(1) from approval_requests where applicant_id = #{employeeId}")
    long countForApplicant(@Param("employeeId") Long employeeId);

    @Select("select count(1) from approval_requests where applicant_id = #{employeeId} and status = 'PENDING'")
    long countPendingForApplicant(@Param("employeeId") Long employeeId);

    @Select("""
            select
            """ + SELECT_COLUMNS + FROM_CLAUSE + """
            where ar.applicant_id = #{supervisorId}
               or (
                    a.department_id = #{departmentId}
                    and ar.applicant_id <> #{supervisorId}
                    and a.role = 'EMPLOYEE'
                    and ar.type in ('LEAVE', 'FILE_REQUEST')
               )
            order by ar.created_at desc, ar.id desc
            limit #{size} offset #{offset}
            """)
    List<ApprovalRequest> pageForSupervisor(
            @Param("departmentId") Long departmentId,
            @Param("supervisorId") Long supervisorId,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("""
            select count(1)
            from approval_requests ar
            join employees a on ar.applicant_id = a.id
            where ar.applicant_id = #{supervisorId}
               or (
                    a.department_id = #{departmentId}
                    and ar.applicant_id <> #{supervisorId}
                    and a.role = 'EMPLOYEE'
                    and ar.type in ('LEAVE', 'FILE_REQUEST')
               )
            """)
    long countForSupervisor(@Param("departmentId") Long departmentId, @Param("supervisorId") Long supervisorId);

    @Select("""
            select count(1)
            from approval_requests ar
            join employees a on ar.applicant_id = a.id
            where a.department_id = #{departmentId}
              and ar.applicant_id <> #{supervisorId}
              and a.role = 'EMPLOYEE'
              and ar.type in ('LEAVE', 'FILE_REQUEST')
              and ar.status = 'PENDING'
            """)
    long countPendingForSupervisor(@Param("departmentId") Long departmentId, @Param("supervisorId") Long supervisorId);

    @Select("""
            select
            """ + SELECT_COLUMNS + FROM_CLAUSE + """
            where a.department_id = #{departmentId}
            order by ar.created_at desc, ar.id desc
            limit #{size} offset #{offset}
            """)
    List<ApprovalRequest> pageForDepartment(@Param("departmentId") Long departmentId, @Param("offset") int offset, @Param("size") int size);

    @Select("""
            select count(1)
            from approval_requests ar
            join employees a on ar.applicant_id = a.id
            where a.department_id = #{departmentId}
            """)
    long countForDepartment(@Param("departmentId") Long departmentId);

    @Select("""
            select count(1)
            from approval_requests ar
            join employees a on ar.applicant_id = a.id
            where a.department_id = #{departmentId}
              and ar.status = 'PENDING'
              and ar.applicant_id <> #{reviewerId}
            """)
    long countPendingForDepartment(@Param("departmentId") Long departmentId, @Param("reviewerId") Long reviewerId);

    @Insert("""
            insert into approval_requests (
              type, applicant_id, target_employee_id, target_department_id,
              amount, start_date, end_date, file_name, reason, status
            ) values (
              #{type}, #{applicantId}, #{targetEmployeeId}, #{targetDepartmentId},
              #{amount}, #{startDate}, #{endDate}, #{fileName}, #{reason}, #{status}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ApprovalRequest request);

    @Update("""
            update approval_requests
            set status = #{status},
                reviewer_id = #{reviewerId},
                review_comment = #{reviewComment},
                reviewed_at = now(),
                updated_at = now()
            where id = #{id}
              and status = 'PENDING'
            """)
    int review(ApprovalRequest request);

    @Select("""
            <script>
            select
            """ + SELECT_COLUMNS + FROM_CLAUSE + """
            <where>
              <if test='q.type != null and q.type != ""'>and ar.type = #{q.type}</if>
              <if test='q.status != null and q.status != ""'>and ar.status = #{q.status}</if>
              <if test='q.startDate != null'>and ar.created_at &gt;= #{q.startDate}</if>
              <if test='q.endDate != null'>and ar.created_at &lt; date_add(#{q.endDate}, interval 1 day)</if>
            </where>
            order by ar.created_at desc, ar.id desc
            </script>
            """)
    List<ApprovalRequest> exportList(@Param("q") ApprovalRequestQuery query);
}
