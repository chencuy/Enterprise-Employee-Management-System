package com.ssm.mapper;

import com.ssm.dto.AttendanceQuery;
import com.ssm.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceMapper {
    @Select({
            "<script>",
            "select ar.*, e.name as employee_name, e.username as username, e.role as role, e.department_id as department_id, d.name as department_name",
            "from attendance_records ar",
            "join employees e on ar.employee_id = e.id",
            "left join departments d on e.department_id = d.id",
            "<where>",
            "  <if test='q.employeeId != null'>and ar.employee_id = #{q.employeeId}</if>",
            "  <if test='q.startDate != null'>and ar.attendance_date &gt;= #{q.startDate}</if>",
            "  <if test='q.endDate != null'>and ar.attendance_date &lt;= #{q.endDate}</if>",
            "</where>",
            "order by ar.attendance_date desc, ar.id desc",
            "limit #{size} offset #{offset}",
            "</script>"
    })
    List<AttendanceRecord> page(@Param("q") AttendanceQuery query, @Param("offset") int offset, @Param("size") int size);

    @Select({
            "<script>",
            "select count(1)",
            "from attendance_records ar",
            "join employees e on ar.employee_id = e.id",
            "<where>",
            "  <if test='q.employeeId != null'>and ar.employee_id = #{q.employeeId}</if>",
            "  <if test='q.startDate != null'>and ar.attendance_date &gt;= #{q.startDate}</if>",
            "  <if test='q.endDate != null'>and ar.attendance_date &lt;= #{q.endDate}</if>",
            "</where>",
            "</script>"
    })
    long count(@Param("q") AttendanceQuery query);

    @Select({
            "<script>",
            "select ar.*, e.name as employee_name, e.username as username, e.role as role, e.department_id as department_id, d.name as department_name",
            "from attendance_records ar",
            "join employees e on ar.employee_id = e.id",
            "left join departments d on e.department_id = d.id",
            "<where>",
            "  <if test='q.employeeId != null'>and ar.employee_id = #{q.employeeId}</if>",
            "  <if test='q.startDate != null'>and ar.attendance_date &gt;= #{q.startDate}</if>",
            "  <if test='q.endDate != null'>and ar.attendance_date &lt;= #{q.endDate}</if>",
            "</where>",
            "order by ar.attendance_date desc, e.name, ar.id desc",
            "</script>"
    })
    List<AttendanceRecord> exportList(@Param("q") AttendanceQuery query);

    @Select({
            "<script>",
            "select ar.*, e.name as employee_name, e.username as username, e.role as role, e.department_id as department_id, d.name as department_name",
            "from attendance_records ar",
            "join employees e on ar.employee_id = e.id",
            "left join departments d on e.department_id = d.id",
            "where ar.attendance_date &gt;= #{startDate}",
            "and ar.attendance_date &lt;= #{endDate}",
            "and ar.employee_id in",
            "<foreach collection='employeeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<AttendanceRecord> findByEmployeesAndRange(
            @Param("employeeIds") List<Long> employeeIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Select("""
            select ar.*, e.name as employee_name, e.username as username, e.role as role, e.department_id as department_id, d.name as department_name
            from attendance_records ar
            join employees e on ar.employee_id = e.id
            left join departments d on e.department_id = d.id
            where ar.employee_id = #{employeeId}
              and ar.attendance_date = #{attendanceDate}
            """)
    AttendanceRecord findByEmployeeAndDate(
            @Param("employeeId") Long employeeId,
            @Param("attendanceDate") LocalDate attendanceDate
    );

    @Select("""
            select count(1)
            from attendance_records
            where employee_id = #{employeeId}
              and attendance_date = #{attendanceDate}
            """)
    int countByEmployeeAndDate(
            @Param("employeeId") Long employeeId,
            @Param("attendanceDate") LocalDate attendanceDate
    );

    @Insert("""
            insert into attendance_records (employee_id, attendance_date, check_in_at, source, remark)
            values (#{employeeId}, #{attendanceDate}, #{checkInAt}, #{source}, #{remark})
            on duplicate key update
              check_in_at = coalesce(attendance_records.check_in_at, values(check_in_at)),
              source = case when attendance_records.check_in_at is null then values(source) else attendance_records.source end,
              remark = case when attendance_records.check_in_at is null then values(remark) else attendance_records.remark end,
              updated_at = now()
            """)
    int upsertCheckIn(AttendanceRecord record);

    @Insert("""
            insert into attendance_records (employee_id, attendance_date, check_in_at, source, remark)
            values (#{employeeId}, #{attendanceDate}, #{checkInAt}, #{source}, #{remark})
            on duplicate key update
              check_in_at = values(check_in_at),
              source = values(source),
              remark = values(remark),
              updated_at = now()
            """)
    int upsertAdminFill(AttendanceRecord record);

    @Insert("""
            insert into attendance_records (
              employee_id, attendance_date, source, original_name, stored_name, content_type, size_bytes, remark
            ) values (
              #{employeeId}, #{attendanceDate}, #{source}, #{originalName}, #{storedName}, #{contentType}, #{sizeBytes}, #{remark}
            )
            on duplicate key update
              source = values(source),
              original_name = values(original_name),
              stored_name = values(stored_name),
              content_type = values(content_type),
              size_bytes = values(size_bytes),
              remark = values(remark),
              updated_at = now()
            """)
    int upsertImport(AttendanceRecord record);

    @Insert("""
            insert into attendance_records (employee_id, attendance_date, check_in_at, source, remark)
            values (#{employeeId}, #{attendanceDate}, null, #{source}, #{remark})
            on duplicate key update
              check_in_at = null,
              source = values(source),
              remark = values(remark),
              updated_at = now()
            """)
    int upsertForceAbsent(AttendanceRecord record);
}
