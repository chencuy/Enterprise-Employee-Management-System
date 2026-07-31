# Enterprise Employee Management System

<img width="666" height="552" alt="image" src="https://github.com/user-attachments/assets/df671771-d456-4c5b-b4fc-2bb0b546dcfd" />
<img width="2555" height="1165" alt="image" src="https://github.com/user-attachments/assets/e974e3ef-088b-4146-8054-dcea783b8109" />


一个基于 Spring Boot、MyBatis、Thymeleaf、MySQL 和 Vue 3 的企业级员工管理系统。系统采用前后端分离的交互方式，后端提供 REST API，前端通过 Vue 3 在统一登录入口中根据角色渲染不同工作台。

## 技术栈

- 后端：Spring Boot 3.3.5、Spring MVC、MyBatis、Jakarta Servlet
- 前端：Vue 3、Thymeleaf、Lucide Icons、原生 CSS
- 数据库：MySQL 8.x
- 构建工具：Maven
- 运行环境：JDK 17+

## 核心功能

### 统一登录与多角色权限

系统只有一个登录页面，不同角色登录后进入统一的工作台界面。

- 左侧导航栏对所有角色统一展示：个人中心、审批中心、通知中心、消息、文件、我的考勤；管理员侧显示为「考勤管理」
- 角色专属功能通过快捷操作区域访问：
  - 管理员：员工管理、部门管理、工资管理、考勤异常、操作审计、登录日志
  - 部门主管：员工管理、考勤异常
  - 普通员工：无额外功能
- 管理员可在右上角点击「看板」查看数据统计弹窗

### 管理员数据统计看板

管理员登录后可点击右上角「看板」按钮，以弹窗形式查看数据统计看板，用于快速掌握系统整体状态。

- 员工总数
- 部门数量
- 离职人数
- 休假人数
- 本月工资支出
- 未处理审批数量
- 今日已签到人数
- 今日未签到人数

### 个人中心

- 展示姓名、电话、邮箱、性别、工资、部门、主管等个人资料
- 展示在职、离职、休假状态
- 休假状态支持展示休假结束日期
- 展示未读消息数量
- 提供快捷入口跳转到当前角色可访问的功能模块
- 员工可自主修改密码（需验证旧密码，新密码至少 8 位，修改后自动退出登录）
- 屏幕锁屏功能：员工可设置独立的锁屏密码（至少 4 位，加密存储），支持手动锁屏，重新登录时自动锁屏，解锁后可正常使用；锁屏状态由服务端维护并拦截业务 API
- 清除锁屏密码需输入当前锁屏密码验证
- 忘记锁屏密码可发起 7 天后自动解锁，期间显示倒计时
- 管理员可强制解除任意员工的锁屏密码（员工管理 → 操作列「解除锁屏」按钮）

### 员工管理

- 员工新增、删除、修改、分页查询
- 管理员可注册新员工并设置初始密码
- 管理员可修改员工密码，但不能查看明文密码
- 员工列表不展示加密后的密码
- 部门主管只能管理本部门普通员工
- 支持主管标识展示
- 支持修改在职、离职、休假状态
- 支持筛选：
  - 姓名模糊搜索或精确搜索
  - 性别
  - 部门
  - 状态：在职、离职、休假
  - 工资范围
  - 入职日期范围
- 管理员可导出员工列表为 CSV 文件（支持按姓名、性别、部门、状态筛选，导出字段防 Excel 公式注入）

### 部门管理

- 部门新增、修改、删除
- 部门分页查询
- 支持按部门名称和主管名称筛选
- 支持绑定部门主管

### 工资管理

- 工资记录分页查询
- 支持按员工姓名、变动类型、日期范围筛选
- 发放工资时自动填充员工当前工资
- 支持涨薪、降薪
- 记录工资变动历史
- 管理员执行降薪前需要二次确认
- 工资发放、涨薪、降薪会写入操作审计
- 管理员可导出工资记录为 CSV 文件（支持按员工姓名、变动类型、日期范围筛选，导出字段防 Excel 公式注入）

### 消息管理

- 管理员可以查看所有消息，并可以向所有员工发送消息
- 部门主管可以主动联系本部门普通员工
- 部门主管不能主动联系管理员，只能回复管理员发来的消息
- 普通员工可以主动联系同部门普通员工
- 普通员工不能主动联系主管或管理员，只能回复主管或管理员发来的消息
- 消息列表使用分页展示，避免消息过多导致页面持续下拉
- 消息列表采用与员工管理一致的表格行样式，分页区左侧提供发送消息入口，右侧提供上一页和下一页
- 消息内容默认只显示前 3 个字，点击消息行后展开完整内容
- 消息行只保留符合权限规则的回复按钮，不提供修改和删除操作
- 支持未读消息统计
- 支持标记已读，并在消息列表展示已读状态

### 考勤管理

