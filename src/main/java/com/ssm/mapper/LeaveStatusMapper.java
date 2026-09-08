package com.ssm.mapper;

import com.ssm.dto.EmployeeLeaveWindow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

public interface LeaveStatusMapper {
    @Update("""
            update employees e
            set e.status = 'LEAVE',
                e.leave_end_date = greatest(
                    coalesce((
                        select max(lr.end_date)
                        from leave_requests lr
                        where lr.employee_id = e.id
                          and lr.status = 'APPROVED'
                          and lr.start_date <= #{today}
                          and lr.end_date >= #{today}
                    ), cast('1000-01-01' as date)),
                    coalesce((
                        select max(ar.end_date)
                        from approval_requests ar
                        where ar.applicant_id = e.id
                          and ar.type = 'LEAVE'
                          and ar.status = 'APPROVED'
                          and ar.start_date <= #{today}
                          and ar.end_date >= #{today}
                    ), cast('1000-01-01' as date))
                ),
                e.updated_at = now()
            where e.role <> 'ADMIN'
              and e.status <> 'RESIGNED'
              and (
                  exists (
                      select 1
                      from leave_requests lr
                      where lr.employee_id = e.id
                        and lr.status = 'APPROVED'
                        and lr.start_date <= #{today}
                        and lr.end_date >= #{today}
                  )
                  or exists (
                      select 1
                      from approval_requests ar
                      where ar.applicant_id = e.id
                        and ar.type = 'LEAVE'
                        and ar.status = 'APPROVED'
                        and ar.start_date <= #{today}
                        and ar.end_date >= #{today}
                  )
              )
            """)
    int activateCurrentApprovedLeaves(@Param("today") LocalDate today);

    @Update("""
            update employees e
            set e.status = 'LEAVE',
                e.leave_end_date = greatest(
                    coalesce((
                        select max(lr.end_date)
                        from leave_requests lr
                        where lr.employee_id = e.id
                          and lr.status = 'APPROVED'
                          and lr.start_date <= #{today}
                          and lr.end_date >= #{today}
                    ), cast('1000-01-01' as date)),
                    coalesce((
                        select max(ar.end_date)
                        from approval_requests ar
                        where ar.applicant_id = e.id
                          and ar.type = 'LEAVE'
                          and ar.status = 'APPROVED'
                          and ar.start_date <= #{today}
                          and ar.end_date >= #{today}
                    ), cast('1000-01-01' as date))
                ),
                e.updated_at = now()
            where e.id = #{employeeId}
              and e.role <> 'ADMIN'
              and e.status <> 'RESIGNED'
              and (
                  exists (
                      select 1
                      from leave_requests lr
                      where lr.employee_id = e.id
                        and lr.status = 'APPROVED'
                        and lr.start_date <= #{today}
                        and lr.end_date >= #{today}
                  )
                  or exists (
                      select 1
                      from approval_requests ar
                      where ar.applicant_id = e.id
                        and ar.type = 'LEAVE'
                        and ar.status = 'APPROVED'
                        and ar.start_date <= #{today}
                        and ar.end_date >= #{today}
                  )
              )
            """)
    int activateCurrentApprovedLeave(@Param("employeeId") Long employeeId, @Param("today") LocalDate today);

    @Update("""
            update employees e
            set e.status = 'WORKING',
                e.leave_end_date = null,
                e.updated_at = now()
            where e.status = 'LEAVE'
              and e.role <> 'ADMIN'
              and not exists (
                  select 1
                  from leave_requests lr
                  where lr.employee_id = e.id
                    and lr.status = 'APPROVED'
                    and lr.start_date <= #{today}
                    and lr.end_date >= #{today}
              )
              and not exists (
                  select 1
                  from approval_requests ar
                  where ar.applicant_id = e.id
                    and ar.type = 'LEAVE'
                    and ar.status = 'APPROVED'
                    and ar.start_date <= #{today}
                    and ar.end_date >= #{today}
              )
              and (
                  e.leave_end_date is null
                  or e.leave_end_date < #{today}
                  or exists (
                      select 1
                      from leave_requests lr
                      where lr.employee_id = e.id
                        and lr.status = 'APPROVED'
                        and lr.start_date > #{today}
                        and lr.end_date = e.leave_end_date
                  )
                  or exists (
                      select 1
                      from approval_requests ar
                      where ar.applicant_id = e.id
                        and ar.type = 'LEAVE'
                        and ar.status = 'APPROVED'
                        and ar.start_date > #{today}
                        and ar.end_date = e.leave_end_date
                  )
              )
            """)
    int deactivateInactiveLeaveStatuses(@Param("today") LocalDate today);

