create database if not exists employee_management
  default character set utf8mb4
  collate utf8mb4_unicode_ci;

use employee_management;

drop table if exists salary_records;
drop table if exists notification_items;
drop table if exists approval_requests;
drop table if exists leave_requests;
drop table if exists attendance_settings;
drop table if exists attendance_records;
drop table if exists file_recipients;
drop table if exists files;
drop table if exists announcement_reads;
drop table if exists announcements;
drop table if exists messages;
drop table if exists login_logs;
drop table if exists audit_logs;
drop table if exists employees;
drop table if exists departments;

create table departments (
  id bigint primary key auto_increment,
  name varchar(80) not null,
  description varchar(255),
  manager_id bigint,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp
) engine=InnoDB default charset=utf8mb4;

create table employees (
  id bigint primary key auto_increment,
  username varchar(60) not null unique,
  password varchar(160) not null,
  lock_password varchar(160),
  lock_password_reset_at datetime,
  name varchar(60) not null,
  phone varchar(30),
  email varchar(120),
  avatar_path varchar(160),
  gender varchar(20) not null default 'UNKNOWN',
  salary decimal(12, 2) not null default 0.00,
  department_id bigint,
  role varchar(20) not null,
  status varchar(20) not null default 'WORKING',
  leave_end_date date,
  hire_date date not null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  constraint fk_employee_department foreign key (department_id) references departments(id)
) engine=InnoDB default charset=utf8mb4;

create index idx_employee_department on employees(department_id);
create index idx_employee_role on employees(role);
create index idx_employee_status on employees(status);
create index idx_employee_hire_date on employees(hire_date);

create table attendance_records (
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
) engine=InnoDB default charset=utf8mb4;

create index idx_attendance_date on attendance_records(attendance_date);
create index idx_attendance_employee on attendance_records(employee_id);

create table attendance_settings (
  id bigint primary key,
  check_in_start time not null,
  check_in_end time not null,
  late_after time not null,
  updated_at datetime not null default current_timestamp on update current_timestamp
) engine=InnoDB default charset=utf8mb4;

create table messages (
  id bigint primary key auto_increment,
  sender_id bigint not null,
  receiver_id bigint not null,
  content varchar(1000) not null,
  read_flag tinyint(1) not null default 0,
  created_at datetime not null default current_timestamp,
  constraint fk_message_sender foreign key (sender_id) references employees(id) on delete cascade,
  constraint fk_message_receiver foreign key (receiver_id) references employees(id) on delete cascade
) engine=InnoDB default charset=utf8mb4;

create index idx_message_sender on messages(sender_id);
create index idx_message_receiver on messages(receiver_id);
create index idx_message_read on messages(read_flag);

create table audit_logs (
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
) engine=InnoDB default charset=utf8mb4;

create index idx_audit_created on audit_logs(created_at);
create index idx_audit_module on audit_logs(module);
create index idx_audit_operator on audit_logs(operator_id);

create table login_logs (
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
) engine=InnoDB default charset=utf8mb4;

create index idx_login_log_username on login_logs(username);
create index idx_login_log_ip on login_logs(ip_address);
create index idx_login_log_result on login_logs(result);
create index idx_login_log_created on login_logs(created_at);

create table announcements (
  id bigint primary key auto_increment,
  title varchar(80) not null,
  content varchar(3000) not null,
  publisher_id bigint not null,
  is_pinned tinyint(1) not null default 0,
  status varchar(20) not null default 'PUBLISHED',
  created_at datetime not null default current_timestamp,
  constraint fk_announcement_publisher foreign key (publisher_id) references employees(id)
) engine=InnoDB default charset=utf8mb4;

create index idx_announcement_created on announcements(created_at);

create table announcement_reads (
  announcement_id bigint not null,
  employee_id bigint not null,
  read_at datetime not null default current_timestamp,
  primary key (announcement_id, employee_id),
  constraint fk_announcement_read_announcement foreign key (announcement_id) references announcements(id) on delete cascade,
  constraint fk_announcement_read_employee foreign key (employee_id) references employees(id) on delete cascade
) engine=InnoDB default charset=utf8mb4;

create index idx_announcement_read_employee on announcement_reads(employee_id);

create table files (
  id bigint primary key auto_increment,
  original_name varchar(255) not null,
  stored_name varchar(160) not null,
  content_type varchar(150),
  size_bytes bigint not null default 0,
  uploader_id bigint not null,
  created_at datetime not null default current_timestamp,
  constraint fk_file_uploader foreign key (uploader_id) references employees(id)
) engine=InnoDB default charset=utf8mb4;

create index idx_file_uploader on files(uploader_id);

create table file_recipients (
  file_id bigint not null,
  employee_id bigint not null,
  read_flag tinyint(1) not null default 0,
  downloaded_at datetime,
  primary key (file_id, employee_id),
  constraint fk_file_recipient_file foreign key (file_id) references files(id) on delete cascade,
  constraint fk_file_recipient_employee foreign key (employee_id) references employees(id) on delete cascade
) engine=InnoDB default charset=utf8mb4;

create index idx_file_recipient_employee on file_recipients(employee_id);
create index idx_file_recipient_read on file_recipients(read_flag);

create table leave_requests (
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
) engine=InnoDB default charset=utf8mb4;

create index idx_leave_employee on leave_requests(employee_id);
create index idx_leave_status on leave_requests(status);
create index idx_leave_created on leave_requests(created_at);

create table approval_requests (
  id bigint primary key auto_increment,
  type varchar(30) not null,
  applicant_id bigint not null,
  target_employee_id bigint,
  target_department_id bigint,
  amount decimal(12, 2),
  start_date date,
  end_date date,
  file_name varchar(255),
  profile_phone varchar(30),
  profile_email varchar(120),
  profile_avatar_path varchar(160),
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
) engine=InnoDB default charset=utf8mb4;

create index idx_approval_applicant on approval_requests(applicant_id);
create index idx_approval_status on approval_requests(status);
create index idx_approval_type on approval_requests(type);
create index idx_approval_created on approval_requests(created_at);

create table notification_items (
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
) engine=InnoDB default charset=utf8mb4;

create index idx_notification_receiver on notification_items(receiver_id);
create index idx_notification_read on notification_items(read_flag);
create index idx_notification_created on notification_items(created_at);

create table salary_records (
  id bigint primary key auto_increment,
  employee_id bigint not null,
  amount decimal(12, 2) not null,
  change_type varchar(20) not null,
  remark varchar(500),
  operator_id bigint not null,
  created_at datetime not null default current_timestamp,
  constraint fk_salary_employee foreign key (employee_id) references employees(id) on delete cascade,
  constraint fk_salary_operator foreign key (operator_id) references employees(id)
) engine=InnoDB default charset=utf8mb4;

create index idx_salary_employee on salary_records(employee_id);
create index idx_salary_type on salary_records(change_type);
create index idx_salary_created on salary_records(created_at);

insert into departments (id, name, description, manager_id) values
  (1, '研发部', '负责产品研发、架构设计与技术交付', null),
  (2, '市场部', '负责市场活动、客户增长与品牌推广', null),
  (3, '财务部', '负责薪酬核算、预算控制与费用管理', null);