- 管理员无需签到，管理员侧考勤入口显示为「考勤管理」并隐藏签到按钮
- 部门主管、普通员工需要在工作日每日签到
- 签到前需要完成字符验证码，防止机器人自动签到
- 签到时间窗口限制，默认 07:00-10:00，可通过环境变量 `ATTENDANCE_CHECK_IN_START` 和 `ATTENDANCE_CHECK_IN_END` 配置，也可由管理员在考勤页面「设置」中动态调整
- 支持工作日、节假日和补班日配置；非工作日不提示签到、不允许签到或一键全勤
- 考勤页面签到按钮旁显示当前签到时间窗口
- 验证码输入错误后自动刷新验证码并保留错误提示
- 当天未签到时，侧边栏考勤入口会显示红色未读标识
- 考勤记录使用与员工管理一致的分页表格展示
- 管理员可以查看和导出所有非越权范围内的考勤记录，导出前需二次验证当前管理员密码
- 部门主管可以查看和导出自己的考勤记录，也可以查看和导出本部门员工的考勤记录
- 部门主管不能查看或导出其他部门员工的考勤记录
- 普通员工只能查看和导出自己的考勤记录
- 导出格式为 CSV，支持按时间区间和人员筛选，导出字段防 Excel 公式注入
- 管理员可以使用一键全勤，为单个员工、多个员工、单个部门或多个部门批量补签
- 一键全勤记录来源为“管理员全勤”，并写入操作审计
- 管理员可以在「导出考勤」右侧点击「设置」，设置每日有效签到开始时间、结束时间和迟到判定时间；保存前需二次验证当前管理员密码
- 管理员可以在设置弹窗中对已经签到的在职非管理员员工执行强制缺勤；强制缺勤会清空该日签到时间，记录来源为“强制缺勤”，并写入操作审计
- 考勤签到、考勤导出、签到时间设置、强制缺勤会写入操作审计
- 系统不支持员工导入考勤文件，考勤数据以系统签到记录为准

### 考勤异常管理

- 自动识别迟到、缺勤、未签到
- 默认迟到阈值为 `09:00`，可通过 `ATTENDANCE_LATE_AFTER` 环境变量设置默认值，或由管理员在考勤设置中动态调整
- 管理员在考勤设置中调整迟到阈值后，异常统计会按最新规则计算
- 异常统计只在工作日内计算，节假日和非工作日不会误判为缺勤
- 管理员可以按部门、员工、异常类型、日期范围查询全部异常考勤
- 部门主管只能查看本部门异常考勤
- 当日无签到显示为未签到，历史日期无签到显示为缺勤；强制缺勤记录始终显示为缺勤

### 文件管理

- 管理员可以分发文件给指定部门或指定人员
- 普通用户可以查看和下载分发给自己的文件
- 管理员可以查看文件下载明细
- 管理员可以删除已经分发的文件，删除前需要二次确认
- 文件上传默认只允许办公文档、文本/CSV、图片和 ZIP，可通过 `FILE_ALLOWED_EXTENSIONS` 与 `FILE_ALLOWED_CONTENT_TYPES` 调整白名单
- 文件下载本身不再产生“已读”副作用，前端下载后通过 `POST /api/files/{id}/read` 标记阅读状态
- 文件分发、下载、删除会写入操作审计

### 审批中心

- 将休假、调岗、涨薪、离职、文件申请统一纳入审批流
- 普通员工和部门主管可以提交自己的审批申请
- 管理员可以审批全部申请
- 部门主管可以审批本部门普通员工的休假申请和文件申请
- 调岗、涨薪、离职等敏感审批由管理员处理
- 审批列表使用与员工管理一致的五行分页表格
- 待处理审批会在侧边栏显示红色标识
- 审批通过后自动执行对应业务变更：
  - 休假：更新员工休假状态和休假结束日期
  - 调岗：更新员工所属部门
  - 涨薪：生成工资变动记录并更新当前工资
  - 离职：更新员工离职状态
  - 文件申请：记录审批结果，不自动分发文件
- 审批提交、通过、驳回会写入操作审计并推送通知
- 管理员可导出审批记录为 CSV 文件（支持按类型、状态、日期范围筛选，导出字段防 Excel 公式注入）

### 公告中心

- 管理员可以发布全体公告，发布时可选择置顶
- 置顶公告在列表中排最前并显示蓝色「置顶」标签
- 管理员可随时对已有公告进行置顶/取消置顶操作
- 管理员可撤回公告（软删除，状态变为 ARCHIVED），撤回后所有用户不可见，撤回前需二次确认
- 管理员可查看每篇公告的已读统计（已读/未读人员明细）
- 普通用户和部门主管登录后需要强制确认新的公告
- 用户必须输入指定确认文本后才能关闭公告弹窗
- 所有用户可以在右上角公告入口查看历史公告

### 通知中心

