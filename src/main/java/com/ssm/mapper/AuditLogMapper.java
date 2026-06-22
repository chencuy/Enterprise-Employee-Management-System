package com.ssm.mapper;

import com.ssm.entity.AuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AuditLogMapper {
    @Insert("""
            insert into audit_logs (
              operator_id, operator_name, operator_role, module, action,
              target_type, target_id, target_name, detail
            ) values (
              #{operatorId}, #{operatorName}, #{operatorRole}, #{module}, #{action},
              #{targetType}, #{targetId}, #{targetName}, #{detail}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuditLog log);

    @Select({
            "<script>",
            "select * from audit_logs",
            "<where>",
            "  <if test='module != null and module != \"\"'>and module = #{module}</if>",
            "  <if test='operatorName != null and operatorName != \"\"'>and operator_name like concat('%', #{operatorName}, '%')</if>",
            "</where>",
            "order by created_at desc, id desc limit #{size} offset #{offset}",
            "</script>"
    })
    List<AuditLog> page(
            @Param("module") String module,
            @Param("operatorName") String operatorName,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select({
            "<script>",
            "select count(1) from audit_logs",
            "<where>",
            "  <if test='module != null and module != \"\"'>and module = #{module}</if>",
            "  <if test='operatorName != null and operatorName != \"\"'>and operator_name like concat('%', #{operatorName}, '%')</if>",
            "</where>",
            "</script>"
    })
    long count(@Param("module") String module, @Param("operatorName") String operatorName);

    @Select("select count(1) from audit_logs where id > #{afterId}")
    long countAfterId(@Param("afterId") Long afterId);
}
