package com.ssm.mapper;

import com.ssm.dto.LoginLogQuery;
import com.ssm.entity.LoginLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface LoginLogMapper {
    @Insert("""
            insert into login_logs (
              employee_id, username, employee_name, ip_address, result, detail, kicked_offline
            ) values (
              #{employeeId}, #{username}, #{employeeName}, #{ipAddress}, #{result}, #{detail}, #{kickedOffline}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LoginLog log);

    @Select("select * from login_logs where employee_id = #{employeeId} order by created_at desc, id desc limit #{size} offset #{offset}")
    List<LoginLog> pageForEmployee(@Param("employeeId") Long employeeId, @Param("offset") int offset, @Param("size") int size);

    @Select("select count(1) from login_logs where employee_id = #{employeeId}")
    long countForEmployee(@Param("employeeId") Long employeeId);

    @Select({
            "<script>",
            "select * from login_logs",
            "<where>",
            "  <if test='q.username != null and q.username != \"\"'>and username like concat('%', #{q.username}, '%')</if>",
            "  <if test='q.ipAddress != null and q.ipAddress != \"\"'>and ip_address like concat('%', #{q.ipAddress}, '%')</if>",
            "  <if test='q.result != null and q.result != \"\"'>and result = #{q.result}</if>",
            "  <if test='q.startDate != null'>and created_at &gt;= #{q.startDate}</if>",
            "  <if test='q.endDate != null'>and created_at &lt; date_add(#{q.endDate}, interval 1 day)</if>",
            "</where>",
            "order by created_at desc, id desc",
            "limit #{size} offset #{offset}",
            "</script>"
    })
    List<LoginLog> page(@Param("q") LoginLogQuery query, @Param("offset") int offset, @Param("size") int size);

    @Select({
            "<script>",
            "select count(1) from login_logs",
            "<where>",
            "  <if test='q.username != null and q.username != \"\"'>and username like concat('%', #{q.username}, '%')</if>",
            "  <if test='q.ipAddress != null and q.ipAddress != \"\"'>and ip_address like concat('%', #{q.ipAddress}, '%')</if>",
            "  <if test='q.result != null and q.result != \"\"'>and result = #{q.result}</if>",
            "  <if test='q.startDate != null'>and created_at &gt;= #{q.startDate}</if>",
            "  <if test='q.endDate != null'>and created_at &lt; date_add(#{q.endDate}, interval 1 day)</if>",
            "</where>",
            "</script>"
    })
    long count(@Param("q") LoginLogQuery query);
}