- 统一汇总消息、公告、工资变动、文件分发、考勤异常、审批结果等提醒
- 通知中心使用与员工管理一致的五行分页表格
- 支持未读数量统计、单条标记已读、全部标记已读
- 点击通知可以跳转到对应的消息、公告、文件、审批或考勤页面
- 今日未签到会在通知中心显示考勤异常提示，并计入顶部通知数量

### 操作日志审计

- 管理员可以查看系统操作日志
- 支持按模块、操作人筛选
- 新增操作日志时，管理员侧边栏会显示红色未读标识
- 覆盖员工管理、部门管理、工资管理、文件管理、公告管理、休假申请、考勤管理等模块

### 登录日志

- 记录账号、IP、登录时间、登录结果和说明
- 记录成功、失败、限流、离职禁用等登录结果
- 同一账号从新会话登录并挤掉旧会话时，会在新登录日志中标记“挤下线”
- 管理员可以按账号、IP、结果、日期范围分页查询

### 安全能力

- 密码使用 PBKDF2-HMAC-SHA256 加盐哈希保存
- 登录失败限流
- CSRF Token 校验
- Session Fixation 防护
- 同一账号同一时间只允许一个有效会话，新设备登录会挤掉旧设备
- 安全响应头配置
- 最后一个有效管理员保护
- 管理员初始化通过环境变量控制，避免在代码中内置固定账号
- 删除员工、删除部门、强制解除锁屏密码、降薪、删除文件、敏感 CSV 导出等管理员高风险操作需要输入当前管理员密码二次验证
- 员工、薪资、审批、考勤等敏感 CSV 导出使用 `POST` + CSRF Token，避免导出请求被简单链接或跨站表单触发
- 前端 Vue 与 Lucide 依赖已本地化到 `static/vendor`，默认 CSP 不再允许外部 CDN 脚本；由于当前前端仍使用浏览器端 Vue 模板编译，`script-src` 暂保留 `'unsafe-eval'`
- 敏感操作会写入操作日志，便于管理员追踪变更来源
- 签到字符验证码（HMAC-SHA256 签名，5 分钟有效期，服务端生成图片，答案只保存在服务端）
- 签到时间窗口限制（默认 07:00-10:00，可配置，启动时强校验配置格式）
- 签到 IP 频率限制（每分钟 10 次，每小时 60 次）
- 屏幕锁屏功能（独立锁屏密码，PBKDF2 加密存储，手动锁屏 + 登录自动锁屏，服务端拦截锁屏会话业务 API，忘记密码 7 天自动解锁）
- CSV 导出防公式注入，避免姓名、备注、审批意见等字段以 `= + - @` 开头时被 Excel 当作公式执行
- 生产硬化校验可通过 `REQUIRE_PRODUCTION_HARDENING=true` 或 `prod` Profile 启用，启动时强制检查验证码 HMAC 密钥、HTTPS Cookie、HSTS、数据库密码和数据库连接安全参数
- 公告撤回为软删除，撤回后数据仍保留在数据库中

### 注入与越权复核

本轮对 SQL 注入、CSV/Excel 公式注入、DOM 注入和 IDOR/越权访问做了静态复核，结论如下：

- SQL 查询使用 MyBatis `#{}` 参数绑定，未发现 `${}`、可控 `ORDER BY`、可控表名/列名等直接拼接 SQL 的注入点
- 模糊查询使用 `like concat('%', #{param}, '%')`，属于绑定参数，不会把用户输入拼进 SQL 结构
- 分页参数在服务层归一化并设置上限，`limit #{size} offset #{offset}` 不接受任意 SQL 片段
- CSV 导出统一经过 `CsvExportUtils.cell`，会处理逗号、换行、引号，并对 `= + - @` 开头字段做公式注入保护
- 前端模板使用 Vue 文本插值，未使用 `v-html`；渲染失败兜底也改为 `textContent`/DOM API，避免把异常消息拼进 `innerHTML`
- 员工、部门、工资、审批、考勤、文件、通知、消息、公告、审计、登录日志等接口均在服务层做角色或资源归属校验
- 文件下载先校验管理员或接收人身份，文件已读标记使用独立 `POST /api/files/{id}/read` 并校验接收人
- 旧休假兼容接口已补齐主管审批边界：主管只能审批本部门普通员工，不能审批自己、同部门主管或管理员
- 管理员高风险操作除角色校验外，还要求输入当前管理员密码二次验证
- 考勤设置和强制缺勤仅管理员可执行，并要求输入当前管理员密码二次验证；强制缺勤只能作用于已经签到的在职非管理员员工

仍建议生产环境继续增强：

- 前端引入构建流程预编译 Vue 模板，移除 CSP 中的 `'unsafe-eval'`
- 文件上传增加魔数/内容扫描和病毒扫描，当前白名单主要基于扩展名与浏览器上报的 `Content-Type`
- 对高敏接口增加更细粒度审计、异常频率告警和可选 IP/设备风险校验

## 项目结构

