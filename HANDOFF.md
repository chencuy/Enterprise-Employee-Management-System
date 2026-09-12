# 项目交接说明

> 更新时间：2026-09-10
> 当前分支：`main`
> 最近已提交版本：`49b0f37 docs: update README for security and settings changes`
> 当前状态：本轮个人设置、资料审批、登录记录和头像存储改动仍在工作区，尚未提交或推送。

## 交接结论

本轮已经完成个人设置、资料审批、登录日志隔离、通知已读交互和头像独立存储的代码改造，编译与前端构建检查通过。当前不能视为“已验收完成”：真实服务启动、数据库联调、头像重启持久化和双用户权限隔离尚未完成。

接手后的第一件事不是继续扩展页面，而是完成“头像上传 → 审批 → 重启 → 读取”的端到端验收，并优先修复头像审批中数据库与文件移动顺序不一致的问题。未完成这两项前，不要把当前工作区标记为生产可用。

## 接手前必读

1. 不要删除 `data/avatars`。它虽然被 Git 忽略，但属于运行时持久化数据，不是可随意清理的缓存。
2. 不要提交 `.idea/vcs.xml` 或其他本地 IDE 状态文件。
3. 不要只根据数据库中的 `avatar_path` 判断头像存在，必须同时确认磁盘文件可读。
4. 不要把头像重新放回 `data/files`。普通分发文件和用户头像已经分离。
5. 不要在当前 CSP 下使用 `blob:` 地址预览头像。当前 `img-src` 未允许 `blob:`，应继续使用 `FileReader.readAsDataURL()`，或者先同步修改并验证 CSP。
6. 不要把日期中的 `/` 写入文件名。Windows 不允许文件名包含 `/`，头像时间格式使用 `yyyyMMddHHmmssSSS`。
7. 不要绕过后端手机号和邮箱校验。前端校验只用于改善体验，真正的边界必须保留在提交、直接保存和审批通过链路。

## 本轮做了什么

### 个人设置页面

- 新增独立侧边栏入口“个人设置”。
- 登录密码、锁屏密码、资料维护和登录会话集中在个人设置页面。
- 个人资料维护默认只显示摘要，点击“修改”后弹出编辑窗口。
- 登录与会话默认只显示入口，点击“查看”后弹出当前用户自己的登录记录。
- 登录记录每页 5 条，支持上一页和下一页。
- 左侧侧边栏固定为视口高度并独立滚动，右侧内容增长不会再把退出登录按钮挤到页面最下方。

### 个人资料变更与审批

- 新增审批类型 `PROFILE_UPDATE`。
- 普通员工和主管修改手机号、邮箱、头像时创建管理员审批申请，审批通过前不会覆盖已生效资料。
- 管理员没有上级审批人，修改自己的资料时直接保存并立即生效。
- 手机号规则统一为 `^1[3-9]\d{9}$`。
- 邮箱执行基本格式校验，最大长度 120 个字符。
- 校验覆盖前端提交、后端申请创建、管理员直接保存、员工管理编辑和审批通过前复核。
- 审批驳回时删除对应的待审批头像。

### 头像存储

- 新增 `AvatarStorageService`，头像不再使用普通文件的 `data/files` 目录。
- 正式头像目录：`${AVATAR_STORAGE_DIR}`，默认 `${user.dir}/data/avatars`。
- 待审批头像目录：`${AVATAR_STORAGE_DIR}/pending`。
- 正式命名格式：`员工编号_avatar_yyyyMMddHHmmssSSS.jpg/png`，例如 `01_avatar_20260910143025123.jpg`。
- 员工编号不足两位时补零；编号超过两位时保留完整编号。
- 管理员直接上传时保存为正式头像；普通员工/主管上传时先保存到 `pending`，审批通过后移动到正式目录。
- 新正式头像生效后清理同一员工的旧正式头像，确保每个员工最终只保留一个正式头像。
- 选择头像后的前端预览改用 `FileReader.readAsDataURL()`，解决 CSP 阻止 `blob:` 图片导致无法预览的问题。
- 头像接口仍只允许已登录用户读取自己当前已生效的头像，这是既有隐私边界，不应无意放宽。
- 头像文件缺失时，前端自动回退为绿色 EMS 默认标识。

