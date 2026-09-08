package com.ssm.mapper;

import com.ssm.entity.Department;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface DepartmentMapper {
    @Select({
            "<script>",
            "select d.*,",
            "       m.name as manager_name,",
            "       (select count(1) from employees e where e.department_id = d.id) as employee_count",
            "from departments d",
            "left join employees m on d.manager_id = m.id",
            "<where>",
            "  <if test='name != null and name != \"\"'>and d.name like concat('%', #{name}, '%')</if>",
            "  <if test='managerName != null and managerName != \"\"'>and m.name like concat('%', #{managerName}, '%')</if>",
            "</where>",
            "order by d.id",
            "limit #{size} offset #{offset}",
            "</script>"
    })
    List<Department> page(
            @Param("name") String name,
            @Param("managerName") String managerName,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select({
            "<script>",
            "select count(1)",
            "from departments d",
            "left join employees m on d.manager_id = m.id",
            "<where>",
            "  <if test='name != null and name != \"\"'>and d.name like concat('%', #{name}, '%')</if>",
            "  <if test='managerName != null and managerName != \"\"'>and m.name like concat('%', #{managerName}, '%')</if>",
            "</where>",
            "</script>"
    })
    long count(@Param("name") String name, @Param("managerName") String managerName);

    @Select("""
            select d.*,
                   m.name as manager_name,
                   (select count(1) from employees e where e.department_id = d.id) as employee_count
            from departments d
            left join employees m on d.manager_id = m.id
            order by d.id
            """)
    List<Department> findAll();

    @Select("""
            select d.*,
                   m.name as manager_name,
                   (select count(1) from employees e where e.department_id = d.id) as employee_count
            from departments d
            left join employees m on d.manager_id = m.id
            where d.id = #{id}
            """)
    Department findById(@Param("id") Long id);

    @Select("select count(1) from employees where department_id = #{departmentId}")
    int countEmployees(@Param("departmentId") Long departmentId);

    @Insert("""
            insert into departments (name, description, manager_id)
            values (#{name}, #{description}, #{managerId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Department department);

    @Update("""
            update departments
            set name = #{name},
                description = #{description},
                manager_id = #{managerId},
                updated_at = now()
            where id = #{id}
            """)
    int update(Department department);

    @Delete("delete from departments where id = #{id}")
    int delete(@Param("id") Long id);
}