```text
vue3
├── pom.xml
├── README.md
├── data
│   └── files
├── src
│   └── main
│       ├── java
│       │   └── com
│       │       └── ssm
│       │           ├── Main.java
│       │           ├── config
│       │           ├── controller
│       │           ├── dto
│       │           ├── entity
│       │           ├── mapper
│       │           └── service
│       └── resources
│           ├── application.yml
│           ├── db
│           │   └── schema.sql
│           ├── static
│           │   ├── app
│           │   │   ├── app.js
│           │   │   ├── boot.js
│           │   │   └── styles.css
│           │   └── vendor
│           │       ├── lucide.min.js
│           │       └── vue.global.prod.js
│           └── templates
│               └── login.html
└── target
```

## 数据库初始化

1. 创建并初始化数据库：

```bash
mysql -u root -p < src/main/resources/db/schema.sql
```

2. 如果需要重新创建数据库，可以先执行：

```sql
DROP DATABASE IF EXISTS employee_management;
```

然后重新导入 `schema.sql`。

## 配置说明

主要配置文件位于：

```text
src/main/resources/application.yml
```

常用环境变量：

| 变量名 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_ADDRESS` | `0.0.0.0` | 服务监听地址 |
| `DB_URL` | `jdbc:mysql://localhost:3306/employee_management...` | MySQL 连接地址 |
| `DB_USERNAME` | `root` | MySQL 用户名 |
| `DB_PASSWORD` | `123456` | MySQL 密码 |
| `FILE_STORAGE_DIR` | `./data/files` | 分发文件存储目录 |
| `FILE_ALLOWED_EXTENSIONS` | `pdf,doc,docx,xls,xlsx,ppt,pptx,txt,csv,png,jpg,jpeg,zip` | 允许上传的文件扩展名白名单 |
| `FILE_ALLOWED_CONTENT_TYPES` | 常见办公文档、文本、CSV、PNG/JPEG、ZIP MIME | 允许上传的 `Content-Type` 白名单 |
| `ATTENDANCE_LATE_AFTER` | `09:00` | 考勤迟到判定时间 |
| `ATTENDANCE_CHECK_IN_START` | `07:00` | 签到开始时间 |
| `ATTENDANCE_CHECK_IN_END` | `10:00` | 签到结束时间 |
| `ATTENDANCE_WORK_DAYS` | `MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY` | 工作日星期配置 |
| `ATTENDANCE_HOLIDAYS` | 空 | 节假日日期列表，逗号分隔，格式 `yyyy-MM-dd` |
| `ATTENDANCE_EXTRA_WORKDAYS` | 空 | 补班日期列表，逗号分隔，格式 `yyyy-MM-dd` |
| `BOOTSTRAP_ADMIN_USERNAME` | 空 | 首次启动时可自动创建管理员账号 |
| `BOOTSTRAP_ADMIN_PASSWORD` | 空 | 首次启动管理员密码，至少 8 位 |
| `BOOTSTRAP_ADMIN_NAME` | `System Administrator` | 首次启动管理员姓名 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8080` | 允许跨域来源 |
| `SESSION_COOKIE_SECURE` | `false` | 是否只允许 HTTPS Cookie |
| `HSTS_ENABLED` | `false` | 是否启用 HSTS 响应头 |
| `CAPTCHA_HMAC_KEY` | `ems-default-captcha-key-change-me` | 签到验证码 HMAC 密钥，生产环境需改为 32 位以上随机值 |
| `CONTENT_SECURITY_POLICY` | `default-src 'self'; ...` | 自定义 CSP 响应头 |
| `REQUIRE_PRODUCTION_HARDENING` | `false` | 启用生产硬化启动校验 |

> `ATTENDANCE_CHECK_IN_START`、`ATTENDANCE_CHECK_IN_END`、`ATTENDANCE_LATE_AFTER` 是系统默认值。管理员在考勤页面「设置」中保存后，会优先使用数据库表 `attendance_settings` 中的配置。

## 首次启动管理员

系统不会在数据库脚本中内置固定管理员账号。首次部署时可以通过环境变量创建管理员。

Windows CMD 示例：

```bat
set BOOTSTRAP_ADMIN_USERNAME=admin
set BOOTSTRAP_ADMIN_PASSWORD=admin123
set BOOTSTRAP_ADMIN_NAME=System Administrator
```

PowerShell 示例：

```powershell
$env:BOOTSTRAP_ADMIN_USERNAME="admin"
$env:BOOTSTRAP_ADMIN_PASSWORD="admin123"
$env:BOOTSTRAP_ADMIN_NAME="System Administrator"
```

管理员只会在数据库中不存在同名账号时创建一次。后续员工账号由管理员在员工管理页面创建。

## 启动项目

确保已安装：

- JDK 17+
- Maven 3.8+
- MySQL 8.x

启动命令：

```bash
mvn spring-boot:run
```

浏览器访问：

```text
http://localhost:8080/
```

## 局域网访问

项目默认监听所有网卡：

```yaml
server:
  address: ${SERVER_ADDRESS:0.0.0.0}