### 登录、通知与日志

- 新增 `GET /api/login-logs/me`，后端使用当前会话员工编号固定过滤，不接受前端指定其他员工。
- 个人设置中的登录与会话只显示当前用户自己的登录记录。
- 通知中心移除独立“已读”按钮，单条操作只保留“查看”；点击后先标记已读再跳转来源页面。
- 系统已有数据库登录日志 `login_logs` 和业务操作日志 `audit_logs`。
- Spring Boot 运行日志现在同时输出到控制台和文件，默认写入 `${user.dir}/data/logs/application.log`，支持 `LOG_FILE` 覆盖。
- 日志按天压缩轮转，只保留最近 3 个自然日；`LogRetentionCleanup` 按归档文件名日期在启动时（可由 `LOG_CLEAN_HISTORY_ON_START` 关闭）和每小时清理早于保留窗口的文件，Logback 数量限制仅作兜底。可通过 `LOG_RETENTION_NATURAL_DAYS`、`LOG_RETENTION_CLEANUP_INTERVAL_MS`、`LOG_CLEAN_HISTORY_ON_START`、`LOG_MAX_FILE_SIZE` 和 `LOG_TOTAL_SIZE_CAP` 调整。
- 根日志级别和 `com.ssm` 应用日志级别默认均为 `INFO`，可通过 `LOG_LEVEL_ROOT` 和 `LOG_LEVEL_APP` 调整；生产环境不建议开启 `DEBUG`。
- 文件和控制台日志默认使用 UTF-8，避免中文运行日志在不同操作系统区域设置下乱码。
- 日志通过 `logback-spring.xml` 统一输出 JSON；请求过滤器生成或透传 `X-Request-ID`，并在 MDC 中记录请求方法、路径、状态码、耗时和来源 IP。
- JSON 日志布局统一遮蔽密码、验证码、Cookie、Authorization、CSRF Token 和常见 token 字段；`LogSanitizerTest` 覆盖表单/JSON 字段和 HTTP 头格式。
- 当前仍未完成结构化字段、敏感字段脱敏和集中式日志采集，这些属于后续生产加固事项。

### 数据库与配置

- `employees` 新增 `avatar_path`。
- `approval_requests` 新增：
  - `profile_phone`
  - `profile_email`
  - `profile_avatar_path`
- `schema.sql` 和 `DatabaseUpgradeInitializer` 均已同步存量数据库升级逻辑。
- 新增环境变量 `AVATAR_STORAGE_DIR`。
- `.gitignore` 已忽略 `data/avatars/**` 和旧格式 `data/files/avatar-*`。

### 文档与仓库边界

- `README.md` 已补充个人设置、资料审批、通知、登录日志、头像目录、运行日志和当前验证状态。
- 本文件用于开发交接，不替代 README；README 说明“系统有什么”，本文件说明“当前做到哪里、下一步怎么接、哪些坑不能再踩”。
- 当前工作区存在未提交改动；`HANDOFF.md`、`AvatarStorageService.java` 和 `.idea/vcs.xml` 是未跟踪文件，其中 `.idea/vcs.xml` 不得提交。

## 当前主要文件

| 文件 | 作用 |
| --- | --- |
| `src/main/java/com/ssm/controller/ProfileController.java` | 个人资料接口、头像上传和头像读取 |
| `src/main/java/com/ssm/service/AvatarStorageService.java` | 头像命名、正式/待审批目录、提升和清理 |
| `src/main/java/com/ssm/service/ApprovalService.java` | `PROFILE_UPDATE` 创建、审批、驳回及资料生效 |
| `src/main/java/com/ssm/service/EmployeeService.java` | 管理员直接修改资料和统一资料校验 |
| `src/main/java/com/ssm/controller/LoginLogController.java` | 当前用户登录记录接口 |
| `src/main/java/com/ssm/service/LoginLogService.java` | 登录记录当前用户隔离和分页 |
| `src/main/java/com/ssm/config/RequestLoggingFilter.java` | 请求 ID、MDC 请求字段和 HTTP 访问日志 |
| `src/main/java/com/ssm/config/JsonLogLayout.java` | JSON 日志格式化和敏感信息统一脱敏 |
| `src/main/java/com/ssm/config/LogSanitizer.java` | 密码、验证码、Cookie、Authorization、CSRF Token 脱敏 |
| `src/main/resources/logback-spring.xml` | JSON 控制台/文件输出和轮转兜底 |
| `src/main/java/com/ssm/config/LogRetentionCleanup.java` | 按自然日清理过期 JSON 日志归档 |
| `src/main/resources/static/app/app.js` | 个人设置弹窗、头像预览、通知查看交互 |
| `src/main/resources/static/app/styles.css` | 设置页面、弹窗和固定侧边栏样式 |
| `src/main/resources/application.yml` | 文件与头像存储目录配置 |

