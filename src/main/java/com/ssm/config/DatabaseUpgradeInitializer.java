package com.ssm.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class DatabaseUpgradeInitializer {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseUpgradeInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureAnnouncementTables() {
        addColumnIfMissing("employees", "lock_password", "alter table employees add column lock_password varchar(160) after password");
        addColumnIfMissing("employees", "lock_password_reset_at", "alter table employees add column lock_password_reset_at datetime after lock_password");

        jdbcTemplate.execute("""
                create table if not exists announcements (
                  id bigint primary key auto_increment,
                  title varchar(80) not null,
                  content varchar(3000) not null,
                  publisher_id bigint not null,
                  created_at datetime not null default current_timestamp,
                  constraint fk_announcement_publisher foreign key (publisher_id) references employees(id)
                ) engine=InnoDB default charset=utf8mb4
                """);
        createIndexIfMissing(
                "announcements",
                "idx_announcement_created",
                "create index idx_announcement_created on announcements(created_at)");
        addColumnIfMissing("announcements", "is_pinned", "alter table announcements add column is_pinned tinyint(1) not null default 0 after publisher_id");
        addColumnIfMissing("announcements", "status", "alter table announcements add column status varchar(20) not null default 'PUBLISHED' after is_pinned");
        jdbcTemplate.execute("""
                create table if not exists announcement_reads (
                  announcement_id bigint not null,
                  employee_id bigint not null,
                  read_at datetime not null default current_timestamp,
                  primary key (announcement_id, employee_id),
                  constraint fk_announcement_read_announcement foreign key (announcement_id) references announcements(id) on delete cascade,
                  constraint fk_announcement_read_employee foreign key (employee_id) references employees(id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4
                """);
        createIndexIfMissing(
                "announcement_reads",
                "idx_announcement_read_employee",
                "create index idx_announcement_read_employee on announcement_reads(employee_id)");
        ensureFileTables();
        ensureAuditTables();
        ensureLoginLogTables();
        ensureLeaveTables();
        ensureAttendanceTables();
        ensureApprovalTables();
        ensureNotificationTables();
    }

    private void ensureFileTables() {
        jdbcTemplate.execute("""
                create table if not exists files (
                  id bigint primary key auto_increment,
                  original_name varchar(255) not null,
                  stored_name varchar(160) not null,
                  content_type varchar(150),
                  size_bytes bigint not null default 0,
                  uploader_id bigint not null,
                  created_at datetime not null default current_timestamp,
                  constraint fk_file_uploader foreign key (uploader_id) references employees(id)
                ) engine=InnoDB default charset=utf8mb4
                """);
        createIndexIfMissing(
                "files",
                "idx_file_uploader",
                "create index idx_file_uploader on files(uploader_id)");
        jdbcTemplate.execute("""
                create table if not exists file_recipients (
                  file_id bigint not null,
                  employee_id bigint not null,
                  read_flag tinyint(1) not null default 0,
                  downloaded_at datetime,
                  primary key (file_id, employee_id),
                  constraint fk_file_recipient_file foreign key (file_id) references files(id) on delete cascade,
                  constraint fk_file_recipient_employee foreign key (employee_id) references employees(id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4
                """);
        createIndexIfMissing(
                "file_recipients",
                "idx_file_recipient_employee",
                "create index idx_file_recipient_employee on file_recipients(employee_id)");
        createIndexIfMissing(
                "file_recipients",
                "idx_file_recipient_read",
                "create index idx_file_recipient_read on file_recipients(read_flag)");
    }

    private void ensureAuditTables() {
        jdbcTemplate.execute("""
                create table if not exists audit_logs (
                  id bigint primary key auto_increment,
                  operator_id bigint,
                  operator_name varchar(80) not null,
                  operator_role varchar(30) not null,
                  module varchar(40) not null,
                  action varchar(40) not null,
                  target_type varchar(40),
                  target_id bigint,
                  target_name varchar(160),
                  detail varchar(1000),
                  created_at datetime not null default current_timestamp
                ) engine=InnoDB default charset=utf8mb4
                """);
        createIndexIfMissing(
                "audit_logs",
                "idx_audit_created",
                "create index idx_audit_created on audit_logs(created_at)");
        createIndexIfMissing(
                "audit_logs",
                "idx_audit_module",
                "create index idx_audit_module on audit_logs(module)");
        createIndexIfMissing(
                "audit_logs",
                "idx_audit_operator",
                "create index idx_audit_operator on audit_logs(operator_id)");
    }

    private void ensureLoginLogTables() {
        jdbcTemplate.execute("""
                create table if not exists login_logs (
                  id bigint primary key auto_increment,
                  employee_id bigint,
                  username varchar(60) not null,
                  employee_name varchar(80),
                  ip_address varchar(64) not null,
                  result varchar(30) not null,
                  detail varchar(300),
                  kicked_offline tinyint(1) not null default 0,
                  created_at datetime not null default current_timestamp,
                  constraint fk_login_log_employee foreign key (employee_id) references employees(id) on delete set null
                ) engine=InnoDB default charset=utf8mb4
                """);
        createIndexIfMissing(
                "login_logs",
                "idx_login_log_username",
                "create index idx_login_log_username on login_logs(username)");
        createIndexIfMissing(
                "login_logs",
                "idx_login_log_ip",
                "create index idx_login_log_ip on login_logs(ip_address)");
        createIndexIfMissing(
                "login_logs",
                "idx_login_log_result",
                "create index idx_login_log_result on login_logs(result)");
        createIndexIfMissing(
                "login_logs",
                "idx_login_log_created",
                "create index idx_login_log_created on login_logs(created_at)");
    }

    private void ensureLeaveTables() {
        jdbcTemplate.execute("""
                create table if not exists leave_requests (
                  id bigint primary key auto_increment,
                  employee_id bigint not null,
                  start_date date not null,
                  end_date date not null,
                  reason varchar(500) not null,
                  status varchar(20) not null default 'PENDING',
                  approver_id bigint,
                  review_comment varchar(500),
                  reviewed_at datetime,
                  created_at datetime not null default current_timestamp,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  constraint fk_leave_employee foreign key (employee_id) references employees(id) on delete cascade,
                  constraint fk_leave_approver foreign key (approver_id) references employees(id) on delete set null
                ) engine=InnoDB default charset=utf8mb4
                """);
        createIndexIfMissing(
                "leave_requests",
                "idx_leave_employee",
                "create index idx_leave_employee on leave_requests(employee_id)");
        createIndexIfMissing(
                "leave_requests",
                "idx_leave_status",
                "create index idx_leave_status on leave_requests(status)");
        createIndexIfMissing(
                "leave_requests",
                "idx_leave_created",
                "create index idx_leave_created on leave_requests(created_at)");
    }

    private void ensureAttendanceTables() {
        jdbcTemplate.execute("""
                create table if not exists attendance_records (
                  id bigint primary key auto_increment,
                  employee_id bigint not null,
                  attendance_date date not null,
                  check_in_at datetime,
                  source varchar(20) not null default 'SIGN_IN',
                  original_name varchar(255),
                  stored_name varchar(160),
                  content_type varchar(150),
                  size_bytes bigint not null default 0,
                  remark varchar(500),
                  created_at datetime not null default current_timestamp,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  unique key uk_attendance_employee_date (employee_id, attendance_date),
                  constraint fk_attendance_employee foreign key (employee_id) references employees(id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4
                """);
        createIndexIfMissing(
                "attendance_records",
                "idx_attendance_date",
                "create index idx_attendance_date on attendance_records(attendance_date)");
        createIndexIfMissing(
                "attendance_records",
                "idx_attendance_employee",
                "create index idx_attendance_employee on attendance_records(employee_id)");
        jdbcTemplate.execute("""
                create table if not exists attendance_settings (
                  id bigint primary key,
                  check_in_start time not null,
                  check_in_end time not null,
                  late_after time not null,
                  updated_at datetime not null default current_timestamp on update current_timestamp
                ) engine=InnoDB default charset=utf8mb4
                """);
    }

    private void ensureApprovalTables() {
        jdbcTemplate.execute("""
                create table if not exists approval_requests (
                  id bigint primary key auto_increment,
                  type varchar(30) not null,
                  applicant_id bigint not null,
                  target_employee_id bigint,
                  target_department_id bigint,
                  amount decimal(12, 2),
                  start_date date,
                  end_date date,
                  file_name varchar(255),
                  reason varchar(500) not null,
                  status varchar(20) not null default 'PENDING',
                  reviewer_id bigint,
                  review_comment varchar(500),
                  reviewed_at datetime,
                  created_at datetime not null default current_timestamp,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  constraint fk_approval_applicant foreign key (applicant_id) references employees(id) on delete cascade,
                  constraint fk_approval_target_employee foreign key (target_employee_id) references employees(id) on delete set null,
                  constraint fk_approval_target_department foreign key (target_department_id) references departments(id) on delete set null,
                  constraint fk_approval_reviewer foreign key (reviewer_id) references employees(id) on delete set null
                ) engine=InnoDB default charset=utf8mb4
                """);
        addColumnIfMissing("approval_requests", "start_date", "alter table approval_requests add column start_date date after amount");
        addColumnIfMissing("approval_requests", "end_date", "alter table approval_requests add column end_date date after start_date");
        addColumnIfMissing("approval_requests", "file_name", "alter table approval_requests add column file_name varchar(255) after end_date");
        createIndexIfMissing(
                "approval_requests",
                "idx_approval_applicant",
                "create index idx_approval_applicant on approval_requests(applicant_id)");
        createIndexIfMissing(
                "approval_requests",
                "idx_approval_status",
                "create index idx_approval_status on approval_requests(status)");
        createIndexIfMissing(
                "approval_requests",
                "idx_approval_type",
                "create index idx_approval_type on approval_requests(type)");
        createIndexIfMissing(
                "approval_requests",
                "idx_approval_created",
                "create index idx_approval_created on approval_requests(created_at)");
    }

    private void ensureNotificationTables() {
        jdbcTemplate.execute("""
                create table if not exists notification_items (
                  id bigint primary key auto_increment,
                  receiver_id bigint not null,
                  type varchar(40) not null,
                  title varchar(120) not null,
                  content varchar(1000) not null,
                  source_type varchar(40),
                  source_id bigint,
                  link_view varchar(40),
                  read_flag tinyint(1) not null default 0,
                  read_at datetime,
                  created_at datetime not null default current_timestamp,
                  constraint fk_notification_receiver foreign key (receiver_id) references employees(id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4
                """);
        addColumnIfMissing("notification_items", "read_at", "alter table notification_items add column read_at datetime after read_flag");
        createIndexIfMissing(
                "notification_items",
                "idx_notification_receiver",
                "create index idx_notification_receiver on notification_items(receiver_id)");
        createIndexIfMissing(
                "notification_items",
                "idx_notification_read",
                "create index idx_notification_read on notification_items(read_flag)");
        createIndexIfMissing(
                "notification_items",
                "idx_notification_created",
                "create index idx_notification_created on notification_items(created_at)");
    }

    private void createIndexIfMissing(String tableName, String indexName, String sql) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(1)
                from information_schema.statistics
                where table_schema = database()
                  and table_name = ?
                  and index_name = ?
                """, Integer.class, tableName, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(sql);
        }
    }

    private void addColumnIfMissing(String tableName, String columnName, String sql) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(1)
                from information_schema.columns
                where table_schema = database()
                  and table_name = ?
                  and column_name = ?
                """, Integer.class, tableName, columnName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(sql);
        }
    }
}
