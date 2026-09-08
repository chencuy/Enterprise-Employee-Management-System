package com.ssm.mapper;

import com.ssm.entity.Message;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface MessageMapper {
    @Select("""
            select msg.*,
                   s.name as sender_name,
                   s.role as sender_role,
                   r.name as receiver_name,
                   r.role as receiver_role
            from messages msg
            left join employees s on msg.sender_id = s.id
            left join employees r on msg.receiver_id = r.id
            order by msg.created_at desc, msg.id desc
            limit 200
            """)
    List<Message> findAll();

    @Select("""
            select msg.*,
                   s.name as sender_name,
                   s.role as sender_role,
                   r.name as receiver_name,
                   r.role as receiver_role
            from messages msg
            left join employees s on msg.sender_id = s.id
            left join employees r on msg.receiver_id = r.id
            where msg.sender_id = #{userId} or msg.receiver_id = #{userId}
            order by msg.created_at desc, msg.id desc
            limit 200
            """)
    List<Message> findForUser(@Param("userId") Long userId);

    @Select("""
            select msg.*,
                   s.name as sender_name,
                   s.role as sender_role,
                   r.name as receiver_name,
                   r.role as receiver_role
            from messages msg
            left join employees s on msg.sender_id = s.id
            left join employees r on msg.receiver_id = r.id
            order by msg.created_at desc, msg.id desc
            limit #{size} offset #{offset}
            """)
    List<Message> pageAll(@Param("offset") int offset, @Param("size") int size);

    @Select("select count(1) from messages")
    long countAll();

    @Select("""
            select msg.*,
                   s.name as sender_name,
                   s.role as sender_role,
                   r.name as receiver_name,
                   r.role as receiver_role
            from messages msg
            left join employees s on msg.sender_id = s.id
            left join employees r on msg.receiver_id = r.id
            where msg.sender_id = #{userId} or msg.receiver_id = #{userId}
            order by msg.created_at desc, msg.id desc
            limit #{size} offset #{offset}
            """)
    List<Message> pageForUser(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

    @Select("select count(1) from messages where sender_id = #{userId} or receiver_id = #{userId}")
    long countForUser(@Param("userId") Long userId);

    @Select("select count(1) from messages where receiver_id = #{receiverId} and read_flag = false")
    int countUnread(@Param("receiverId") Long receiverId);

    @Select("select count(1) from messages where sender_id = #{senderId} and receiver_id = #{receiverId}")
    int countFromTo(@Param("senderId") Long senderId, @Param("receiverId") Long receiverId);

    @Select("""
            select count(1)
            from messages
            where (sender_id = #{firstId} and receiver_id = #{secondId})
               or (sender_id = #{secondId} and receiver_id = #{firstId})
            """)
    int countBetween(@Param("firstId") Long firstId, @Param("secondId") Long secondId);

    @Insert("""
            insert into messages (sender_id, receiver_id, content, read_flag)
            values (#{senderId}, #{receiverId}, #{content}, false)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Message message);

    @Update("update messages set read_flag = true where id = #{id}")
    int markRead(@Param("id") Long id);

    @Select("select * from messages where id = #{id}")
    Message findById(@Param("id") Long id);
}
