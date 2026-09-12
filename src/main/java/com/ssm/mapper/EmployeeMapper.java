package com.ssm.mapper;

import com.ssm.dto.EmployeeQuery;
import com.ssm.entity.Employee;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface EmployeeMapper {
    @Select({
            "<script>",
            "select e.*, d.name as department_name, d.manager_id as manager_id, m.name as manager_name,",
            "(e.lock_password is not null) as has_lock_password",
            "from employees e",
            "left join departments d on e.department_id = d.id",
            "left join employees m on d.manager_id = m.id",
            "<where>",
            "  <if test='q.name != null and q.name != \"\"'>",
            "    <choose>",
            "      <when test='q.nameMode == \"exact\"'>and e.name = #{q.name}</when>",
            "      <otherwise>and e.name like concat('%', #{q.name}, '%')</otherwise>",
            "    </choose>",
            "  </if>",
            "  <if test='q.gender != null and q.gender != \"\"'>and e.gender = #{q.gender}</if>",
            "  <if test='q.departmentId != null'>and e.department_id = #{q.departmentId}</if>",
            "  <if test='q.status != null and q.status != \"\"'>and e.status = #{q.status}</if>",
            "  <if test='q.minSalary != null'>and e.salary &gt;= #{q.minSalary}</if>",
            "  <if test='q.maxSalary != null'>and e.salary &lt;= #{q.maxSalary}</if>",
            "  <if test='q.hireDateStart != null'>and e.hire_date &gt;= #{q.hireDateStart}</if>",
            "  <if test='q.hireDateEnd != null'>and e.hire_date &lt;= #{q.hireDateEnd}</if>",
            "</where>",
            "order by e.id desc limit #{size} offset #{offset}",
            "</script>"
    })
    List<Employee> page(@Param("q") EmployeeQuery query, @Param("offset") int offset, @Param("size") int size);

    @Select({
            "<script>",
            "select count(1)",
            "from employees e",
            "left join departments d on e.department_id = d.id",
            "<where>",
            "  <if test='q.name != null and q.name != \"\"'>",
            "    <choose>",
            "      <when test='q.nameMode == \"exact\"'>and e.name = #{q.name}</when>",
            "      <otherwise>and e.name like concat('%', #{q.name}, '%')</otherwise>",
            "    </choose>",
            "  </if>",
            "  <if test='q.gender != null and q.gender != \"\"'>and e.gender = #{q.gender}</if>",
            "  <if test='q.departmentId != null'>and e.department_id = #{q.departmentId}</if>",
            "  <if test='q.status != null and q.status != \"\"'>and e.status = #{q.status}</if>",
            "  <if test='q.minSalary != null'>and e.salary &gt;= #{q.minSalary}</if>",
            "  <if test='q.maxSalary != null'>and e.salary &lt;= #{q.maxSalary}</if>",
            "  <if test='q.hireDateStart != null'>and e.hire_date &gt;= #{q.hireDateStart}</if>",
            "  <if test='q.hireDateEnd != null'>and e.hire_date &lt;= #{q.hireDateEnd}</if>",
            "</where>",
            "</script>"
    })
    long count(@Param("q") EmployeeQuery query);

    @Select("""
            select e.*, d.name as department_name, d.manager_id as manager_id, m.name as manager_name
            from employees e
            left join departments d on e.department_id = d.id
            left join employees m on d.manager_id = m.id
            where e.id = #{id}
            """)
    Employee findById(@Param("id") Long id);

    @Select("""
            select e.*, d.name as department_name, d.manager_id as manager_id, m.name as manager_name
            from employees e
            left join departments d on e.department_id = d.id
            left join employees m on d.manager_id = m.id
            where e.username = #{username}
            """)
    Employee findByUsername(@Param("username") String username);

    @Select({
            "<script>",
            "select count(1) from employees where username = #{username}",
            "<if test='id != null'>and id &lt;&gt; #{id}</if>",
            "</script>"
    })
    int countByUsername(@Param("username") String username, @Param("id") Long id);

    @Select("select count(1) from employees where role = 'ADMIN' and status <> 'RESIGNED'")
    int countActiveAdmins();

    @Select("""
            select e.*, d.name as department_name, d.manager_id as manager_id, m.name as manager_name
            from employees e
            left join departments d on e.department_id = d.id
            left join employees m on d.manager_id = m.id
            where e.role = 'ADMIN' and e.id <> #{excludeId} and e.status <> 'RESIGNED'
            order by e.name
            """)
    List<Employee> findAdmins(@Param("excludeId") Long excludeId);

    @Select("""
            select e.*, d.name as department_name, d.manager_id as manager_id, m.name as manager_name
            from employees e
            left join departments d on e.department_id = d.id
            left join employees m on d.manager_id = m.id
            where e.department_id = #{departmentId}
              and e.role = 'EMPLOYEE'
              and e.status <> 'RESIGNED'
              and e.id <> #{excludeId}
            order by e.name
            """)
    List<Employee> findDepartmentEmployees(@Param("departmentId") Long departmentId, @Param("excludeId") Long excludeId);

    @Select("""
            select e.*, d.name as department_name, d.manager_id as manager_id, m.name as manager_name
            from employees e
            left join departments d on e.department_id = d.id
            left join employees m on d.manager_id = m.id
            where e.id <> #{excludeId} and e.status <> 'RESIGNED'
            order by e.role, e.department_id, e.name
            """)
    List<Employee> findAllActiveExcept(@Param("excludeId") Long excludeId);

    @Select("""
            select e.*, d.name as department_name, d.manager_id as manager_id, m.name as manager_name
            from employees e
            left join departments d on e.department_id = d.id
            left join employees m on d.manager_id = m.id
            where e.role <> 'ADMIN'
            order by e.role, e.status, e.department_id, e.name, e.id
            """)
    List<Employee> findAllForAttendanceOptions();

    @Select("""
            select e.*, d.name as department_name, d.manager_id as manager_id, m.name as manager_name
            from employees e
            left join departments d on e.department_id = d.id
            left join employees m on d.manager_id = m.id
            where e.department_id = #{departmentId}
              and e.id <> #{excludeId}
              and e.role <> 'ADMIN'
            order by e.status, e.role, e.name, e.id
            """)
    List<Employee> findDepartmentAttendanceOptions(@Param("departmentId") Long departmentId, @Param("excludeId") Long excludeId);

    @Select("select id from employees where department_id = #{departmentId} and status <> 'RESIGNED'")
    List<Long> findActiveIdsByDepartment(@Param("departmentId") Long departmentId);

    @Select({
            "<script>",
            "select e.*, d.name as department_name, d.manager_id as manager_id, m.name as manager_name",
            "from employees e",
            "left join departments d on e.department_id = d.id",
            "left join employees m on d.manager_id = m.id",
            "where e.status != 'RESIGNED'",
            "and e.role != 'ADMIN'",
            "<if test='departmentId != null'>and e.department_id = #{departmentId}</if>",
            "<if test='employeeId != null'>and e.id = #{employeeId}</if>",
            "order by d.name, e.name, e.id",
            "</script>"
    })
    List<Employee> findAttendanceCandidates(@Param("departmentId") Long departmentId, @Param("employeeId") Long employeeId);

    @Select({
            "<script>",
            "select id from employees",
            "where status = 'WORKING'",
            "and role != 'ADMIN'",
            "and department_id in",
            "<foreach collection='departmentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<Long> findWorkingIdsByDepartments(@Param("departmentIds") List<Long> departmentIds);

    @Insert("""
            insert into employees (
                username, password, name, phone, email, gender, salary, department_id,
                role, status, leave_end_date, hire_date
            ) values (
                #{username}, #{password}, #{name}, #{phone}, #{email}, #{gender}, #{salary}, #{departmentId},
                #{role}, #{status}, #{leaveEndDate}, #{hireDate}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Employee employee);

    @Update("""
            update employees
            set username = #{username},
                password = #{password},
                name = #{name},
                phone = #{phone},
                email = #{email},
                gender = #{gender},
                salary = #{salary},
                department_id = #{departmentId},
                role = #{role},
                status = #{status},
                leave_end_date = #{leaveEndDate},
                hire_date = #{hireDate},
                updated_at = now()
            where id = #{id}
            """)
    int update(Employee employee);

    @Update("""
            update employees
            set salary = #{salary}, updated_at = now()
            where id = #{employeeId}
              and salary <=> #{expectedSalary}
            """)
    int updateSalaryIfCurrent(
            @Param("employeeId") Long employeeId,
            @Param("expectedSalary") BigDecimal expectedSalary,
            @Param("salary") BigDecimal salary
    );

    @Update("""
            update employees
            set department_id = #{departmentId}, updated_at = now()
            where id = #{employeeId}
            """)
    int updateDepartment(@Param("employeeId") Long employeeId, @Param("departmentId") Long departmentId);

    @Update("""
            update employees
            set status = #{status}, leave_end_date = #{leaveEndDate}, updated_at = now()
            where id = #{employeeId}
            """)
    int updateLeaveState(
            @Param("employeeId") Long employeeId,
            @Param("status") String status,
            @Param("leaveEndDate") LocalDate leaveEndDate
    );

    @Update("""
            update employees
            set password = #{password}, updated_at = now()
            where id = #{employeeId}
            """)
    int updatePassword(@Param("employeeId") Long employeeId, @Param("password") String password);

    @Update("update employees set phone = #{phone}, email = #{email}, avatar_path = #{avatarPath}, updated_at = now() where id = #{employeeId}")
    int updateProfile(@Param("employeeId") Long employeeId, @Param("phone") String phone, @Param("email") String email, @Param("avatarPath") String avatarPath);

    @Update("""
            update employees
            set lock_password = #{lockPassword}, updated_at = now()
            where id = #{employeeId}
            """)
    int updateLockPassword(@Param("employeeId") Long employeeId, @Param("lockPassword") String lockPassword);

    @Update("""
            update employees
            set lock_password = null, lock_password_reset_at = null, updated_at = now()
            where id = #{employeeId}
            """)
    int clearLockPassword(@Param("employeeId") Long employeeId);

    @Update("""
            update employees
            set lock_password_reset_at = now(), updated_at = now()
            where id = #{employeeId} and lock_password is not null
            """)
    int requestLockPasswordReset(@Param("employeeId") Long employeeId);

    @Update("""
            update employees
            set lock_password = null, lock_password_reset_at = null, updated_at = now()
            where lock_password is not null
              and lock_password_reset_at is not null
              and lock_password_reset_at < date_sub(now(), interval 7 day)
            """)
    int autoClearExpiredLockPasswords();

    @Update("""
            update employees
            set lock_password = null, lock_password_reset_at = null, updated_at = now()
            where id = #{employeeId}
              and lock_password is not null
              and lock_password_reset_at is not null
              and lock_password_reset_at < date_sub(now(), interval 7 day)
            """)
    int autoClearExpiredLockPassword(@Param("employeeId") Long employeeId);

    @Delete("delete from employees where id = #{id}")
    int delete(@Param("id") Long id);

    @Select({
            "<script>",
            "select e.*, d.name as department_name, d.manager_id as manager_id, m.name as manager_name",
            "from employees e",
            "left join departments d on e.department_id = d.id",
            "left join employees m on d.manager_id = m.id",
            "<where>",
            "  <if test='q.name != null and q.name != \"\"'>",
            "    <choose>",
            "      <when test='q.nameMode == \"exact\"'>and e.name = #{q.name}</when>",
            "      <otherwise>and e.name like concat('%', #{q.name}, '%')</otherwise>",
            "    </choose>",
            "  </if>",
            "  <if test='q.gender != null and q.gender != \"\"'>and e.gender = #{q.gender}</if>",
            "  <if test='q.departmentId != null'>and e.department_id = #{q.departmentId}</if>",
            "  <if test='q.status != null and q.status != \"\"'>and e.status = #{q.status}</if>",
            "  <if test='q.minSalary != null'>and e.salary &gt;= #{q.minSalary}</if>",
            "  <if test='q.maxSalary != null'>and e.salary &lt;= #{q.maxSalary}</if>",
            "  <if test='q.hireDateStart != null'>and e.hire_date &gt;= #{q.hireDateStart}</if>",
            "  <if test='q.hireDateEnd != null'>and e.hire_date &lt;= #{q.hireDateEnd}</if>",
            "</where>",
            "order by e.id",
            "</script>"
    })
    List<Employee> exportList(@Param("q") EmployeeQuery query);
}
