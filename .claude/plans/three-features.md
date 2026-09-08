# Plan: 员工改密 + 签到时间窗口 + 公告置顶/撤回

## Feature 1: 员工自主修改密码

### Backend
- **`ProfileController.java`** — 新增 `POST /api/profile/change-password`
- **`ChangePasswordRequest.java`** (新 DTO) — `oldPassword`, `newPassword`
- **`EmployeeService.java`** — 新增 `changePassword(userId, oldPassword, newPassword)`:
  1. 从 DB 查出员工记录
  2. 用 `passwordService.matches()` 验证旧密码
  3. 用 `passwordService.validateRawPassword()` 校验新密码（8-72位）
  4. 旧密码和新密码不能相同
  5. `passwordService.encode()` 加密后更新 DB
  6. 写操作审计日志

### Frontend (app.js)
- 个人信息面板底部增加「修改密码」按钮
- 弹窗表单：旧密码、新密码、确认新密码
- 前端校验：新密码两次输入一致、长度 8-72

---

## Feature 2: 签到时间窗口限制

### Backend
- **`application.yml`** — 新增配置项 `app.attendance.check-in-start=07:00` 和 `app.attendance.check-in-end=10:00`
- **`AttendanceService.java`** — `checkIn()` 方法开头增加时间窗口校验：
  - 读取 `checkInStart` / `checkInEnd` (LocalTime)
  - 当前时间不在窗口内时抛出 403，提示"签到时间为 HH:MM - HH:MM"
  - 已签到的幂等逻辑不受影响
- **`AttendanceController.java`** — 新增 `GET /api/attendance/check-in-window` 返回当前签到时间窗口配置，供前端展示
- **`DatabaseUpgradeInitializer.java`** — 无需改动（用 application.yml 配置即可）

### Frontend (app.js)
- 考勤页面签到按钮旁显示签到时间窗口提示（如"签到时间 07:00-10:00"）
- 打开验证码弹窗前先检查时间窗口，不在窗口内直接提示

---

## Feature 3: 公告置顶/撤回

### Database
- **`schema.sql`** — announcements 表新增 `is_pinned tinyint(1) not null default 0` 和 `status varchar(20) not null default 'PUBLISHED'`
- **`DatabaseUpgradeInitializer.java`** — `addColumnIfMissing` 添加 `is_pinned` 和 `status` 列

### Backend
- **`Announcement.java`** — 新增 `isPinned` (Boolean) 和 `status` (String) 字段
- **`AnnouncementMapper.java`**:
  - `findAllForUser` 的 ORDER BY 改为 `ORDER BY is_pinned DESC, created_at DESC, id DESC`（置顶排最前）
  - 新增 `updatePinned(id, pinned)` — UPDATE is_pinned
  - 新增 `updateStatus(id, status)` — UPDATE status
  - `findAllForUser` 和 `findUnreadForUser` 增加 `WHERE status = 'PUBLISHED'` 条件
- **`AnnouncementService.java`**:
  - 新增 `togglePin(id, user)` — ADMIN only，切换置顶状态，写审计
  - 新增 `unpublish(id, user)` — ADMIN only，将 status 改为 'ARCHIVED'，写审计
- **`AnnouncementController.java`**:
  - 新增 `PUT /api/announcements/{id}/pin` — 切换置顶
  - 新增 `DELETE /api/announcements/{id}` — 撤回公告（软删除，status → ARCHIVED）

### Frontend (app.js)
- 公告列表中置顶公告显示「置顶」标签
- 管理员看到「置顶/取消置顶」和「撤回」操作按钮
- 撤回需二次确认
- 公告发布表单增加「置顶」勾选框

---

## 涉及文件清单

| 文件 | 操作 |
|------|------|
| `src/main/java/com/ssm/dto/ChangePasswordRequest.java` | 新建 |
| `src/main/java/com/ssm/controller/ProfileController.java` | 修改 |
| `src/main/java/com/ssm/service/EmployeeService.java` | 修改 |
| `src/main/java/com/ssm/service/AttendanceService.java` | 修改 |
| `src/main/java/com/ssm/controller/AttendanceController.java` | 修改 |
| `src/main/java/com/ssm/entity/Announcement.java` | 修改 |
| `src/main/java/com/ssm/mapper/AnnouncementMapper.java` | 修改 |
| `src/main/java/com/ssm/service/AnnouncementService.java` | 修改 |
| `src/main/java/com/ssm/controller/AnnouncementController.java` | 修改 |
| `src/main/java/com/ssm/config/DatabaseUpgradeInitializer.java` | 修改 |
| `src/main/resources/db/schema.sql` | 修改 |
| `src/main/resources/application.yml` | 修改 |
| `src/main/resources/static/app/app.js` | 修改 |
| `src/main/resources/static/app/styles.css` | 修改 |
| `src/main/resources/templates/login.html` | 修改（版本号） |
| `README.md` | 修改 |