```

同一局域网内其他设备可以访问：

```text
http://服务器局域网IP:8080/
```

例如：

```text
http://192.168.1.10:8080/
```

如果只允许本机访问，可以设置：

```bash
SERVER_ADDRESS=127.0.0.1
```

## 服务器部署

### 方案一：直接使用 80 端口（最简单）

修改 `application.yml`，将端口改为 80：

```yaml
server:
  port: ${SERVER_PORT:80}
```

打包并启动：

```bash
mvn clean package -DskipTests

export DB_URL="jdbc:mysql://localhost:3306/employee_management?sslMode=REQUIRED&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4"
export DB_USERNAME="root"
export DB_PASSWORD="你的数据库密码"
export BOOTSTRAP_ADMIN_USERNAME="admin"
export BOOTSTRAP_ADMIN_PASSWORD="你的管理员密码至少8位"
export BOOTSTRAP_ADMIN_NAME="System Administrator"
export CAPTCHA_HMAC_KEY="请替换为32位以上随机字符串"
export SESSION_COOKIE_SECURE=true
export HSTS_ENABLED=true
export REQUIRE_PRODUCTION_HARDENING=true

nohup java -jar target/vue3-1.0-SNAPSHOT.jar > app.log 2>&1 &
```

直接访问 `http://你的服务器IP` 即可。

> Linux 下 80 端口需要 root 权限，可用 `sudo` 启动或使用方案二。

### 方案二：Nginx 反向代理（推荐）

**1. 安装 Nginx**

```bash
# Ubuntu / Debian
sudo apt update && sudo apt install nginx -y

# CentOS / RHEL
sudo yum install nginx -y
```

**2. 配置反向代理**

```bash
sudo vi /etc/nginx/conf.d/employee.conf
```

写入：

```nginx
server {
    listen 80;
    server_name 你的服务器IP;

    client_max_body_size 100m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

**3. 启动服务**

```bash
# 测试 Nginx 配置
sudo nginx -t

# 启动 Nginx
sudo systemctl start nginx
sudo systemctl enable nginx

# 启动 Java 应用（保持 8080 端口）
nohup java -jar target/vue3-1.0-SNAPSHOT.jar > app.log 2>&1 &
```

访问 `http://你的服务器IP` 即可。

### 开机自启动（systemd）

创建服务文件：

```bash
sudo vi /etc/systemd/system/employee.service
```

写入：

```ini
[Unit]
Description=Employee Management System
After=network.target mysql.service

[Service]
Type=simple
User=root
Environment=DB_URL=jdbc:mysql://localhost:3306/employee_management?sslMode=REQUIRED&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4
Environment=DB_USERNAME=root
Environment=DB_PASSWORD=你的数据库密码
Environment=BOOTSTRAP_ADMIN_USERNAME=admin
Environment=BOOTSTRAP_ADMIN_PASSWORD=你的管理员密码至少8位
Environment=CAPTCHA_HMAC_KEY=请替换为32位以上随机字符串
Environment=SESSION_COOKIE_SECURE=true
Environment=HSTS_ENABLED=true
Environment=REQUIRE_PRODUCTION_HARDENING=true
WorkingDirectory=/opt/employee
ExecStart=/usr/bin/java -jar vue3-1.0-SNAPSHOT.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

部署应用并启动：

```bash
# 创建目录并复制文件
sudo mkdir -p /opt/employee
sudo cp target/vue3-1.0-SNAPSHOT.jar /opt/employee/

# 启动服务
sudo systemctl daemon-reload
sudo systemctl start employee
sudo systemctl enable employee

