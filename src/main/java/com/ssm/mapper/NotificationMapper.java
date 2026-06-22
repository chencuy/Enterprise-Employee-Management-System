package com.ssm.mapper;

import com.ssm.entity.NotificationItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface NotificationMapper {
    @Insert("""
            insert into notification_items (
              receiver_id, type, title, content, source_type, source_id, link_view, read_flag
            ) values (
              #{receiverId}, #{type}, #{title}, #{content}, #{sourceType}, #{sourceId}, #{linkView}, false
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NotificationItem item);

    @Select("""
            select ni.*, e.name as receiver_name
            from notification_items ni
            left join employees e on ni.receiver_id = e.id
            where ni.receiver_id = #{receiverId}
            order by ni.created_at desc, ni.id desc
            limit #{size} offset #{offset}
            """)
    List<NotificationItem> pageForUser(@Param("receiverId") Long receiverId, @Param("offset") int offset, @Param("size") int size);

    @Select("select count(1) from notification_items where receiver_id = #{receiverId}")
    long countForUser(@Param("receiverId") Long receiverId);

    @Select("select count(1) from notification_items where receiver_id = #{receiverId} and read_flag = false")
    long countUnread(@Param("receiverId") Long receiverId);

    @Select("select * from notification_items where id = #{id}")
    NotificationItem findById(@Param("id") Long id);

    @Update("""
            update notification_items
            set read_flag = true, read_at = now()
            where id = #{id} and receiver_id = #{receiverId}
            """)
    int markRead(@Param("id") Long id, @Param("receiverId") Long receiverId);

    @Update("""
            update notification_items
            set read_flag = true, read_at = now()
            where receiver_id = #{receiverId} and read_flag = false
            """)
    int markAllRead(@Param("receiverId") Long receiverId);
}
