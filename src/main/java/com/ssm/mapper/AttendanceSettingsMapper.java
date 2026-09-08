package com.ssm.mapper;

import com.ssm.entity.AttendanceSettings;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

public interface AttendanceSettingsMapper {
    @Select("select * from attendance_settings where id = 1")
    AttendanceSettings find();

    @Insert("""
            insert into attendance_settings (id, check_in_start, check_in_end, late_after)
            values (1, #{checkInStart}, #{checkInEnd}, #{lateAfter})
            on duplicate key update
              check_in_start = values(check_in_start),
              check_in_end = values(check_in_end),
              late_after = values(late_after),
              updated_at = now()
            """)
    int upsert(AttendanceSettings settings);
}