## 已验证

最近一次检查全部通过：

```powershell
mvn -o -q -DskipTests compile
node --check src/main/resources/static/app/app.js
npm run build -- --emptyOutDir=false
git diff --check
```

这些检查只能证明代码可编译、前端可构建且差异格式正常，不能代替真实浏览器和数据库联调。

当前没有完成以下验证，因此不能在交接时声称“头像功能已完整验证”：真实上传、审批通过/驳回、服务重启后读取、旧头像清理、双用户隔离和异常文件拒绝。

## 还没有完成的验证

接手后优先在真实运行环境完成以下流程：

1. 管理员上传 JPG 和 PNG，确认立即预览、立即生效、数据库路径正确且重启后仍可显示。
2. 普通员工上传新头像，确认审批前侧边栏仍显示旧头像，待审批文件位于 `data/avatars/pending`。
3. 管理员通过头像审批，确认文件移动到正式目录、数据库改为正式文件名、旧正式头像被删除。
4. 管理员驳回头像审批，确认待审批文件被删除，旧头像不受影响。
5. 分别测试仅修改手机号、仅修改邮箱、仅修改头像和同时修改三项。
6. 验证非法手机号、非法邮箱、超过 2 MB、伪造 MIME 和错误图片文件头均被拒绝。
7. 使用两个不同用户登录，确认 `/api/login-logs/me` 绝不会返回其他用户的记录。
8. 在通知中心点击未读通知，确认只显示“查看”，点击后状态变为已读并正确跳转。
9. 重启服务前后保持同一个绝对 `AVATAR_STORAGE_DIR`，确认头像文件不会丢失。

## 已知问题与必须避免的重复问题

### P1：头像审批的数据库与文件移动顺序仍需修复

当前 `ApprovalService.applyApproval()` 会先把正式头像文件名写入员工记录，再调用 `AvatarStorageService.promote()` 移动待审批文件。如果移动失败，数据库可能引用一个不存在的正式文件，页面只能回退默认头像，形成数据与磁盘不一致。

后续必须调整为：先验证并移动待审批文件，再更新数据库；数据库更新失败时删除或回滚刚移动的文件；任何失败路径都不能留下“数据库已引用、磁盘不存在”的状态。修复后必须补充审批通过失败和进程异常场景的测试。

### 旧头像无法自动恢复

早期版本把头像保存到 `data/files`，并使用 `avatar-active-*` / `avatar-pending-*` 命名。当前正式接口只识别新命名格式。旧数据库如果仍引用旧文件名，需要重新上传头像，或者单独编写迁移脚本同步移动文件并更新数据库。不要只修改数据库字符串而不移动实际文件。

### Git 忽略不代表可以删除

`data/avatars/**` 被忽略是为了防止用户图片进入源码仓库，不代表这些文件是临时文件。部署、备份和清理脚本必须把它当作持久化业务数据。此前曾因把运行时头像误认为仓库垃圾而删除，导致数据库仍有引用但浏览器显示破图；不要再次发生。

### 启动目录变化会改变默认目录

默认值基于 `${user.dir}`。IDE、命令行、Windows 服务或容器的工作目录可能不同。稳定部署必须显式配置绝对路径，例如：

```powershell
$env:AVATAR_STORAGE_DIR="E:\employee-management-data\avatars"
$env:FILE_STORAGE_DIR="E:\employee-management-data\files"
mvn spring-boot:run
```

不要在不同启动方式之间依赖隐式相对目录。

