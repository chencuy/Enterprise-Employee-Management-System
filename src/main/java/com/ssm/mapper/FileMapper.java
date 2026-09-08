package com.ssm.mapper;

import com.ssm.dto.FileRecipientDetail;
import com.ssm.entity.SharedFile;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface FileMapper {
    @Insert("""
            insert into files (original_name, stored_name, content_type, size_bytes, uploader_id)
            values (#{originalName}, #{storedName}, #{contentType}, #{sizeBytes}, #{uploaderId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SharedFile file);

    @Insert("""
            insert ignore into file_recipients (file_id, employee_id)
            values (#{fileId}, #{employeeId})
            """)
    int insertRecipient(@Param("fileId") Long fileId, @Param("employeeId") Long employeeId);

    @Select("select * from files where id = #{id}")
    SharedFile findById(@Param("id") Long id);

    @Select("""
            select f.*,
                   u.name as uploader_name,
                   (select count(1) from file_recipients fr where fr.file_id = f.id) as recipient_count,
                   (select count(1) from file_recipients fr where fr.file_id = f.id and fr.read_flag = true) as downloaded_count
            from files f
            left join employees u on f.uploader_id = u.id
            order by f.created_at desc, f.id desc
            """)
    List<SharedFile> findAllForAdmin();

    @Select("""
            select f.*,
                   u.name as uploader_name,
                   (select count(1) from file_recipients fr where fr.file_id = f.id) as recipient_count,
                   (select count(1) from file_recipients fr where fr.file_id = f.id and fr.read_flag = true) as downloaded_count
            from files f
            left join employees u on f.uploader_id = u.id
            order by f.created_at desc, f.id desc
            limit #{size} offset #{offset}
            """)
    List<SharedFile> pageAllForAdmin(@Param("offset") int offset, @Param("size") int size);

    @Select("select count(1) from files")
    long countAllForAdmin();

    @Select("""
            select f.*,
                   u.name as uploader_name,
                   fr.read_flag as read_flag,
                   fr.downloaded_at as downloaded_at
            from files f
            join file_recipients fr on fr.file_id = f.id and fr.employee_id = #{employeeId}
            left join employees u on f.uploader_id = u.id
            order by f.created_at desc, f.id desc
            """)
    List<SharedFile> findForUser(@Param("employeeId") Long employeeId);

    @Select("""
            select f.*,
                   u.name as uploader_name,
                   fr.read_flag as read_flag,
                   fr.downloaded_at as downloaded_at
            from files f
            join file_recipients fr on fr.file_id = f.id and fr.employee_id = #{employeeId}
            left join employees u on f.uploader_id = u.id
            order by f.created_at desc, f.id desc
            limit #{size} offset #{offset}
            """)
    List<SharedFile> pageForUser(@Param("employeeId") Long employeeId, @Param("offset") int offset, @Param("size") int size);

    @Select("select count(1) from file_recipients where employee_id = #{employeeId}")
    long countForUser(@Param("employeeId") Long employeeId);

    @Select("select count(1) from file_recipients where employee_id = #{employeeId} and read_flag = false")
    int countUnreadForUser(@Param("employeeId") Long employeeId);

    @Select("select count(1) from file_recipients where file_id = #{fileId} and employee_id = #{employeeId}")
    int isRecipient(@Param("fileId") Long fileId, @Param("employeeId") Long employeeId);

    @Update("""
            update file_recipients
            set read_flag = true, downloaded_at = now()
            where file_id = #{fileId} and employee_id = #{employeeId}
            """)
    int markRead(@Param("fileId") Long fileId, @Param("employeeId") Long employeeId);

    @Select("""
            select e.id as employee_id,
                   e.name as employee_name,
                   e.username as username,
                   e.role as role,
                   e.status as status,
                   e.department_id as department_id,
                   d.name as department_name,
                   fr.read_flag as read_flag,
                   fr.downloaded_at as downloaded_at
            from file_recipients fr
            join employees e on fr.employee_id = e.id
            left join departments d on e.department_id = d.id
            where fr.file_id = #{fileId}
            order by fr.read_flag desc, fr.downloaded_at desc, d.name, e.name
            """)
    List<FileRecipientDetail> recipientDetails(@Param("fileId") Long fileId);

    @Delete("delete from files where id = #{id}")
    int delete(@Param("id") Long id);
}
