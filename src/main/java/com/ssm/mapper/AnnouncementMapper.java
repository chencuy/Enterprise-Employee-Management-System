package com.ssm.mapper;

import com.ssm.dto.AnnouncementReadDetail;
import com.ssm.entity.Announcement;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface AnnouncementMapper {
    @Select("""
            select a.*,
                   p.name as publisher_name,
                   case when a.publisher_id = #{employeeId} or ar.employee_id is not null then true else false end as read_flag,
                   ar.read_at
            from announcements a
            left join employees p on a.publisher_id = p.id
            left join announcement_reads ar
                   on ar.announcement_id = a.id and ar.employee_id = #{employeeId}
            where a.status = 'PUBLISHED'
            order by a.is_pinned desc, a.created_at desc, a.id desc
            """)
    List<Announcement> findAllForUser(@Param("employeeId") Long employeeId);

    @Select("""
            select a.*,
                   p.name as publisher_name,
                   false as read_flag,
                   null as read_at
            from announcements a
            left join employees p on a.publisher_id = p.id
            where a.publisher_id <> #{employeeId}
              and a.status = 'PUBLISHED'
              and not exists (
                select 1
                from announcement_reads ar
                where ar.announcement_id = a.id
                  and ar.employee_id = #{employeeId}
            )
            order by a.is_pinned desc, a.created_at asc, a.id asc
            """)
    List<Announcement> findUnreadForUser(@Param("employeeId") Long employeeId);

    @Select("select * from announcements where id = #{id}")
    Announcement findById(@Param("id") Long id);

    @Insert("""
            insert into announcements (title, content, publisher_id, is_pinned)
            values (#{title}, #{content}, #{publisherId}, #{isPinned})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Announcement announcement);

    @Update("update announcements set is_pinned = #{isPinned} where id = #{id}")
    int updatePinned(@Param("id") Long id, @Param("isPinned") boolean isPinned);

    @Update("update announcements set status = #{status} where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Insert("""
            insert ignore into announcement_reads (announcement_id, employee_id, read_at)
            values (#{announcementId}, #{employeeId}, now())
            """)
    int markRead(@Param("announcementId") Long announcementId, @Param("employeeId") Long employeeId);

    @Select("""
            select e.id as employee_id,
                   e.name as employee_name,
                   e.username,
                   e.role,
                   e.department_id as department_id,
                   d.name as department_name,
                   ar.read_at
            from announcement_reads ar
            join employees e on ar.employee_id = e.id
            left join departments d on e.department_id = d.id
            where ar.announcement_id = #{announcementId}
            order by ar.read_at desc
            """)
    List<AnnouncementReadDetail> readDetails(@Param("announcementId") Long announcementId);

    @Select("""
            select e.id as employee_id,
                   e.name as employee_name,
                   e.username,
                   e.role,
                   e.department_id as department_id,
                   d.name as department_name,
                   null as read_at
            from employees e
            left join departments d on e.department_id = d.id
            where e.status <> 'RESIGNED'
              and e.id <> #{publisherId}
              and not exists (
                select 1 from announcement_reads ar
                where ar.announcement_id = #{announcementId}
                  and ar.employee_id = e.id
              )
            order by d.name, e.name
            """)
    List<AnnouncementReadDetail> unreadEmployees(
            @Param("announcementId") Long announcementId,
            @Param("publisherId") Long publisherId
    );
}