### 多个待审批资料申请需要进一步约束

当前头像目录只保留同一员工最新的待审批头像。如果同一员工在上一条资料审批未处理时再次提交带头像的新申请，旧申请引用的待审批文件可能失效。下一步应选择并实现一种明确策略：

- 阻止同一员工同时存在多条待处理的 `PROFILE_UPDATE`；或
- 新申请自动撤销旧申请并记录审计；或
- 为每条审批独立保留文件，审批结束后再统一清理。

推荐第一种，规则最清晰，也最符合“每个员工仅一个待审批头像”的约束。

### 文件系统与数据库不是同一事务

头像移动和数据库更新无法天然处于同一个数据库事务。即使修复审批顺序，进程崩溃或磁盘异常仍可能产生数据库与磁盘不一致。后续应增加启动时或定时的头像一致性检查：

- 数据库引用但文件不存在：记录告警并回退默认头像；
- 文件存在但数据库未引用：经过保留期后清理；
- `pending` 文件没有对应待处理审批：经过保留期后清理。

### 不要放宽头像读取权限

`GET /api/profile/avatar/{name}` 当前只允许当前用户读取自己数据库中已经生效的头像。待审批头像不能通过该接口读取。若未来员工列表需要展示其他人的头像，应新增经过权限设计的接口，不要直接移除现有归属校验。

### 不要把“测试环境”当作跳过校验的理由

当前是测试环境，但手机号、邮箱、头像格式、头像大小、头像文件头、审批角色和资源归属校验仍必须保留。测试环境可以使用较宽松的部署参数，不能接受“前端拦截了就算校验完成”、手机号超位数仍能审批、或普通用户读取其他用户登录日志等行为。

## 下一步计划

按优先级建议继续，完成一项再进入下一项：

1. 修复头像审批的“先更新数据库、后移动文件”顺序，并增加失败回滚。
2. 完成头像上传、预览、审批、驳回、替换、重启持久化和缺失回退的真实验收。
3. 阻止同一员工创建多条待处理的 `PROFILE_UPDATE`，或实现旧申请自动撤销并记录审计。
4. 增加旧头像命名和目录迁移工具，或明确要求所有旧用户重新上传；不得只改数据库字符串。
5. 增加头像数据库/磁盘一致性检查、孤儿文件清理和管理员告警。
6. 接入集中式日志采集和告警；本地 JSON 输出、请求字段、三自然日保留和基础敏感字段脱敏已完成。
7. 扩充单元测试和集成测试；当前自动化测试主要只有 `ConcurrencyIntegrationTest`，不能覆盖完整头像链路。
8. 清理并确认提交内容后，再提交和推送 GitHub；推送前不得包含运行时头像、运行日志、密码、密钥或 `.idea/vcs.xml`。

## 接手验收顺序

建议严格按以下顺序执行，避免只测到“页面看起来正常”：

1. 使用固定绝对路径配置 `AVATAR_STORAGE_DIR` 和 `FILE_STORAGE_DIR`，启动服务并确认目录可写。
2. 管理员上传 JPG、PNG，确认 `FileReader` 预览、数据库路径、正式目录文件和刷新后显示均正确。
3. 普通员工上传头像，确认头像进入 `pending`，审批前旧头像不变，审批通过后才替换。
4. 分别验证审批通过、驳回、待审批文件缺失、伪造 MIME、错误文件头、超过 2MB 和非法手机号/邮箱。
5. 重启服务后重复读取头像，确认不会因工作目录变化而丢失；再验证旧头像是否只保留一个。
6. 使用两个账号验证 `/api/login-logs/me`、头像读取和通知“查看即已读”的用户边界。
7. 运行编译、前端构建、差异检查，再检查 `git status --short` 和待提交文件清单。

## 提交前检查

```powershell
git status --short
mvn -o -q -DskipTests compile
node --check src/main/resources/static/app/app.js
npm run build -- --emptyOutDir=false
git diff --check
```

提交时不要包含：

- `.idea/vcs.xml`
- `data/avatars/**`
- 真实用户头像
- 数据库密码、生产密钥或其他环境机密

建议提交信息：

```text
feat: add profile approval session settings and avatar storage
```