    @Update("""
            update employees e
            set e.status = 'WORKING',
                e.leave_end_date = null,
                e.updated_at = now()
            where e.id = #{employeeId}
              and e.status = 'LEAVE'
              and e.role <> 'ADMIN'
              and not exists (
                  select 1
                  from leave_requests lr
                  where lr.employee_id = e.id
                    and lr.status = 'APPROVED'
                    and lr.start_date <= #{today}
                    and lr.end_date >= #{today}
              )
              and not exists (
                  select 1
                  from approval_requests ar
                  where ar.applicant_id = e.id
                    and ar.type = 'LEAVE'
                    and ar.status = 'APPROVED'
                    and ar.start_date <= #{today}
                    and ar.end_date >= #{today}
              )
              and (
                  e.leave_end_date is null
                  or e.leave_end_date < #{today}
                  or exists (
                      select 1
                      from leave_requests lr
                      where lr.employee_id = e.id
                        and lr.status = 'APPROVED'
                        and lr.start_date > #{today}
                        and lr.end_date = e.leave_end_date
                  )
                  or exists (
                      select 1
                      from approval_requests ar
                      where ar.applicant_id = e.id
                        and ar.type = 'LEAVE'
                        and ar.status = 'APPROVED'
                        and ar.start_date > #{today}
                        and ar.end_date = e.leave_end_date
                  )
              )
            """)
    int deactivateInactiveLeaveStatus(@Param("employeeId") Long employeeId, @Param("today") LocalDate today);

    @Select("""
            select (
                select count(1)
                from leave_requests lr
                where lr.employee_id = #{employeeId}
                  and lr.status = 'APPROVED'
                  and lr.start_date <= #{date}
                  and lr.end_date >= #{date}
            ) + (
                select count(1)
                from approval_requests ar
                where ar.applicant_id = #{employeeId}
                  and ar.type = 'LEAVE'
                  and ar.status = 'APPROVED'
                  and ar.start_date <= #{date}
                  and ar.end_date >= #{date}
            )
            """)
    int countApprovedLeaveOnDate(@Param("employeeId") Long employeeId, @Param("date") LocalDate date);

    @Select({
            "<script>",
            "select distinct leave_days.employee_id",
            "from (",
            "  select lr.employee_id",
            "  from leave_requests lr",
            "  where lr.status = 'APPROVED'",
            "    and lr.start_date &lt;= #{date}",
            "    and lr.end_date &gt;= #{date}",
            "    and lr.employee_id in",
            "    <foreach collection='employeeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "  union",
            "  select ar.applicant_id as employee_id",
            "  from approval_requests ar",
            "  where ar.type = 'LEAVE'",
            "    and ar.status = 'APPROVED'",
            "    and ar.start_date &lt;= #{date}",
            "    and ar.end_date &gt;= #{date}",
            "    and ar.applicant_id in",
            "    <foreach collection='employeeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            ") leave_days",
            "</script>"
    })
    List<Long> findApprovedLeaveEmployeeIdsOnDate(@Param("employeeIds") List<Long> employeeIds, @Param("date") LocalDate date);

    @Select({
            "<script>",
            "select lr.employee_id, lr.start_date, lr.end_date",
            "from leave_requests lr",
            "where lr.status = 'APPROVED'",
            "  and lr.start_date &lt;= #{endDate}",
            "  and lr.end_date &gt;= #{startDate}",
            "  and lr.employee_id in",
            "  <foreach collection='employeeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "union all",
            "select ar.applicant_id as employee_id, ar.start_date, ar.end_date",
            "from approval_requests ar",
            "where ar.type = 'LEAVE'",
            "  and ar.status = 'APPROVED'",
            "  and ar.start_date &lt;= #{endDate}",
            "  and ar.end_date &gt;= #{startDate}",
            "  and ar.applicant_id in",
            "  <foreach collection='employeeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<EmployeeLeaveWindow> findApprovedLeaveWindows(
            @Param("employeeIds") List<Long> employeeIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