# 查看状态
sudo systemctl status employee
sudo journalctl -u employee -f
```

## API 模块

| 模块 | 路径前缀 | 说明 |
| --- | --- | --- |
| 认证 | `/api/auth` | 登录、当前用户、退出登录 |
| 验证码 | `/api/captcha` | 签到验证码生成 |
| 个人中心 | `/api/profile` | 个人信息、未读消息、修改密码 |
| 员工 | `/api/employees` | 员工增删改查、员工选项 |
| 部门 | `/api/departments` | 部门增删改查、分页查询 |
| 工资 | `/api/salaries` | 工资记录、发放工资、涨薪、降薪 |
| 消息 | `/api/messages` | 分页消息列表、联系人、发送消息、标记已读 |
| 文件 | `/api/files` | 文件分发、分页列表、下载、下载明细、删除 |
| 公告 | `/api/announcements` | 公告列表、待确认公告、发布公告、确认阅读、置顶/取消置顶、撤回 |
| 审批 | `/api/approvals` | 审批申请分页、提交申请、审批通过、审批驳回、待处理统计 |
| 通知 | `/api/notifications` | 通知分页、未读统计、单条已读、全部已读 |
| 休假兼容接口 | `/api/leaves` | 旧休假申请、分页列表、审批、未读统计 |
| 操作审计 | `/api/audit-logs` | 操作日志分页查询、未读统计 |
| 登录日志 | `/api/login-logs` | 登录日志分页查询 |
| 考勤 | `/api/attendance` | 考勤分页、签到（含验证码）、签到时间窗口、考勤设置、强制缺勤、异常查询、一键全勤、未读统计、导出 CSV |
| 数据看板 | `/api/dashboard` | 管理员统计看板 |

## 权限规则概览

| 功能 | 管理员 | 部门主管 | 普通员工 |
| --- | --- | --- | --- |
| 查看个人中心 | 支持 | 支持 | 支持 |
| 部门管理 | 支持 | 不支持 | 不支持 |
| 员工管理 | 全部员工 | 本部门普通员工 | 不支持 |
| 工资管理 | 支持 | 不支持 | 不支持 |
| 查看所有消息 | 支持 | 不支持 | 不支持 |
| 主动发送消息 | 所有人 | 本部门普通员工 | 同部门普通员工 |
| 回复上级消息 | 支持 | 只能回复管理员 | 只能回复主管或管理员 |
| 文件管理 | 分发、下载、明细、删除 | 下载自己的文件 | 下载自己的文件 |
| 公告管理 | 发布、查看、置顶、撤回 | 查看和确认 | 查看和确认 |
| 修改密码 | 支持 | 支持 | 支持 |
| 审批中心 | 审批全部申请 | 提交自己的申请，审批本部门普通员工休假和文件申请 | 提交自己的申请 |
| 通知中心 | 查看自己的通知 | 查看自己的通知 | 查看自己的通知 |
| 操作审计 | 支持 | 不支持 | 不支持 |
| 登录日志 | 支持 | 不支持 | 不支持 |
| 考勤签到 | 无需签到 | 支持 | 支持 |
| 一键全勤 | 支持 | 不支持 | 不支持 |
| 考勤设置 | 支持 | 不支持 | 不支持 |
| 强制缺勤 | 支持 | 不支持 | 不支持 |
| 考勤异常 | 全部部门 | 本部门 | 不支持 |
| 考勤导出 | 全部员工 | 本人和本部门员工 | 本人 |
| 数据统计看板 | 支持 | 不支持 | 不支持 |

## 密码与账号说明

- 密码不会明文保存，使用 PBKDF2-HMAC-SHA256 加盐哈希存储。
- 员工列表不展示密码字段。
- 管理员可以通过员工编辑功能设置新密码。
- 员工可在个人中心自主修改密码，需验证旧密码，新密码至少 8 位、不超过 72 位，修改后自动退出登录。
- 修改密码时输入的是明文新密码，后端会自动加密保存。
- 系统不提供默认员工账号，员工账号需要管理员创建。

## 注意事项

- 前端 Vue 3 和 Lucide Icons 已改为本地静态资源，部署环境不依赖外部 CDN。
- 当前 Vue 模板仍使用运行时编译模式，CSP 中需要允许 `unsafe-eval`；如果改成前端构建产物，可以移除此项以获得更严格的安全策略。
- 部署到生产环境时建议开启 HTTPS，并设置 `SESSION_COOKIE_SECURE=true`。
- 生产环境应通过环境变量配置数据库账号和管理员初始化信息，不建议使用默认数据库密码。
- 生产环境务必修改 `CAPTCHA_HMAC_KEY`，避免使用默认验证码签名密钥。
- 公告表 `announcements` 已新增 `is_pinned`（置顶）和 `status`（状态）列，`DatabaseUpgradeInitializer` 会自动为已有数据库添加。
- 考勤设置表 `attendance_settings` 保存管理员设置的有效签到时间和迟到阈值，`DatabaseUpgradeInitializer` 会自动为已有数据库创建。
- 所有角色的左侧导航栏统一显示，角色专属功能通过快捷操作区域访问。

## 签到安全增强

为降低机器人自动签到风险，签到功能已采用组合安全策略：

### 字符验证码

- 签到前必须完成字符验证码（6 位随机字母数字，不含易混淆字符 I/l/1/O/0）
- 验证码由服务端生成，使用 HMAC-SHA256 签名防伪造
- 服务端只返回验证码图片和 token，不向前端返回答案或绘制种子
- 验证码有效期 5 分钟，过期需重新获取
- 验证码图片由服务端渲染，带随机颜色、旋转角度、干扰线和噪点
- 验证不区分大小写
- 输入错误后自动刷新验证码并保留错误提示

### 签到时间窗口

- 默认签到时间为 07:00 - 10:00
- 可通过环境变量 `ATTENDANCE_CHECK_IN_START` 和 `ATTENDANCE_CHECK_IN_END` 自定义默认值
- 管理员可在考勤管理页面点击「设置」动态调整有效签到时间和迟到阈值，保存后优先使用数据库配置
- 启动时会强校验签到时间配置格式，且开始时间必须早于结束时间
- `ATTENDANCE_LATE_AFTER` 或管理员设置的迟到阈值必须位于签到时间窗口内
- 考勤页面签到按钮旁显示当前签到时间窗口
- 不在签到时间范围内提交签到将返回 403 错误

### 工作日规则

- 默认工作日为周一至周五，可通过 `ATTENDANCE_WORK_DAYS` 调整
- `ATTENDANCE_HOLIDAYS` 可配置节假日，节假日不会生成未签到/缺勤异常
- `ATTENDANCE_EXTRA_WORKDAYS` 可配置调休补班日，补班日即使落在周末也会按工作日处理
- 非工作日不显示未签到提醒，不允许签到，也不允许一键全勤

### IP 频率限制

- 同一 IP 每分钟最多 10 次签到请求
- 同一 IP 每小时最多 60 次签到请求
- 超出限制返回 429 状态码

### 配置项

| 变量名 | 默认值 | 说明 |
| --- | --- | --- |
| `CAPTCHA_HMAC_KEY` | `ems-default-captcha-key-change-me` | 验证码签名密钥，生产环境务必修改 |
| `TRUST_FORWARDED_HEADERS` | `false` | 是否信任 X-Forwarded-For 头（反向代理场景） |
| `ATTENDANCE_CHECK_IN_START` | `07:00` | 签到开始时间 |
| `ATTENDANCE_CHECK_IN_END` | `10:00` | 签到结束时间 |
| `ATTENDANCE_LATE_AFTER` | `09:00` | 迟到判定时间 |
| `ATTENDANCE_WORK_DAYS` | `MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY` | 工作日星期配置 |
| `ATTENDANCE_HOLIDAYS` | 空 | 节假日日期列表，逗号分隔 |
| `ATTENDANCE_EXTRA_WORKDAYS` | 空 | 补班日期列表，逗号分隔 |

### 后续可进一步增强

- 结合局域网网段、设备指纹、浏览器指纹和 Session 风险评分
- 对异常签到行为做二次确认，例如极短时间多账号签到、非工作网段签到、固定脚本 User-Agent
- 支持管理员配置 IP 白名单
- 对高风险签到写入操作审计或通知管理员复核

## 更新日志

### 2026-06-22 更新

**考勤管理增强：**
- 管理员侧「我的考勤」统一显示为「考勤管理」，管理员不再参与签到、未签到提醒、缺勤统计和考勤候选人计算
- 管理员考勤页面在「导出考勤」右侧新增「设置」按钮，可配置有效签到开始时间、结束时间和迟到判定时间
- 新增强制缺勤能力：管理员可对已经签到的在职非管理员员工清空签到时间并标记为“强制缺勤”
- 强制缺勤记录会在异常管理中显示为缺勤，不会被管理员看板误算为今日已签到

**数据库变更：**
- 新增 `attendance_settings` 表，用于保存管理员动态配置的考勤时间规则

**新增 API：**
- `GET /api/attendance/settings` — 管理员获取当前考勤设置
- `PUT /api/attendance/settings` — 管理员保存考勤设置（需 CSRF 和当前管理员密码二次验证）
- `POST /api/attendance/force-absent` — 管理员强制已签到员工缺勤（需 CSRF 和当前管理员密码二次验证）

### 2026-06-19 更新

**安全修复：**
- 签到验证码改为服务端生成图片，前端只接收图片和 token，不再暴露答案或绘制种子
- 签到验证码错误、缺失等失败请求也会计入 IP 频率限制，降低暴力尝试风险
- 屏幕锁屏状态改为服务端维护，锁屏会话除登录、退出、解锁、重置等必要接口外会被后端拦截
- 登录日志“挤下线”判断改为基于真实旧会话注册结果，不再用 IP 变化推断
- CSV 导出统一防 Excel 公式注入，覆盖员工、薪资、审批和考勤导出

**考勤修复：**
- 签到时间配置启动时强校验，配置错误会直接启动失败，避免静默回退
- 新增工作日、节假日、补班日配置，非工作日不会误判未签到或缺勤
- 一键全勤在非工作日不可执行，考勤导出中 `ADMIN_FILL` 统一显示为“管理员全勤”

**健壮性增强：**
- 补充非法 JSON、非法参数类型、缺少上传字段、multipart 异常等 400 响应处理，避免误报 500
- 验证码挑战、签到 IP 限流、登录失败计数和活跃会话增加过期清理与容量上限，降低内存被随机 key 撑爆的风险
- 忘记锁屏密码自动清除改为仅清理当前登录用户的到期锁屏密码，不再由普通会话触发全局清理
- 文件分发在数据库或通知链路失败时会补偿删除刚写入的磁盘文件，减少孤儿文件
- 员工、薪资、审批、考勤 CSV 导出改为流式响应，降低大数据量导出时的内存峰值
- 管理员看板今日签到/未签到统计复用工作日规则，非工作日不再显示误导性的未签到人数
- 前端 Vue、Lucide 依赖改为本地静态资源，默认 CSP 去除外部 CDN 脚本来源
- 管理员删除员工/部门/文件、强制解除锁屏密码、降薪和敏感导出需要输入当前管理员密码二次验证
- 文件上传增加扩展名与 `Content-Type` 白名单，拒绝无扩展名或不受支持的文件类型
- 文件下载和已读标记拆分，下载使用 `GET`，已读状态通过 `POST /api/files/{id}/read` 更新
- 旧休假兼容接口补齐主管审批权限，主管只能审批本部门普通员工，不能审批同部门主管或管理员
- 前端渲染失败兜底移除动态 `innerHTML` 拼接，改用 `textContent`/DOM API

### 2026-06-12 更新

**新增功能：**
- 员工自主修改密码（个人中心 → 账号安全 → 修改密码）
- 签到时间窗口限制（默认 07:00-10:00，可配置）
- 公告置顶/撤回（管理员可置顶公告、撤回公告）
- 签到字符验证码（图形渲染，带干扰线和噪点，不区分大小写）
- 签到 IP 频率限制（每分钟 10 次，每小时 60 次）

**UI 优化：**
- 所有角色左侧导航栏统一，角色专属功能移至快捷操作区域
- 数据统计看板从个人中心移至右上角「看板」按钮（居中弹窗）
- 快捷操作改为三列一行排版
- 修复 Chrome 浏览器密码泄露误报（登录表单 autocomplete 属性优化）
- 验证码输入错误后自动刷新并保留错误提示

**数据库变更：**
- `announcements` 表新增 `is_pinned`（置顶）和 `status`（状态）列
- `DatabaseUpgradeInitializer` 自动处理存量数据库升级

**新增 API：**
- `POST /api/profile/change-password` — 员工修改密码
- `PUT /api/announcements/{id}/pin` — 公告置顶/取消置顶
- `DELETE /api/announcements/{id}` — 撤回公告
- `GET /api/attendance/check-in-window` — 获取签到时间窗口
- `POST /api/employees/export` — 导出员工列表 CSV（需 CSRF，管理员需二次验证）
- `POST /api/salaries/export` — 导出工资记录 CSV（需 CSRF，管理员需二次验证）
- `POST /api/approvals/export` — 导出审批记录 CSV（需 CSRF，管理员需二次验证）
- `POST /api/attendance/export` — 导出考勤记录 CSV（需 CSRF，管理员需二次验证；普通员工/主管按权限范围导出）
- `POST /api/files/{id}/read` — 下载后标记文件已读（接收人本人）
- `GET /api/announcements/{id}/read-stats` — 公告已读统计
- `POST /api/profile/lock-password` — 设置锁屏密码
- `DELETE /api/profile/lock-password` — 清除锁屏密码（需验证）
- `POST /api/profile/lock-screen` — 手动锁屏当前会话
- `POST /api/profile/verify-lock-password` — 验证锁屏密码
- `POST /api/profile/lock-password-reset-request` — 发起忘记锁屏密码（7 天后自动解锁）
- `POST /api/profile/lock-password-auto-clear` — 自动清除到期的锁屏密码
- `DELETE /api/employees/{id}/lock-password` — 管理员强制解除员工锁屏密码

## 生产环境待办事项

本项目目前是一个功能完整的原型系统，若要正式落地为企业管理系统，还需完成以下改进：

| # | 类别 | 待办事项 | 优先级 |
| --- | --- | --- | --- |
| 1 | 数据备份 | 实现数据库定时自动备份（mysqldump + cron） | 高 |
| 2 | HTTPS | 配置 SSL 证书，强制 HTTPS，设置 `SESSION_COOKIE_SECURE=true` | 高 |
| 3 | 文件存储 | 将本地文件存储迁移至对象存储（OSS / MinIO） | 高 |
| 4 | 日志 | 引入 SLF4J + Logback 结构化日志，配置日志轮转 | 高 |
| 5 | 密码策略 | 实现密码过期（90 天）、密码复杂度校验、历史密码检查 | 中 |
| 6 | 并发 | Session 存储迁移至 Redis，支持多实例部署 | 中 |
| 7 | 监控 | 添加 Spring Boot Actuator 健康检查、内存 / CPU 监控 | 中 |
| 8 | 数据量 | 超大导出进一步改为数据库游标/分页批量读取，并增加后台异步导出任务 | 中 |
| 9 | 前端 | 引入前端构建流程预编译 Vue 模板，移除 CSP 中的 `'unsafe-eval'` | 中 |
| 10 | 测试 | 补充单元测试和集成测试，覆盖核心业务逻辑 | 低 |

## License

本项目仅用于学习、课程设计或企业管理系统原型开发。正式商用前请补充完整的测试、审计和部署流程。
