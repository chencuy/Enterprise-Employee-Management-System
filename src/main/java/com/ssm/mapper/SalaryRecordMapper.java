package com.ssm.mapper;

import com.ssm.dto.SalaryQuery;
import com.ssm.entity.SalaryRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SalaryRecordMapper {
    @Select({
            "<script>",
            "select sr.*, e.name as employee_name, e.salary as current_salary, o.name as operator_name",
            "from salary_records sr",
            "left join employees e on sr.employee_id = e.id",
            "left join employees o on sr.operator_id = o.id",
            "<where>",
            "  <if test='q.employeeName != null and q.employeeName != \"\"'>and e.name like concat('%', #{q.employeeName}, '%')</if>",
            "  <if test='q.changeType != null and q.changeType != \"\"'>and sr.change_type = #{q.changeType}</if>",
            "  <if test='q.startDate != null'>and date(sr.created_at) &gt;= #{q.startDate}</if>",
            "  <if test='q.endDate != null'>and date(sr.created_at) &lt;= #{q.endDate}</if>",
            "</where>",
            "order by sr.created_at desc, sr.id desc limit #{size} offset #{offset}",
            "</script>"
    })
    List<SalaryRecord> page(@Param("q") SalaryQuery query, @Param("offset") int offset, @Param("size") int size);

    @Select({
            "<script>",
            "select count(1)",
            "from salary_records sr",
            "left join employees e on sr.employee_id = e.id",
            "<where>",
            "  <if test='q.employeeName != null and q.employeeName != \"\"'>and e.name like concat('%', #{q.employeeName}, '%')</if>",
            "  <if test='q.changeType != null and q.changeType != \"\"'>and sr.change_type = #{q.changeType}</if>",
            "  <if test='q.startDate != null'>and date(sr.created_at) &gt;= #{q.startDate}</if>",
            "  <if test='q.endDate != null'>and date(sr.created_at) &lt;= #{q.endDate}</if>",
            "</where>",
            "</script>"
    })
    long count(@Param("q") SalaryQuery query);

    @Select("""
            select sr.*, e.name as employee_name, e.salary as current_salary, o.name as operator_name
            from salary_records sr
            left join employees e on sr.employee_id = e.id
            left join employees o on sr.operator_id = o.id
            where sr.employee_id = #{employeeId}
            order by sr.created_at desc, sr.id desc
            """)
    List<SalaryRecord> history(@Param("employeeId") Long employeeId);

    @Insert("""
            insert into salary_records (employee_id, amount, change_type, remark, operator_id)
            values (#{employeeId}, #{amount}, #{changeType}, #{remark}, #{operatorId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SalaryRecord record);

    @Select({
            "<script>",
            "select sr.*, e.name as employee_name, e.salary as current_salary, o.name as operator_name",
            "from salary_records sr",
            "left join employees e on sr.employee_id = e.id",
            "left join employees o on sr.operator_id = o.id",
            "<where>",
            "  <if test='q.employeeName != null and q.employeeName != \"\"'>and e.name like concat('%', #{q.employeeName}, '%')</if>",
            "  <if test='q.changeType != null and q.changeType != \"\"'>and sr.change_type = #{q.changeType}</if>",
            "  <if test='q.startDate != null'>and date(sr.created_at) &gt;= #{q.startDate}</if>",
            "  <if test='q.endDate != null'>and date(sr.created_at) &lt;= #{q.endDate}</if>",
            "</where>",
            "order by sr.created_at desc",
            "</script>"
    })
    List<SalaryRecord> exportList(@Param("q") SalaryQuery query);
}
