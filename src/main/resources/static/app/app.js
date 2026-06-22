(function () {
  if (!window.Vue) {
    const app = document.getElementById('app');
    if (app) {
      app.textContent = '';
      const boot = document.createElement('div');
      boot.className = 'boot';
      const panel = document.createElement('div');
      panel.className = 'boot-error';
      const title = document.createElement('strong');
      title.textContent = '前端依赖加载失败';
      const detail = document.createElement('span');
      detail.textContent = '请检查网络或浏览器是否阻止加载 Vue 脚本。';
      panel.append(title, detail);
      boot.appendChild(panel);
      app.appendChild(boot);
    }
    return;
  }

  const { createApp } = Vue;

  const ROLE_LABELS = {
    ADMIN: '管理员',
    SUPERVISOR: '部门主管',
    EMPLOYEE: '普通员工'
  };

  const STATUS_LABELS = {
    WORKING: '在职',
    RESIGNED: '离职',
    LEAVE: '休假'
  };

  const GENDER_LABELS = {
    MALE: '男',
    FEMALE: '女',
    UNKNOWN: '未填写'
  };

  const SALARY_TYPES = {
    PAY: '工资发放',
    RAISE: '涨薪',
    DECREASE: '降薪'
  };

  const LEAVE_STATUS_LABELS = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已驳回'
  };

  const APPROVAL_TYPE_LABELS = {
    LEAVE: '休假',
    TRANSFER: '调岗',
    SALARY_RAISE: '涨薪',
    RESIGNATION: '离职',
    FILE_REQUEST: '文件申请'
  };

  const APPROVAL_STATUS_LABELS = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已驳回'
  };

  const NOTIFICATION_TYPE_LABELS = {
    MESSAGE: '消息',
    ANNOUNCEMENT: '公告',
    SALARY: '工资',
    FILE: '文件',
    APPROVAL: '审批',
    ATTENDANCE: '考勤',
    SYSTEM: '系统'
  };

  const ATTENDANCE_ANOMALY_LABELS = {
    LATE: '迟到',
    ABSENT: '缺勤',
    UNSIGNED: '未签到'
  };

  const LOGIN_RESULT_LABELS = {
    SUCCESS: '成功',
    FAILED: '失败',
    LOCKED: '限流',
    RESIGNED: '离职禁用'
  };

  const LIST_PAGE_SIZE = 5;

  function defaultEmployee() {
    return {
      username: '',
      password: '',
      name: '',
      phone: '',
      email: '',
      gender: 'UNKNOWN',
      salary: 0,
      departmentId: '',
      role: 'EMPLOYEE',
      status: 'WORKING',
      leaveEndDate: '',
      hireDate: new Date().toISOString().slice(0, 10)
    };
  }

  function defaultDepartment() {
    return {
      name: '',
      description: '',
      managerId: ''
    };
  }

  function defaultLeaveForm() {
    return {
      startDate: new Date().toISOString().slice(0, 10),
      endDate: new Date().toISOString().slice(0, 10),
      reason: '',
      reviewComment: ''
    };
  }

  function defaultApprovalForm() {
    return {
      type: 'LEAVE',
      targetDepartmentId: '',
      amount: '',
      startDate: new Date().toISOString().slice(0, 10),
      endDate: new Date().toISOString().slice(0, 10),
      fileName: '',
      reason: '',
      reviewComment: ''
    };
  }

  function defaultBulkAttendanceForm() {
    return {
      employeeIds: [],
      departmentIds: [],
      attendanceDate: new Date().toISOString().slice(0, 10),
      remark: ''
    };
  }

  function defaultAttendanceSettingsForm() {
    return {
      checkInStart: '',
      checkInEnd: '',
      lateAfter: ''
    };
  }

  function defaultAttendanceForceAbsentForm() {
    return {
      employeeId: '',
      attendanceDate: new Date().toISOString().slice(0, 10),
      remark: ''
    };
  }

  function toParams(source) {
    const params = new URLSearchParams();
    Object.entries(source).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        params.append(key, value);
      }
    });
    return params.toString();
  }

  function compactPayload(source) {
    const payload = {};
    Object.entries(source || {}).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        payload[key] = value;
      }
    });
    return payload;
  }

  function requestTimeout(options = {}) {
    return options.timeout || 8000;
  }

  const app = createApp({
    template: `
      <div v-if="loading" class="boot">系统加载中</div>

      <main v-else-if="!user" class="login-screen">
        <section class="login-panel">
          <div class="login-brand">
            <span class="brand-mark">EMS</span>
            <div>
              <h1>员工管理系统</h1>
              <p>统一登录入口</p>
            </div>
          </div>
          <form class="login-form" @submit.prevent="login">
            <label>
              <span>账号</span>
              <input v-model.trim="loginForm.username" autocomplete="username" placeholder="请输入账号">
            </label>
            <label>
              <span>密码</span>
              <input v-model="loginForm.password" type="password" autocomplete="current-password" placeholder="请输入密码">
            </label>
            <p v-if="loginError" class="form-error">{{ loginError }}</p>
            <button class="primary-button full" type="submit" :disabled="submitting">
              <i data-lucide="log-in"></i>
              <span>{{ submitting ? '登录中' : '登录' }}</span>
            </button>
          </form>
        </section>
      </main>

      <div v-else class="app-shell">
        <aside class="sidebar">
          <div class="sidebar-brand">
            <span class="brand-mark">EMS</span>
            <div>
              <strong>员工管理系统</strong>
              <small>{{ roleLabel(user.role) }}</small>
            </div>
          </div>
          <div class="user-block">
            <strong>{{ user.name }}</strong>
            <span>{{ user.departmentName || '总部管理' }}</span>
          </div>
          <nav class="nav-list">
            <button v-for="item in navigation" :key="item.view" :class="{ active: view === item.view }" @click="changeView(item.view)">
              <i :data-lucide="item.icon"></i>
              <span>{{ item.label }}</span>
              <b v-if="item.view === 'leaves' && leaveUnreadCount > 0">{{ leaveUnreadCount }}</b>
              <b v-if="item.view === 'audit' && auditUnreadCount > 0">{{ auditUnreadCount }}</b>
              <b v-if="item.view === 'messages' && unreadCount > 0">{{ unreadCount }}</b>
              <b v-if="item.view === 'files' && fileUnreadCount > 0">{{ fileUnreadCount }}</b>
              <b v-if="item.view === 'attendance' && attendanceUnreadCount > 0">{{ attendanceUnreadCount }}</b>
              <b v-if="item.view === 'approvals' && approvalUnreadCount > 0">{{ approvalUnreadCount }}</b>
              <b v-if="item.view === 'notifications' && notificationBadgeCount > 0">{{ notificationBadgeCount }}</b>
            </button>
          </nav>
          <button class="ghost-button logout-button" @click="logout">
            <i data-lucide="log-out"></i>
            <span>退出登录</span>
          </button>
        </aside>

        <main class="workspace">
          <header class="topbar">
            <div>
              <h1>{{ currentTitle }}</h1>
              <p>{{ user.name }}，{{ roleLabel(user.role) }}</p>
            </div>
            <div class="topbar-actions">
              <button v-if="isAdmin" class="message-indicator" @click="dashboardOpen = true">
                <i data-lucide="bar-chart-3"></i>
                <span>看板</span>
              </button>
              <button class="icon-button" :title="theme === 'dark' ? '切换浅色模式' : '切换深色模式'" @click="toggleTheme">
                <i :data-lucide="theme === 'dark' ? 'sun' : 'moon'"></i>
              </button>
              <button class="icon-button" title="刷新" @click="refreshCurrent">
                <i data-lucide="refresh-cw"></i>
              </button>
              <button v-if="hasLockPassword" class="icon-button" title="锁屏" @click="lockScreen">
                <i data-lucide="lock"></i>
              </button>
              <button class="message-indicator announcement-indicator" @click="openAnnouncementModal">
                <i data-lucide="megaphone"></i>
                <span>公告 {{ announcementCount }}</span>
              </button>
              <button class="message-indicator" @click="changeView('notifications')">
                <i data-lucide="bell"></i>
                <span>通知 {{ notificationBadgeCount }}</span>
              </button>
              <button class="message-indicator" @click="changeView('messages')">
                <i data-lucide="mail"></i>
                <span>未读 {{ unreadCount }}</span>
              </button>
            </div>
          </header>

          <section v-if="feedback.text" :class="['feedback', feedback.type]">
            {{ feedback.text }}
          </section>

          <div v-if="dashboardOpen" class="modal-backdrop" @click.self="dashboardOpen = false">
            <div class="form-panel modal-panel dashboard-modal">
              <div class="section-title">
                <h2>数据统计看板</h2>
                <button type="button" class="icon-button" title="关闭" @click="dashboardOpen = false"><i data-lucide="x"></i></button>
              </div>
              <div class="dashboard-grid">
                <article class="dashboard-card">
                  <span>员工总数</span>
                  <strong>{{ dashboardStats.employeeTotal || 0 }}</strong>
                </article>
                <article class="dashboard-card">
                  <span>部门数量</span>
                  <strong>{{ dashboardStats.departmentTotal || 0 }}</strong>
                </article>
                <article class="dashboard-card">
                  <span>离职人数</span>
                  <strong>{{ dashboardStats.resignedTotal || 0 }}</strong>
                </article>
                <article class="dashboard-card">
                  <span>休假人数</span>
                  <strong>{{ dashboardStats.leaveTotal || 0 }}</strong>
                </article>
                <article class="dashboard-card">
                  <span>本月工资支出</span>
                  <strong>{{ money(dashboardStats.monthlySalaryExpense) }}</strong>
                </article>
                <article class="dashboard-card">
                  <span>未处理审批</span>
                  <strong>{{ dashboardStats.pendingApprovalTotal || 0 }}</strong>
                </article>
                <article class="dashboard-card">
                  <span>今日已签到</span>
                  <strong>{{ dashboardStats.todayAttendanceSigned || 0 }}</strong>
                </article>
                <article class="dashboard-card">
                  <span>今日未签到</span>
                  <strong>{{ dashboardStats.todayAttendanceMissing || 0 }}</strong>
                </article>
              </div>
            </div>
          </div>

          <section v-if="view === 'profile'" class="profile-grid">

            <div class="info-panel">
              <div class="section-title">
                <h2>个人信息</h2>
                <span :class="['status-pill', profile?.status]">{{ statusText(profile) }}</span>
              </div>
              <dl class="info-list">
                <div><dt>姓名</dt><dd>{{ profile?.name || '-' }}</dd></div>
                <div><dt>电话</dt><dd>{{ profile?.phone || '-' }}</dd></div>
                <div><dt>邮箱</dt><dd>{{ profile?.email || '-' }}</dd></div>
                <div><dt>性别</dt><dd>{{ genderLabel(profile?.gender) }}</dd></div>
                <div><dt>工资</dt><dd>{{ money(profile?.salary) }}</dd></div>
                <div><dt>所属部门</dt><dd>{{ profile?.departmentName || '总部管理' }}</dd></div>
                <div><dt>对应主管</dt><dd>{{ profile?.managerName || '-' }}</dd></div>
                <div><dt>入职日期</dt><dd>{{ profile?.hireDate || '-' }}</dd></div>
              </dl>
            </div>

            <div class="quick-panel">
              <div class="section-title">
                <h2>快捷操作</h2>
                <span>{{ formatDate(new Date()) }}</span>
              </div>
              <div class="quick-grid">
                <button v-for="item in quickActions" :key="item.view" @click="changeView(item.view)">
                  <i :data-lucide="item.icon"></i>
                  <span>{{ item.label }}</span>
                </button>
              </div>
            </div>

            <div class="message-panel">
              <div class="section-title">
                <h2>未读消息</h2>
                <strong>{{ unreadCount }}</strong>
              </div>
              <div class="message-preview" v-if="unreadMessages.length">
                <button v-for="message in unreadMessages.slice(0, 3)" :key="message.id" @click="openMessage(message)">
                  <span>{{ message.senderName }} 发来消息</span>
                  <small>{{ formatDateTime(message.createdAt) }}</small>
                </button>
              </div>
              <p v-else class="empty-text">暂无未读消息</p>
            </div>

            <div class="password-panel">
              <div class="section-title">
                <h2>账号安全</h2>
              </div>
              <button class="ghost-button" @click="openChangePasswordModal"><i data-lucide="lock"></i><span>修改密码</span></button>
              <button class="ghost-button" @click="openLockPasswordModal"><i data-lucide="shield"></i><span>{{ hasLockPassword ? '修改锁屏密码' : '设置锁屏密码' }}</span></button>
              <button v-if="hasLockPassword" class="ghost-button danger-text" @click="openClearLockPasswordModal"><i data-lucide="shield-off"></i><span>清除锁屏密码</span></button>
            </div>

            <div v-if="changePasswordModalOpen" class="modal-backdrop" @click.self="closeChangePasswordModal">
              <form class="form-panel modal-panel" @submit.prevent="submitChangePassword">
                <div class="section-title">
                  <h2>修改密码</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeChangePasswordModal"><i data-lucide="x"></i></button>
                </div>
                <label><span>旧密码</span><input v-model="changePasswordForm.oldPassword" type="password" autocomplete="current-password" required placeholder="请输入当前密码"></label>
                <label><span>新密码</span><input v-model="changePasswordForm.newPassword" type="password" autocomplete="new-password" required placeholder="至少 8 位"></label>
                <label><span>确认新密码</span><input v-model="changePasswordForm.confirmPassword" type="password" autocomplete="new-password" required placeholder="再次输入新密码"></label>
                <p v-if="changePasswordError" class="form-error">{{ changePasswordError }}</p>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeChangePasswordModal">取消</button>
                  <button class="primary-button" type="submit" :disabled="changePasswordSubmitting"><i data-lucide="check"></i><span>{{ changePasswordSubmitting ? '提交中' : '确认修改' }}</span></button>
                </div>
              </form>
            </div>

            <div v-if="lockPasswordModalOpen" class="modal-backdrop" @click.self="closeLockPasswordModal">
              <form class="form-panel modal-panel" @submit.prevent="submitLockPassword">
                <div class="section-title">
                  <h2>{{ hasLockPassword ? '修改锁屏密码' : '设置锁屏密码' }}</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeLockPasswordModal"><i data-lucide="x"></i></button>
                </div>
                <label><span>锁屏密码</span><input v-model="lockPasswordForm.lockPassword" type="password" autocomplete="off" required placeholder="至少 4 位"></label>
                <label><span>确认密码</span><input v-model="lockPasswordForm.confirmLockPassword" type="password" autocomplete="off" required placeholder="再次输入锁屏密码"></label>
                <p v-if="lockPasswordError" class="form-error">{{ lockPasswordError }}</p>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeLockPasswordModal">取消</button>
                  <button class="primary-button" type="submit" :disabled="lockPasswordSubmitting"><i data-lucide="check"></i><span>{{ lockPasswordSubmitting ? '提交中' : '确认设置' }}</span></button>
                </div>
              </form>
            </div>

            <div v-if="clearLockPasswordModalOpen" class="modal-backdrop" @click.self="closeClearLockPasswordModal">
              <form class="form-panel modal-panel" @submit.prevent="submitClearLockPassword">
                <div class="section-title">
                  <h2>清除锁屏密码</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeClearLockPasswordModal"><i data-lucide="x"></i></button>
                </div>
                <label><span>当前锁屏密码</span><input v-model="clearLockPasswordForm.lockPassword" type="password" autocomplete="off" required placeholder="请输入当前锁屏密码"></label>
                <p v-if="clearLockPasswordError" class="form-error">{{ clearLockPasswordError }}</p>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeClearLockPasswordModal">取消</button>
                  <button class="primary-button" type="submit" :disabled="clearLockPasswordSubmitting"><i data-lucide="check"></i><span>{{ clearLockPasswordSubmitting ? '清除中' : '确认清除' }}</span></button>
                </div>
              </form>
            </div>

            <div v-if="lockPasswordResetAt" class="lock-reset-banner">
              <i data-lucide="clock"></i>
              <span>锁屏密码将于 <strong>{{ lockPasswordResetRemaining }}</strong> 后自动清除</span>
            </div>
          </section>

          <section v-if="view === 'employees'" class="work-section">
            <div class="table-panel">
              <div class="section-title">
                <h2>员工列表</h2>
                <span>共 {{ employees.total }} 条</span>
              </div>
              <div class="table-wrap list-table-wrap">
                <table class="list-table employee-table">
                  <thead>
                    <tr>
                      <th>姓名</th>
                      <th>账号</th>
                      <th>部门</th>
                      <th>角色</th>
                      <th>性别</th>
                      <th>状态</th>
                      <th>工资</th>
                      <th>入职日期</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="employee in employees.records" :key="employee.id">
                      <td>{{ employee.name }}</td>
                      <td>{{ employee.username }}</td>
                      <td>{{ employee.departmentName || '-' }}</td>
                      <td><span :class="['role-tag', employee.role]">{{ roleLabel(employee.role) }}</span></td>
                      <td>{{ genderLabel(employee.gender) }}</td>
                      <td><span :class="['status-pill', employee.status]">{{ statusText(employee) }}</span></td>
                      <td>{{ money(employee.salary) }}</td>
                      <td>{{ employee.hireDate || '-' }}</td>
                      <td class="row-actions">
                        <button v-if="isAdmin && employee.hasLockPassword" class="icon-button" title="解除锁屏" @click="adminClearLockPassword(employee)"><i data-lucide="shield-off"></i></button>
                        <button class="icon-button" title="编辑" @click="editEmployee(employee)"><i data-lucide="pencil"></i></button>
                        <button class="icon-button danger" title="删除" @click="removeEmployee(employee)"><i data-lucide="trash-2"></i></button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!employees.records.length" class="empty-text table-empty">暂无员工</p>
              </div>
              <div class="pagination inline-actions">
                <div class="pagination-actions">
                  <button v-if="isAdmin" class="primary-button" @click="openEmployeeModal"><i data-lucide="plus"></i><span>新增员工</span></button>
                  <button class="ghost-button" @click="openEmployeeFilterModal"><i data-lucide="search"></i><span>查询</span></button>
                  <button v-if="isAdmin" class="ghost-button" @click="openEmployeeExportModal"><i data-lucide="download"></i><span>导出</span></button>
                </div>
                <div class="pagination-controls">
                  <button class="ghost-button" :disabled="employeeFilter.page <= 1" @click="employeePage(employeeFilter.page - 1)">上一页</button>
                  <span>第 {{ employeeFilter.page }} 页</span>
                  <button class="ghost-button" :disabled="employeeFilter.page * employeeFilter.size >= employees.total" @click="employeePage(employeeFilter.page + 1)">下一页</button>
                </div>
              </div>
            </div>

            <div v-if="employeeFilterModalOpen" class="modal-backdrop" @click.self="closeEmployeeFilterModal">
              <form class="form-panel modal-panel wide-modal" @submit.prevent="searchEmployees">
                <div class="section-title">
                  <h2>查询员工</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeEmployeeFilterModal"><i data-lucide="x"></i></button>
                </div>
                <div class="modal-grid">
                  <label><span>姓名</span><input v-model.trim="employeeFilter.name" placeholder="姓名"></label>
                  <label><span>匹配</span><select v-model="employeeFilter.nameMode"><option value="fuzzy">模糊</option><option value="exact">精确</option></select></label>
                  <label><span>性别</span><select v-model="employeeFilter.gender"><option value="">全部</option><option value="MALE">男</option><option value="FEMALE">女</option><option value="UNKNOWN">未填写</option></select></label>
                  <label v-if="isAdmin"><span>部门</span><select v-model="employeeFilter.departmentId"><option value="">全部</option><option v-for="department in departments" :key="department.id" :value="department.id">{{ department.name }}</option></select></label>
                  <label><span>状态</span><select v-model="employeeFilter.status"><option value="">全部</option><option value="WORKING">在职</option><option value="RESIGNED">离职</option><option value="LEAVE">休假</option></select></label>
                  <label><span>最低工资</span><input v-model.number="employeeFilter.minSalary" type="number"></label>
                  <label><span>最高工资</span><input v-model.number="employeeFilter.maxSalary" type="number"></label>
                  <label><span>入职起始</span><input v-model="employeeFilter.hireDateStart" type="date"></label>
                  <label><span>入职结束</span><input v-model="employeeFilter.hireDateEnd" type="date"></label>
                </div>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="resetEmployeeFilter"><i data-lucide="rotate-ccw"></i><span>重置</span></button>
                  <button class="primary-button" type="submit"><i data-lucide="search"></i><span>查询</span></button>
                </div>
              </form>
            </div>

            <div v-if="employeeExportModalOpen" class="modal-backdrop" @click.self="closeEmployeeExportModal">
              <form class="form-panel modal-panel" @submit.prevent="exportEmployees">
                <div class="section-title">
                  <h2>导出员工</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeEmployeeExportModal"><i data-lucide="x"></i></button>
                </div>
                <label><span>姓名</span><input v-model.trim="employeeExportForm.name" placeholder="姓名"></label>
                <label><span>性别</span><select v-model="employeeExportForm.gender"><option value="">全部</option><option value="MALE">男</option><option value="FEMALE">女</option><option value="UNKNOWN">未填写</option></select></label>
                <label v-if="isAdmin"><span>部门</span><select v-model="employeeExportForm.departmentId"><option value="">全部</option><option v-for="department in departments" :key="department.id" :value="department.id">{{ department.name }}</option></select></label>
                <label><span>状态</span><select v-model="employeeExportForm.status"><option value="">全部</option><option value="WORKING">在职</option><option value="RESIGNED">离职</option><option value="LEAVE">休假</option></select></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeEmployeeExportModal">取消</button>
                  <button class="primary-button" type="submit"><i data-lucide="download"></i><span>导出文件</span></button>
                </div>
              </form>
            </div>

            <div v-if="employeeModalOpen" class="modal-backdrop" @click.self="closeEmployeeModal">
              <form class="form-panel modal-panel" @submit.prevent="saveEmployee">
                <div class="section-title">
                  <h2>{{ employeeEditing ? '编辑员工' : '新增员工' }}</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeEmployeeModal"><i data-lucide="x"></i></button>
                </div>
                <label v-if="isAdmin"><span>登录账号</span><input v-model.trim="employeeForm.username" autocomplete="off" required></label>
                <label v-if="isAdmin"><span>密码</span><input v-model="employeeForm.password" type="password" autocomplete="new-password" :required="!employeeEditing" :placeholder="employeeEditing ? '留空则不修改' : '新建时必填'"></label>
                <label><span>姓名</span><input v-model.trim="employeeForm.name" required></label>
                <label><span>电话</span><input v-model.trim="employeeForm.phone"></label>
                <label><span>邮箱</span><input v-model.trim="employeeForm.email" type="email"></label>
                <label><span>性别</span><select v-model="employeeForm.gender"><option value="MALE">男</option><option value="FEMALE">女</option><option value="UNKNOWN">未填写</option></select></label>
                <label v-if="isAdmin"><span>工资</span><input v-model.number="employeeForm.salary" type="number" min="0" step="0.01"></label>
                <label v-if="isAdmin"><span>部门</span><select v-model="employeeForm.departmentId"><option value="">总部管理</option><option v-for="department in departments" :key="department.id" :value="department.id">{{ department.name }}</option></select></label>
                <label v-if="isAdmin"><span>角色</span><select v-model="employeeForm.role"><option value="EMPLOYEE">普通员工</option><option value="SUPERVISOR">部门主管</option><option value="ADMIN">管理员</option></select></label>
                <label><span>状态</span><select v-model="employeeForm.status"><option value="WORKING">在职</option><option value="RESIGNED">离职</option><option value="LEAVE">休假</option></select></label>
                <label v-if="employeeForm.status === 'LEAVE'"><span>休假结束</span><input v-model="employeeForm.leaveEndDate" type="date"></label>
                <label><span>入职日期</span><input v-model="employeeForm.hireDate" type="date"></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeEmployeeModal">取消</button>
                  <button class="primary-button" type="submit"><i data-lucide="save"></i><span>保存员工</span></button>
                </div>
              </form>
            </div>
          </section>

          <section v-if="view === 'departments'" class="work-section">
            <div class="table-panel">
              <div class="section-title">
                <h2>部门管理</h2>
                <span>共 {{ departmentPage.total }} 个部门</span>
              </div>
              <div class="table-wrap list-table-wrap">
                <table class="list-table department-table">
                  <thead><tr><th>部门</th><th>主管</th><th>人数</th><th>说明</th><th>操作</th></tr></thead>
                  <tbody>
                    <tr v-for="department in departmentPage.records" :key="department.id">
                      <td>{{ department.name }}</td>
                      <td>{{ department.managerName || '-' }}</td>
                      <td>{{ department.employeeCount || 0 }}</td>
                      <td :title="department.description || '-'">{{ department.description || '-' }}</td>
                      <td class="row-actions">
                        <button class="icon-button" title="编辑" @click="editDepartment(department)"><i data-lucide="pencil"></i></button>
                        <button class="icon-button danger" title="删除" @click="removeDepartment(department)"><i data-lucide="trash-2"></i></button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!departmentPage.records.length" class="empty-text table-empty">暂无部门</p>
              </div>
              <div class="pagination has-actions">
                <div class="pagination-actions">
                  <button class="primary-button" @click="openDepartmentModal"><i data-lucide="plus"></i><span>新增部门</span></button>
                  <button class="ghost-button" @click="openDepartmentFilterModal"><i data-lucide="search"></i><span>查询</span></button>
                </div>
                <div class="pagination-controls">
                  <button class="ghost-button" :disabled="departmentFilter.page <= 1" @click="departmentPageTo(departmentFilter.page - 1)">上一页</button>
                  <span>第 {{ departmentFilter.page }} 页</span>
                  <button class="ghost-button" :disabled="departmentFilter.page * departmentFilter.size >= departmentPage.total" @click="departmentPageTo(departmentFilter.page + 1)">下一页</button>
                </div>
              </div>
            </div>

            <div v-if="departmentModalOpen" class="modal-backdrop" @click.self="closeDepartmentModal">
              <form class="form-panel modal-panel" @submit.prevent="saveDepartment">
                <div class="section-title">
                  <h2>{{ departmentEditing ? '编辑部门' : '新增部门' }}</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeDepartmentModal"><i data-lucide="x"></i></button>
                </div>
                <label><span>部门名称</span><input v-model.trim="departmentForm.name" required></label>
                <label><span>部门主管</span><select v-model="departmentForm.managerId"><option value="">暂不指定</option><option v-for="employee in supervisorOptions" :key="employee.id" :value="employee.id">{{ employee.name }}（{{ employee.departmentName || '未分配' }}）</option></select></label>
                <label><span>说明</span><textarea v-model.trim="departmentForm.description" rows="5"></textarea></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeDepartmentModal">取消</button>
                  <button class="primary-button" type="submit"><i data-lucide="save"></i><span>保存部门</span></button>
                </div>
              </form>
            </div>

            <div v-if="departmentFilterModalOpen" class="modal-backdrop" @click.self="closeDepartmentFilterModal">
              <form class="form-panel modal-panel" @submit.prevent="searchDepartments">
                <div class="section-title">
                  <h2>查询部门</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeDepartmentFilterModal"><i data-lucide="x"></i></button>
                </div>
                <label><span>部门名称</span><input v-model.trim="departmentFilter.name" placeholder="部门名称"></label>
                <label><span>主管姓名</span><input v-model.trim="departmentFilter.managerName" placeholder="主管姓名"></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="resetDepartmentFilter"><i data-lucide="rotate-ccw"></i><span>重置</span></button>
                  <button class="primary-button" type="submit"><i data-lucide="search"></i><span>查询</span></button>
                </div>
              </form>
            </div>
          </section>

          <section v-if="view === 'salaries'" class="work-section">
            <div class="table-panel">
              <div class="section-title">
                <h2>工资记录</h2>
                <span>共 {{ salaries.total }} 条</span>
              </div>
              <div class="table-wrap list-table-wrap">
                <table class="list-table salary-table">
                  <thead><tr><th>员工</th><th>类型</th><th>金额</th><th>当前工资</th><th>操作人</th><th>时间</th><th>备注</th></tr></thead>
                  <tbody>
                    <tr v-for="record in salaries.records" :key="record.id">
                      <td>{{ record.employeeName }}</td>
                      <td><span class="role-tag">{{ salaryType(record.changeType) }}</span></td>
                      <td>{{ money(record.amount) }}</td>
                      <td>{{ money(record.currentSalary) }}</td>
                      <td>{{ record.operatorName || '-' }}</td>
                      <td>{{ formatDateTime(record.createdAt) }}</td>
                      <td :title="record.remark || '-'">{{ record.remark || '-' }}</td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!salaries.records.length" class="empty-text table-empty">暂无工资记录</p>
              </div>
              <div class="pagination has-actions">
                <div class="pagination-actions">
                  <button class="primary-button" @click="openSalaryModal"><i data-lucide="wallet"></i><span>工资操作</span></button>
                  <button class="ghost-button" @click="openSalaryFilterModal"><i data-lucide="search"></i><span>查询</span></button>
                  <button class="ghost-button" @click="openSalaryExportModal"><i data-lucide="download"></i><span>导出</span></button>
                </div>
                <div class="pagination-controls">
                  <button class="ghost-button" :disabled="salaryFilter.page <= 1" @click="salaryPage(salaryFilter.page - 1)">上一页</button>
                  <span>第 {{ salaryFilter.page }} 页</span>
                  <button class="ghost-button" :disabled="salaryFilter.page * salaryFilter.size >= salaries.total" @click="salaryPage(salaryFilter.page + 1)">下一页</button>
                </div>
              </div>
            </div>

            <div v-if="salaryFilterModalOpen" class="modal-backdrop" @click.self="closeSalaryFilterModal">
              <form class="form-panel modal-panel" @submit.prevent="searchSalaries">
                <div class="section-title">
                  <h2>查询工资记录</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeSalaryFilterModal"><i data-lucide="x"></i></button>
                </div>
                <label><span>员工姓名</span><input v-model.trim="salaryFilter.employeeName" placeholder="姓名"></label>
                <label><span>类型</span><select v-model="salaryFilter.changeType"><option value="">全部</option><option value="PAY">工资发放</option><option value="RAISE">涨薪</option><option value="DECREASE">降薪</option></select></label>
                <label><span>开始日期</span><input v-model="salaryFilter.startDate" type="date"></label>
                <label><span>结束日期</span><input v-model="salaryFilter.endDate" type="date"></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="resetSalaryFilter"><i data-lucide="rotate-ccw"></i><span>重置</span></button>
                  <button class="primary-button" type="submit"><i data-lucide="search"></i><span>查询</span></button>
                </div>
              </form>
            </div>

            <div v-if="salaryExportModalOpen" class="modal-backdrop" @click.self="closeSalaryExportModal">
              <form class="form-panel modal-panel" @submit.prevent="exportSalaries">
                <div class="section-title">
                  <h2>导出工资记录</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeSalaryExportModal"><i data-lucide="x"></i></button>
                </div>
                <label><span>员工姓名</span><input v-model.trim="salaryExportForm.employeeName" placeholder="姓名"></label>
                <label><span>类型</span><select v-model="salaryExportForm.changeType"><option value="">全部</option><option value="PAY">工资发放</option><option value="RAISE">涨薪</option><option value="DECREASE">降薪</option></select></label>
                <label><span>开始日期</span><input v-model="salaryExportForm.startDate" type="date"></label>
                <label><span>结束日期</span><input v-model="salaryExportForm.endDate" type="date"></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeSalaryExportModal">取消</button>
                  <button class="primary-button" type="submit"><i data-lucide="download"></i><span>导出文件</span></button>
                </div>
              </form>
            </div>

            <div v-if="salaryModalOpen" class="modal-backdrop" @click.self="closeSalaryModal">
              <div class="form-panel modal-panel">
                <div class="section-title">
                  <h2>工资操作</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeSalaryModal"><i data-lucide="x"></i></button>
                </div>
                <div class="salary-current">
                  <span>当前工资</span>
                  <strong>{{ selectedSalaryEmployee ? money(selectedSalaryEmployee.salary) : '未选择' }}</strong>
                </div>
                <label><span>员工</span><select v-model="salaryForm.employeeId" @change="loadSalaryHistory"><option value="">请选择</option><option v-for="employee in employeeOptions" :key="employee.id" :value="employee.id">{{ employee.name }}（{{ employee.departmentName || '总部管理' }}）</option></select></label>
                <label><span>调整金额</span><input v-model.number="salaryForm.amount" type="number" min="0" step="0.01"></label>
                <label><span>备注</span><textarea v-model.trim="salaryForm.remark" rows="4"></textarea></label>
                <div class="button-row">
                  <button class="primary-button" @click="paySalary" type="button"><i data-lucide="send"></i><span>发放</span></button>
                  <button class="ghost-button" @click="changeSalary('raise')" type="button"><i data-lucide="trending-up"></i><span>涨薪</span></button>
                  <button class="ghost-button danger-text" @click="changeSalary('decrease')" type="button"><i data-lucide="trending-down"></i><span>降薪</span></button>
                </div>
                <div class="history-list" v-if="salaryHistory.length">
                  <h3>变动历史</h3>
                  <div v-for="item in salaryHistory" :key="item.id">
                    <span>{{ salaryType(item.changeType) }} {{ money(item.amount) }}</span>
                    <small>{{ formatDateTime(item.createdAt) }}</small>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <section v-if="view === 'audit'" class="work-section">
            <div class="table-panel">
              <div class="section-title">
                <h2>操作日志审计</h2>
                <span>共 {{ auditLogs.total }} 条</span>
              </div>
              <div class="table-wrap list-table-wrap">
                <table class="list-table audit-table">
                  <thead><tr><th>操作人</th><th>模块</th><th>动作</th><th>对象</th><th>详情</th><th>时间</th></tr></thead>
                  <tbody>
                    <tr v-for="log in auditLogs.records" :key="log.id">
                      <td>{{ log.operatorName }}</td>
                      <td>{{ log.module }}</td>
                      <td><span class="role-tag">{{ log.action }}</span></td>
                      <td :title="log.targetName || '-'">{{ log.targetName || '-' }}</td>
                      <td :title="log.detail || '-'">{{ log.detail || '-' }}</td>
                      <td>{{ formatDateTime(log.createdAt) }}</td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!auditLogs.records.length" class="empty-text table-empty">暂无操作日志</p>
              </div>
              <div class="pagination has-actions">
                <div class="pagination-actions">
                  <button class="ghost-button" @click="auditFilterModalOpen = true"><i data-lucide="search"></i><span>查询</span></button>
                </div>
                <div class="pagination-controls">
                  <button class="ghost-button" :disabled="auditFilter.page <= 1" @click="auditPage(auditFilter.page - 1)">上一页</button>
                  <span>第 {{ auditFilter.page }} 页</span>
                  <button class="ghost-button" :disabled="auditFilter.page * auditFilter.size >= auditLogs.total" @click="auditPage(auditFilter.page + 1)">下一页</button>
                </div>
              </div>
            </div>

            <div v-if="auditFilterModalOpen" class="modal-backdrop" @click.self="auditFilterModalOpen = false">
              <form class="form-panel modal-panel" @submit.prevent="searchAuditLogs">
                <div class="section-title">
                  <h2>查询操作日志</h2>
                  <button type="button" class="icon-button" title="关闭" @click="auditFilterModalOpen = false"><i data-lucide="x"></i></button>
                </div>
                <label><span>模块</span><select v-model="auditFilter.module"><option value="">全部</option><option value="员工管理">员工管理</option><option value="部门管理">部门管理</option><option value="工资管理">工资管理</option><option value="文件管理">文件管理</option><option value="公告管理">公告管理</option><option value="休假申请">休假申请</option><option value="考勤管理">考勤管理</option></select></label>
                <label><span>操作人</span><input v-model.trim="auditFilter.operatorName" placeholder="姓名"></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="resetAuditFilter"><i data-lucide="rotate-ccw"></i><span>重置</span></button>
                  <button class="primary-button" type="submit"><i data-lucide="search"></i><span>查询</span></button>
                </div>
              </form>
            </div>
          </section>

          <section v-if="view === 'leaves'" class="work-section">
            <div class="table-panel">
              <div class="section-title">
                <h2>{{ isAdmin || isSupervisor ? '休假管理' : '休假申请' }}</h2>
                <span>共 {{ leavePage.total }} 条</span>
              </div>
              <div class="table-wrap list-table-wrap">
                <table class="list-table leave-table">
                  <thead><tr><th>申请人</th><th>部门</th><th>休假时间</th><th>天数</th><th>状态</th><th>审批人</th><th>申请时间</th><th>操作</th></tr></thead>
                  <tbody>
                    <tr v-for="item in leaves" :key="item.id">
                      <td>{{ item.employeeName || '-' }}</td>
                      <td>{{ item.departmentName || '总部管理' }}</td>
                      <td>{{ item.startDate }} 至 {{ item.endDate }}</td>
                      <td>{{ leaveDays(item) }} 天</td>
                      <td><span :class="['leave-state', item.status]">{{ leaveStatusLabel(item.status) }}</span></td>
                      <td>{{ item.approverName || '-' }}</td>
                      <td>{{ formatDateTime(item.createdAt) }}</td>
                      <td class="row-actions">
                        <button v-if="canReviewLeave(item)" class="ghost-button compact-button" @click="openLeaveReview(item, 'approve')">通过</button>
                        <button v-if="canReviewLeave(item)" class="ghost-button compact-button danger-text" @click="openLeaveReview(item, 'reject')">驳回</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!leaves.length" class="empty-text table-empty">暂无休假申请</p>
              </div>
              <div class="pagination has-actions">
                <div class="pagination-actions">
                  <button v-if="!isAdmin" class="primary-button" @click="openLeaveModal"><i data-lucide="calendar-plus"></i><span>提交申请</span></button>
                </div>
                <div class="pagination-controls">
                  <button class="ghost-button" :disabled="leaveFilter.page <= 1" @click="leavePageTo(leaveFilter.page - 1)">上一页</button>
                  <span>第 {{ leaveFilter.page }} 页</span>
                  <button class="ghost-button" :disabled="leaveFilter.page * leaveFilter.size >= leavePage.total" @click="leavePageTo(leaveFilter.page + 1)">下一页</button>
                </div>
              </div>
            </div>

            <div v-if="leaveModalOpen" class="modal-backdrop" @click.self="closeLeaveModal">
              <form class="form-panel modal-panel" @submit.prevent="submitLeave">
                <div class="section-title">
                  <h2>提交休假申请</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeLeaveModal"><i data-lucide="x"></i></button>
                </div>
                <label><span>开始日期</span><input v-model="leaveForm.startDate" type="date" required></label>
                <label><span>结束日期</span><input v-model="leaveForm.endDate" type="date" required></label>
                <label><span>申请原因</span><textarea v-model.trim="leaveForm.reason" rows="5" maxlength="500" required></textarea></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeLeaveModal">取消</button>
                  <button class="primary-button" type="submit"><i data-lucide="send"></i><span>提交申请</span></button>
                </div>
              </form>
            </div>

            <div v-if="leaveReviewModalOpen" class="modal-backdrop" @click.self="closeLeaveReview">
              <form class="form-panel modal-panel" @submit.prevent="submitLeaveReview">
                <div class="section-title">
                  <h2>{{ leaveReviewAction === 'approve' ? '通过休假申请' : '驳回休假申请' }}</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeLeaveReview"><i data-lucide="x"></i></button>
                </div>
                <div class="reply-context">
                  <span>{{ leaveReviewTarget?.employeeName || '-' }}：{{ leaveReviewTarget?.startDate }} 至 {{ leaveReviewTarget?.endDate }}</span>
                </div>
                <label><span>审批意见</span><textarea v-model.trim="leaveForm.reviewComment" rows="5" maxlength="500"></textarea></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeLeaveReview">取消</button>
                  <button class="primary-button" type="submit"><i data-lucide="check"></i><span>确认</span></button>
                </div>
              </form>
            </div>
          </section>

          <section v-if="view === 'approvals'" class="work-section">
            <div class="table-panel">
              <div class="section-title">
                <h2>审批中心</h2>
                <span>共 {{ approvalPage.total }} 条</span>
              </div>
              <div class="table-wrap list-table-wrap">
                <table class="list-table approval-table">
                  <thead>
                    <tr>
                      <th>类型</th>
                      <th>申请人</th>
                      <th>部门</th>
                      <th>申请内容</th>
                      <th>状态</th>
                      <th>审批人</th>
                      <th>申请时间</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="item in approvals" :key="item.id">
                      <td><span class="role-tag">{{ approvalTypeLabel(item.type) }}</span></td>
                      <td>{{ item.applicantName || '-' }}</td>
                      <td>{{ item.applicantDepartmentName || '总部管理' }}</td>
                      <td :title="approvalDetail(item)">{{ approvalDetail(item) }}</td>
                      <td><span :class="['leave-state', item.status]">{{ approvalStatusLabel(item.status) }}</span></td>
                      <td>{{ item.reviewerName || '-' }}</td>
                      <td>{{ formatDateTime(item.createdAt) }}</td>
                      <td class="row-actions">
                        <button v-if="canReviewApproval(item)" class="ghost-button compact-button" @click="openApprovalReview(item, 'approve')">通过</button>
                        <button v-if="canReviewApproval(item)" class="ghost-button compact-button danger-text" @click="openApprovalReview(item, 'reject')">驳回</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!approvals.length" class="empty-text table-empty">暂无审批申请</p>
              </div>
              <div class="pagination inline-actions">
                <div class="pagination-actions">
                  <button v-if="!isAdmin" class="primary-button" @click="openApprovalModal"><i data-lucide="plus"></i><span>提交申请</span></button>
                  <button v-if="isAdmin" class="ghost-button" @click="openApprovalExportModal"><i data-lucide="download"></i><span>导出</span></button>
                </div>
                <div class="pagination-controls">
                  <button class="ghost-button" :disabled="approvalFilter.page <= 1" @click="approvalPageTo(approvalFilter.page - 1)">上一页</button>
                  <span>第 {{ approvalFilter.page }} 页</span>
                  <button class="ghost-button" :disabled="approvalFilter.page * approvalFilter.size >= approvalPage.total" @click="approvalPageTo(approvalFilter.page + 1)">下一页</button>
                </div>
              </div>
            </div>

            <div v-if="approvalModalOpen" class="modal-backdrop" @click.self="closeApprovalModal">
              <form class="form-panel modal-panel" @submit.prevent="submitApproval">
                <div class="section-title">
                  <h2>提交审批申请</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeApprovalModal"><i data-lucide="x"></i></button>
                </div>
                <label>
                  <span>申请类型</span>
                  <select v-model="approvalForm.type">
                    <option value="LEAVE">休假</option>
                    <option value="TRANSFER">调岗</option>
                    <option value="SALARY_RAISE">涨薪</option>
                    <option value="RESIGNATION">离职</option>
                    <option value="FILE_REQUEST">文件申请</option>
                  </select>
                </label>
                <label v-if="approvalForm.type === 'LEAVE'"><span>开始日期</span><input v-model="approvalForm.startDate" type="date" required></label>
                <label v-if="approvalForm.type === 'LEAVE'"><span>结束日期</span><input v-model="approvalForm.endDate" type="date" required></label>
                <label v-if="approvalForm.type === 'TRANSFER'">
                  <span>目标部门</span>
                  <select v-model="approvalForm.targetDepartmentId" required>
                    <option value="">请选择</option>
                    <option v-for="department in departments" :key="department.id" :value="department.id">{{ department.name }}</option>
                  </select>
                </label>
                <label v-if="approvalForm.type === 'SALARY_RAISE'"><span>申请涨薪金额</span><input v-model.number="approvalForm.amount" type="number" min="0.01" step="0.01" required></label>
                <label v-if="approvalForm.type === 'FILE_REQUEST'"><span>文件名称</span><input v-model.trim="approvalForm.fileName" maxlength="255" placeholder="需要申请的文件名称"></label>
                <label><span>申请原因</span><textarea v-model.trim="approvalForm.reason" rows="5" maxlength="500" required></textarea></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeApprovalModal">取消</button>
                  <button class="primary-button" type="submit"><i data-lucide="send"></i><span>提交申请</span></button>
                </div>
              </form>
            </div>

            <div v-if="approvalReviewModalOpen" class="modal-backdrop" @click.self="closeApprovalReview">
              <form class="form-panel modal-panel" @submit.prevent="submitApprovalReview">
                <div class="section-title">
                  <h2>{{ approvalReviewAction === 'approve' ? '通过审批' : '驳回审批' }}</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeApprovalReview"><i data-lucide="x"></i></button>
                </div>
                <div class="reply-context">
                  <span>{{ approvalReviewTarget?.applicantName || '-' }}，{{ approvalTypeLabel(approvalReviewTarget?.type) }}，{{ approvalDetail(approvalReviewTarget) }}</span>
                </div>
                <label><span>审批意见</span><textarea v-model.trim="approvalForm.reviewComment" rows="5" maxlength="500"></textarea></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeApprovalReview">取消</button>
                  <button class="primary-button" type="submit"><i data-lucide="check"></i><span>确认</span></button>
                </div>
              </form>
            </div>

            <div v-if="approvalExportModalOpen" class="modal-backdrop" @click.self="closeApprovalExportModal">
              <form class="form-panel modal-panel" @submit.prevent="exportApprovals">
                <div class="section-title">
                  <h2>导出审批记录</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeApprovalExportModal"><i data-lucide="x"></i></button>
                </div>
                <label><span>类型</span><select v-model="approvalExportForm.type"><option value="">全部</option><option value="LEAVE">休假</option><option value="TRANSFER">调岗</option><option value="SALARY_RAISE">涨薪</option><option value="RESIGNATION">离职</option><option value="FILE_REQUEST">文件申请</option></select></label>
                <label><span>状态</span><select v-model="approvalExportForm.status"><option value="">全部</option><option value="PENDING">待审批</option><option value="APPROVED">已通过</option><option value="REJECTED">已驳回</option></select></label>
                <label><span>开始日期</span><input v-model="approvalExportForm.startDate" type="date"></label>
                <label><span>结束日期</span><input v-model="approvalExportForm.endDate" type="date"></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeApprovalExportModal">取消</button>
                  <button class="primary-button" type="submit"><i data-lucide="download"></i><span>导出文件</span></button>
                </div>
              </form>
            </div>
          </section>

          <section v-if="view === 'notifications'" class="work-section">
            <div v-if="!isAdmin && attendanceUnreadCount > 0" class="notification-alert">
              <div>
                <strong>考勤异常</strong>
                <span>今日尚未签到，请及时处理。</span>
              </div>
              <button class="ghost-button compact-button" @click="changeView('attendance')">去签到</button>
            </div>
            <div class="table-panel">
              <div class="section-title">
                <h2>通知中心</h2>
                <span>共 {{ notificationPage.total }} 条</span>
              </div>
              <div class="table-wrap list-table-wrap">
                <table class="list-table notification-table">
                  <thead>
                    <tr>
                      <th>类型</th>
                      <th>标题</th>
                      <th>内容</th>
                      <th>状态</th>
                      <th>时间</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="item in notifications" :key="item.id" :class="['notification-row', { unread: !item.readFlag }]">
                      <td><span class="role-tag">{{ notificationTypeLabel(item.type) }}</span></td>
                      <td :title="item.title || '-'">{{ item.title || '-' }}</td>
                      <td class="notification-content-cell" :title="item.content || '-'">{{ item.content || '-' }}</td>
                      <td><span :class="['read-state', { unread: !item.readFlag }]">{{ item.readFlag ? '已读' : '未读' }}</span></td>
                      <td>{{ formatDateTime(item.createdAt) }}</td>
                      <td class="row-actions">
                        <button v-if="!item.readFlag" class="ghost-button compact-button" @click="markNotificationRead(item)">已读</button>
                        <button class="ghost-button compact-button" @click="openNotificationSource(item)">查看</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!notifications.length" class="empty-text table-empty">暂无通知</p>
              </div>
              <div class="pagination inline-actions">
                <div class="pagination-actions">
                  <button class="ghost-button" :disabled="notificationUnreadCount === 0" @click="markAllNotificationsRead"><i data-lucide="check-check"></i><span>全部已读</span></button>
                </div>
                <div class="pagination-controls">
                  <button class="ghost-button" :disabled="notificationFilter.page <= 1" @click="notificationPageTo(notificationFilter.page - 1)">上一页</button>
                  <span>第 {{ notificationFilter.page }} 页</span>
                  <button class="ghost-button" :disabled="notificationFilter.page * notificationFilter.size >= notificationPage.total" @click="notificationPageTo(notificationFilter.page + 1)">下一页</button>
                </div>
              </div>
            </div>
          </section>

          <section v-if="view === 'messages'" class="work-section">
            <div class="table-panel">
              <div class="section-title">
                <h2>消息列表</h2>
                <span>共 {{ messagePage.total }} 条</span>
              </div>
              <div class="table-wrap list-table-wrap message-table-wrap">
                <table class="list-table message-table">
                  <thead>
                    <tr>
                      <th>发送人</th>
                      <th>接收人</th>
                      <th>内容</th>
                      <th>角色</th>
                      <th>状态</th>
                      <th>发送时间</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="message in messages" :key="message.id" :class="['message-row', { unread: !message.readFlag && message.receiverId === user.id, expanded: expandedMessageId === message.id }]" @click="toggleMessage(message)">
                      <td>{{ message.senderName }}</td>
                      <td>{{ message.receiverName }}</td>
                      <td class="message-content-cell">{{ expandedMessageId === message.id ? message.content : messagePreview(message.content) }}</td>
                      <td><span :class="['role-tag', message.senderRole]">{{ roleLabel(message.senderRole) }}</span></td>
                      <td><span :class="['read-state', { unread: !message.readFlag }]">{{ readStateText(message) }}</span></td>
                      <td>{{ formatDateTime(message.createdAt) }}</td>
                      <td class="row-actions">
                        <button v-if="canReply(message)" class="ghost-button compact-button" @click.stop="replyTo(message)">回复</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!messages.length" class="empty-text table-empty">暂无消息</p>
              </div>
              <div class="pagination inline-actions">
                <div class="pagination-actions">
                  <button class="primary-button" @click="openMessageModal"><i data-lucide="send"></i><span>发送消息</span></button>
                </div>
                <div class="pagination-controls">
                  <button class="ghost-button" :disabled="messageFilter.page <= 1" @click="messagePageTo(messageFilter.page - 1)">上一页</button>
                  <span>第 {{ messageFilter.page }} 页</span>
                  <button class="ghost-button" :disabled="messageFilter.page * messageFilter.size >= messagePage.total" @click="messagePageTo(messageFilter.page + 1)">下一页</button>
                </div>
              </div>
            </div>

            <div v-if="messageModalOpen" class="modal-backdrop" @click.self="closeMessageModal">
              <form class="form-panel modal-panel" @submit.prevent="sendMessage">
                <div class="section-title">
                  <h2>{{ messageForm.replyToMessageId ? '回复消息' : (isAdmin ? '消息管理' : '发送消息') }}</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeMessageModal"><i data-lucide="x"></i></button>
                </div>
                <label v-if="messageForm.replyToMessageId"><span>收件人</span><input :value="replyTargetText" disabled></label>
                <label v-else><span>收件人</span><select v-model="messageForm.receiverId"><option value="">请选择</option><option v-for="contact in contacts" :key="contact.id" :value="contact.id">{{ contact.name }}（{{ roleLabel(contact.role) }}）</option></select></label>
                <div v-if="messageForm.replyToMessageId" class="reply-context">
                  <span>当前为消息回复</span>
                  <button type="button" class="ghost-button" @click="resetMessageForm">取消回复</button>
                </div>
                <label><span>内容</span><textarea v-model.trim="messageForm.content" rows="8" placeholder="请输入消息内容"></textarea></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeMessageModal">取消</button>
                  <button class="primary-button" type="submit"><i data-lucide="send"></i><span>发送消息</span></button>
                </div>
                <p v-if="!contacts.length && !messageForm.replyToMessageId" class="empty-text">暂无可联系对象</p>
              </form>
            </div>
          </section>

          <section v-if="view === 'files'" class="work-section">
            <div class="table-panel">
              <div class="section-title">
                <h2>{{ isAdmin ? '文件管理' : '我的文件' }}</h2>
                <span>共 {{ filePage.total }} 个文件</span>
              </div>
              <div class="table-wrap list-table-wrap">
                <table class="list-table file-table">
                  <thead>
                    <tr>
                      <th>文件名</th>
                      <th>大小</th>
                      <th v-if="!isAdmin">上传人</th>
                      <th v-if="isAdmin">接收情况</th>
                      <th v-else>状态</th>
                      <th>上传时间</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="file in files" :key="file.id" :class="{ 'file-row': true, unread: !isAdmin && !file.readFlag }">
                      <td class="file-name-cell" :title="file.originalName">{{ file.originalName }}</td>
                      <td>{{ formatSize(file.sizeBytes) }}</td>
                      <td v-if="!isAdmin">{{ file.uploaderName || '管理员' }}</td>
                      <td v-if="isAdmin">已下载 {{ file.downloadedCount }} / 共 {{ file.recipientCount }}</td>
                      <td v-else><span :class="['read-state', { unread: !file.readFlag }]">{{ file.readFlag ? '已下载' : '未读' }}</span></td>
                      <td>{{ formatDateTime(file.createdAt) }}</td>
                      <td class="row-actions">
                        <button class="ghost-button compact-button" @click="downloadFile(file)"><i data-lucide="download"></i><span>下载</span></button>
                        <button v-if="isAdmin" class="ghost-button compact-button" @click="openFileDetail(file)"><i data-lucide="list-checks"></i><span>明细</span></button>
                        <button v-if="isAdmin" class="ghost-button compact-button danger-text" @click="removeFile(file)"><i data-lucide="trash-2"></i><span>删除</span></button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!files.length" class="empty-text table-empty">{{ isAdmin ? '暂无文件，点击“分发文件”上传并指定可下载范围' : '暂无文件' }}</p>
              </div>
              <div class="pagination inline-actions">
                <div class="pagination-actions">
                  <button v-if="isAdmin" class="primary-button" @click="openFileModal"><i data-lucide="upload"></i><span>分发文件</span></button>
                </div>
                <div class="pagination-controls">
                  <button class="ghost-button" :disabled="fileFilter.page <= 1" @click="filePageTo(fileFilter.page - 1)">上一页</button>
                  <span>第 {{ fileFilter.page }} 页</span>
                  <button class="ghost-button" :disabled="fileFilter.page * fileFilter.size >= filePage.total" @click="filePageTo(fileFilter.page + 1)">下一页</button>
                </div>
              </div>
            </div>

            <div v-if="fileModalOpen" class="modal-backdrop" @click.self="closeFileModal">
              <form class="form-panel modal-panel wide-modal" @submit.prevent="uploadFile">
                <div class="section-title">
                  <h2>分发文件</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeFileModal"><i data-lucide="x"></i></button>
                </div>
                <label class="file-upload-field">
                  <span>选择文件（最大 25MB）</span>
                  <input type="file" @change="onFileChange">
                  <small>{{ fileForm.fileName || '未选择文件' }}</small>
                </label>
                <div class="file-target-block">
                  <div class="file-target-col">
                    <div class="file-target-title">
                      <strong>按部门分发</strong>
                      <span>部门全员可下载</span>
                    </div>
                    <div class="file-target-group">
                      <label v-for="dept in departments" :key="'d' + dept.id" class="file-target-item">
                        <input type="checkbox" :value="dept.id" v-model="fileForm.departmentIds">
                        <span>{{ dept.name }}</span>
                      </label>
                      <p v-if="!departments.length" class="empty-text">暂无部门</p>
                    </div>
                  </div>
                  <div class="file-target-col">
                    <div class="file-target-title">
                      <strong>按人员分发</strong>
                      <span>单独指定可下载人员</span>
                    </div>
                    <div class="file-target-group">
                      <label v-for="person in employeeOptions" :key="'e' + person.id" class="file-target-item">
                        <input type="checkbox" :value="person.id" v-model="fileForm.employeeIds">
                        <span>{{ person.name }}（{{ roleLabel(person.role) }}）</span>
                      </label>
                      <p v-if="!employeeOptions.length" class="empty-text">暂无人员</p>
                    </div>
                  </div>
                </div>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeFileModal">取消</button>
                  <button class="primary-button" type="submit" :disabled="fileSubmitting"><i data-lucide="upload"></i><span>{{ fileSubmitting ? '分发中' : '确认分发' }}</span></button>
                </div>
              </form>
            </div>

            <div v-if="fileDetailModalOpen" class="modal-backdrop" @click.self="closeFileDetail">
              <div class="form-panel modal-panel wide-modal">
                <div class="section-title">
                  <h2>下载明细</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeFileDetail"><i data-lucide="x"></i></button>
                </div>
                <div class="reply-context">
                  <span>{{ fileDetailTarget?.originalName || '-' }}</span>
                </div>
                <div class="table-wrap">
                  <table class="list-table file-detail-table">
                    <thead><tr><th>员工</th><th>部门</th><th>角色</th><th>状态</th><th>下载时间</th></tr></thead>
                    <tbody>
                      <tr v-for="detail in fileRecipientDetails" :key="detail.employeeId">
                        <td>{{ detail.employeeName }}</td>
                        <td>{{ detail.departmentName || '总部管理' }}</td>
                        <td><span :class="['role-tag', detail.role]">{{ roleLabel(detail.role) }}</span></td>
                        <td><span :class="['read-state', { unread: !detail.readFlag }]">{{ detail.readFlag ? '已下载' : '未下载' }}</span></td>
                        <td>{{ formatDateTime(detail.downloadedAt) }}</td>
                      </tr>
                    </tbody>
                  </table>
                  <p v-if="!fileRecipientDetails.length" class="empty-text table-empty">暂无接收人明细</p>
                </div>
              </div>
            </div>
          </section>

          <section v-if="view === 'attendance'" class="work-section">
            <div class="table-panel">
              <div class="section-title">
                <h2>{{ isAdmin ? '考勤管理' : '我的考勤' }}</h2>
                <span>共 {{ attendancePage.total }} 条</span>
              </div>
              <div class="table-wrap list-table-wrap">
                <table class="list-table attendance-table">
                  <thead>
                    <tr>
                      <th>员工</th>
                      <th>部门</th>
                      <th>日期</th>
                      <th>签到时间</th>
                      <th>来源</th>
                      <th>记录时间</th>
                      <th>备注</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="record in attendances" :key="record.id">
                      <td>{{ record.employeeName || user.name }}</td>
                      <td>{{ record.departmentName || '总部管理' }}</td>
                      <td>{{ record.attendanceDate || '-' }}</td>
                      <td>{{ formatDateTime(record.checkInAt) }}</td>
                      <td><span class="role-tag">{{ attendanceSourceLabel(record.source) }}</span></td>
                      <td>{{ formatDateTime(record.createdAt) }}</td>
                      <td :title="record.remark || '-'">{{ record.remark || '-' }}</td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!attendances.length" class="empty-text table-empty">暂无考勤记录</p>
              </div>
              <div class="pagination inline-actions">
                <div class="pagination-actions">
                  <button v-if="!isAdmin" class="primary-button" :disabled="attendanceUnreadCount === 0" @click="checkInAttendance"><i data-lucide="check-circle"></i><span>{{ attendanceUnreadCount === 0 ? '今日已签到' : '今日签到' }}</span></button>
                  <span v-if="!isAdmin && checkInWindow && attendanceUnreadCount > 0" class="check-in-window-hint">签到时间 {{ checkInWindow.start }} - {{ checkInWindow.end }}</span>
                  <button v-if="isAdmin" class="ghost-button" @click="openBulkAttendanceModal"><i data-lucide="badge-check"></i><span>一键全勤</span></button>
                  <button class="ghost-button" @click="openAttendanceFilterModal"><i data-lucide="search"></i><span>查询</span></button>
                  <button class="ghost-button" @click="openAttendanceExportModal"><i data-lucide="download"></i><span>导出考勤</span></button>
                  <button v-if="isAdmin" class="ghost-button" @click="openAttendanceSettingsModal"><i data-lucide="settings"></i><span>设置</span></button>
                </div>
                <div class="pagination-controls">
                  <button class="ghost-button" :disabled="attendanceFilter.page <= 1" @click="attendancePageTo(attendanceFilter.page - 1)">上一页</button>
                  <span>第 {{ attendanceFilter.page }} 页</span>
                  <button class="ghost-button" :disabled="attendanceFilter.page * attendanceFilter.size >= attendancePage.total" @click="attendancePageTo(attendanceFilter.page + 1)">下一页</button>
                </div>
              </div>
            </div>

            <div v-if="attendanceFilterModalOpen" class="modal-backdrop" @click.self="closeAttendanceFilterModal">
              <form class="form-panel modal-panel" @submit.prevent="searchAttendance">
                <div class="section-title">
                  <h2>查询考勤</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeAttendanceFilterModal"><i data-lucide="x"></i></button>
                </div>
                <label v-if="isAdmin || isSupervisor"><span>员工</span><select v-model="attendanceFilter.employeeId"><option value="">{{ isAdmin ? '全部' : '本人' }}</option><option v-for="employee in attendanceEmployeeOptions" :key="employee.id" :value="employee.id">{{ employee.name }}（{{ employee.departmentName || '总部管理' }}）</option></select></label>
                <label><span>开始日期</span><input v-model="attendanceFilter.startDate" type="date"></label>
                <label><span>结束日期</span><input v-model="attendanceFilter.endDate" type="date"></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="resetAttendanceFilter"><i data-lucide="rotate-ccw"></i><span>重置</span></button>
                  <button class="primary-button" type="submit"><i data-lucide="search"></i><span>查询</span></button>
                </div>
              </form>
            </div>

            <div v-if="attendanceExportModalOpen" class="modal-backdrop" @click.self="closeAttendanceExportModal">
              <form class="form-panel modal-panel" @submit.prevent="exportAttendance">
                <div class="section-title">
                  <h2>导出考勤</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeAttendanceExportModal"><i data-lucide="x"></i></button>
                </div>
                <label v-if="isAdmin || isSupervisor"><span>员工</span><select v-model="attendanceExportForm.employeeId"><option value="">{{ isAdmin ? '全部' : '本人' }}</option><option v-for="employee in attendanceEmployeeOptions" :key="employee.id" :value="employee.id">{{ employee.name }}（{{ employee.departmentName || '总部管理' }}）</option></select></label>
                <label><span>开始日期</span><input v-model="attendanceExportForm.startDate" type="date"></label>
                <label><span>结束日期</span><input v-model="attendanceExportForm.endDate" type="date"></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeAttendanceExportModal">取消</button>
                  <button class="primary-button" type="submit"><i data-lucide="download"></i><span>导出文件</span></button>
                </div>
              </form>
            </div>

            <div v-if="attendanceSettingsModalOpen" class="modal-backdrop" @click.self="closeAttendanceSettingsModal">
              <div class="form-panel modal-panel wide-modal attendance-settings-modal">
                <div class="section-title">
                  <h2>考勤设置</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeAttendanceSettingsModal"><i data-lucide="x"></i></button>
                </div>
                <form class="attendance-settings-section" @submit.prevent="saveAttendanceSettings">
                  <div class="file-target-title">
                    <strong>有效签到时间</strong>
                    <span>当前规则会影响签到、全勤和迟到判定</span>
                  </div>
                  <div class="attendance-time-grid">
                    <label><span>开始时间</span><input v-model="attendanceSettingsForm.checkInStart" type="time" required></label>
                    <label><span>结束时间</span><input v-model="attendanceSettingsForm.checkInEnd" type="time" required></label>
                    <label><span>迟到时间</span><input v-model="attendanceSettingsForm.lateAfter" type="time" required></label>
                  </div>
                  <div class="form-actions">
                    <button class="primary-button" type="submit" :disabled="attendanceSettingsSubmitting"><i data-lucide="save"></i><span>{{ attendanceSettingsSubmitting ? '保存中' : '保存设置' }}</span></button>
                  </div>
                </form>
                <form class="attendance-settings-section" @submit.prevent="forceAbsentAttendance">
                  <div class="file-target-title">
                    <strong>强制缺勤</strong>
                    <span>仅可处理已经签到的在职员工</span>
                  </div>
                  <div class="attendance-time-grid">
                    <label><span>员工</span><select v-model="attendanceForceAbsentForm.employeeId" required><option value="">请选择</option><option v-for="employee in attendanceActionEmployeeOptions" :key="'absent-' + employee.id" :value="employee.id">{{ employee.name }}（{{ employee.departmentName || '总部管理' }}）</option></select></label>
                    <label><span>日期</span><input v-model="attendanceForceAbsentForm.attendanceDate" type="date" required></label>
                    <label><span>备注</span><input v-model.trim="attendanceForceAbsentForm.remark" maxlength="400" placeholder="例如：外出未按规定补卡"></label>
                  </div>
                  <div class="form-actions">
                    <button class="ghost-button danger-text" type="submit" :disabled="attendanceForceAbsentSubmitting"><i data-lucide="user-x"></i><span>{{ attendanceForceAbsentSubmitting ? '处理中' : '强制缺勤' }}</span></button>
                  </div>
                </form>
              </div>
            </div>

            <div v-if="bulkAttendanceModalOpen" class="modal-backdrop" @click.self="closeBulkAttendanceModal">
              <form class="form-panel modal-panel wide-modal" @submit.prevent="bulkFullAttendance">
                <div class="section-title">
                  <h2>一键全勤</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeBulkAttendanceModal"><i data-lucide="x"></i></button>
                </div>
                <label><span>签到日期</span><input v-model="bulkAttendanceForm.attendanceDate" type="date" required></label>
                <div class="file-target-block">
                  <div class="file-target-col">
                    <div class="file-target-title">
                      <strong>按部门选择</strong>
                      <span>部门内在职人员</span>
                    </div>
                    <div class="file-target-group">
                      <label v-for="department in departments" :key="'bulk-d' + department.id" class="file-target-item">
                        <input type="checkbox" :value="department.id" v-model="bulkAttendanceForm.departmentIds">
                        <span>{{ department.name }}</span>
                      </label>
                    </div>
                  </div>
                  <div class="file-target-col">
                    <div class="file-target-title">
                      <strong>按人员选择</strong>
                      <span>可多选</span>
                    </div>
                    <div class="file-target-group">
                      <label v-for="employee in attendanceActionEmployeeOptions" :key="'bulk-e' + employee.id" class="file-target-item">
                        <input type="checkbox" :value="employee.id" v-model="bulkAttendanceForm.employeeIds">
                        <span>{{ employee.name }}（{{ employee.departmentName || '总部管理' }}）</span>
                      </label>
                    </div>
                  </div>
                </div>
                <label><span>备注</span><textarea v-model.trim="bulkAttendanceForm.remark" rows="4" maxlength="400" placeholder="例如：外出团建、系统故障、特殊补签"></textarea></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="closeBulkAttendanceModal">取消</button>
                  <button class="primary-button" type="submit"><i data-lucide="check"></i><span>确认全勤</span></button>
                </div>
              </form>
            </div>

            <div v-if="captchaModalOpen" class="modal-backdrop" @click.self="closeCaptchaModal">
              <div class="form-panel modal-panel captcha-panel">
                <div class="section-title">
                  <h2>安全验证</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeCaptchaModal"><i data-lucide="x"></i></button>
                </div>
                <p class="captcha-hint">请输入下方验证码（不区分大小写）</p>
                <div class="captcha-container">
                  <img v-if="captchaData && captchaData.image" class="captcha-image" :src="captchaData.image" alt="验证码">
                  <button class="ghost-button captcha-refresh" @click="refreshCaptcha" title="换一张">
                    <i data-lucide="rotate-ccw"></i><span>换一张</span>
                  </button>
                </div>
                <label><span>验证码</span><input v-model.trim="captchaInput" placeholder="请输入验证码" autocomplete="off" maxlength="6" @keyup.enter="submitCaptchaCheckIn"></label>
                <p v-if="captchaError" class="form-error captcha-error">{{ captchaError }}</p>
                <div class="form-actions captcha-actions">
                  <button class="ghost-button" @click="closeCaptchaModal">取消</button>
                  <button class="primary-button" :disabled="captchaInput.length < 6 || captchaSubmitting" @click="submitCaptchaCheckIn">
                    <i data-lucide="check-circle"></i>
                    <span>{{ captchaSubmitting ? '签到中' : '确认签到' }}</span>
                  </button>
                </div>
              </div>
            </div>
          </section>

          <section v-if="view === 'attendanceAnomalies'" class="work-section">
            <div class="table-panel">
              <div class="section-title">
                <h2>考勤异常管理</h2>
                <span>共 {{ attendanceAnomalyPage.total }} 条</span>
              </div>
              <div class="table-wrap list-table-wrap">
                <table class="list-table attendance-anomaly-table">
                  <thead><tr><th>员工</th><th>部门</th><th>日期</th><th>异常类型</th><th>签到时间</th><th>规则</th><th>说明</th></tr></thead>
                  <tbody>
                    <tr v-for="item in attendanceAnomalies" :key="item.employeeId + '-' + item.attendanceDate + '-' + item.type">
                      <td>{{ item.employeeName || '-' }}</td>
                      <td>{{ item.departmentName || '总部管理' }}</td>
                      <td>{{ item.attendanceDate || '-' }}</td>
                      <td><span :class="['read-state', { unread: item.type !== 'LATE' }]">{{ attendanceAnomalyLabel(item.type) }}</span></td>
                      <td>{{ formatDateTime(item.checkInAt) }}</td>
                      <td>{{ item.lateAfter || '-' }}</td>
                      <td :title="item.detail || '-'">{{ item.detail || '-' }}</td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!attendanceAnomalies.length" class="empty-text table-empty">暂无考勤异常</p>
              </div>
              <div class="pagination inline-actions">
                <div class="pagination-actions">
                  <button class="ghost-button" @click="openAttendanceAnomalyFilterModal"><i data-lucide="search"></i><span>查询</span></button>
                </div>
                <div class="pagination-controls">
                  <button class="ghost-button" :disabled="attendanceAnomalyFilter.page <= 1" @click="attendanceAnomalyPageTo(attendanceAnomalyFilter.page - 1)">上一页</button>
                  <span>第 {{ attendanceAnomalyFilter.page }} 页</span>
                  <button class="ghost-button" :disabled="attendanceAnomalyFilter.page * attendanceAnomalyFilter.size >= attendanceAnomalyPage.total" @click="attendanceAnomalyPageTo(attendanceAnomalyFilter.page + 1)">下一页</button>
                </div>
              </div>
            </div>

            <div v-if="attendanceAnomalyFilterModalOpen" class="modal-backdrop" @click.self="closeAttendanceAnomalyFilterModal">
              <form class="form-panel modal-panel" @submit.prevent="searchAttendanceAnomalies">
                <div class="section-title">
                  <h2>查询考勤异常</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeAttendanceAnomalyFilterModal"><i data-lucide="x"></i></button>
                </div>
                <label v-if="isAdmin"><span>部门</span><select v-model="attendanceAnomalyFilter.departmentId"><option value="">全部</option><option v-for="department in departments" :key="department.id" :value="department.id">{{ department.name }}</option></select></label>
                <label><span>员工</span><select v-model="attendanceAnomalyFilter.employeeId"><option value="">全部</option><option v-for="employee in attendanceEmployeeOptions" :key="employee.id" :value="employee.id">{{ employee.name }}（{{ employee.departmentName || '总部管理' }}）</option></select></label>
                <label><span>异常类型</span><select v-model="attendanceAnomalyFilter.type"><option value="">全部</option><option value="LATE">迟到</option><option value="ABSENT">缺勤</option><option value="UNSIGNED">未签到</option></select></label>
                <label><span>开始日期</span><input v-model="attendanceAnomalyFilter.startDate" type="date"></label>
                <label><span>结束日期</span><input v-model="attendanceAnomalyFilter.endDate" type="date"></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="resetAttendanceAnomalyFilter"><i data-lucide="rotate-ccw"></i><span>重置</span></button>
                  <button class="primary-button" type="submit"><i data-lucide="search"></i><span>查询</span></button>
                </div>
              </form>
            </div>
          </section>

          <section v-if="view === 'loginLogs'" class="work-section">
            <div class="table-panel">
              <div class="section-title">
                <h2>登录日志</h2>
                <span>共 {{ loginLogs.total }} 条</span>
              </div>
              <div class="table-wrap list-table-wrap">
                <table class="list-table login-log-table">
                  <thead><tr><th>账号</th><th>姓名</th><th>IP</th><th>结果</th><th>挤下线</th><th>时间</th><th>说明</th></tr></thead>
                  <tbody>
                    <tr v-for="log in loginLogs.records" :key="log.id">
                      <td>{{ log.username || '-' }}</td>
                      <td>{{ log.employeeName || '-' }}</td>
                      <td>{{ log.ipAddress || '-' }}</td>
                      <td><span :class="['read-state', { unread: log.result !== 'SUCCESS' }]">{{ loginResultLabel(log.result) }}</span></td>
                      <td>{{ log.kickedOffline ? '是' : '否' }}</td>
                      <td>{{ formatDateTime(log.createdAt) }}</td>
                      <td :title="log.detail || '-'">{{ log.detail || '-' }}</td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!loginLogs.records.length" class="empty-text table-empty">暂无登录日志</p>
              </div>
              <div class="pagination inline-actions">
                <div class="pagination-actions">
                  <button class="ghost-button" @click="openLoginLogFilterModal"><i data-lucide="search"></i><span>查询</span></button>
                </div>
                <div class="pagination-controls">
                  <button class="ghost-button" :disabled="loginLogFilter.page <= 1" @click="loginLogPageTo(loginLogFilter.page - 1)">上一页</button>
                  <span>第 {{ loginLogFilter.page }} 页</span>
                  <button class="ghost-button" :disabled="loginLogFilter.page * loginLogFilter.size >= loginLogs.total" @click="loginLogPageTo(loginLogFilter.page + 1)">下一页</button>
                </div>
              </div>
            </div>

            <div v-if="loginLogFilterModalOpen" class="modal-backdrop" @click.self="closeLoginLogFilterModal">
              <form class="form-panel modal-panel" @submit.prevent="searchLoginLogs">
                <div class="section-title">
                  <h2>查询登录日志</h2>
                  <button type="button" class="icon-button" title="关闭" @click="closeLoginLogFilterModal"><i data-lucide="x"></i></button>
                </div>
                <label><span>账号</span><input v-model.trim="loginLogFilter.username" placeholder="账号"></label>
                <label><span>IP</span><input v-model.trim="loginLogFilter.ipAddress" placeholder="IP 地址"></label>
                <label><span>结果</span><select v-model="loginLogFilter.result"><option value="">全部</option><option value="SUCCESS">成功</option><option value="FAILED">失败</option><option value="LOCKED">限流</option><option value="RESIGNED">离职禁用</option></select></label>
                <label><span>开始日期</span><input v-model="loginLogFilter.startDate" type="date"></label>
                <label><span>结束日期</span><input v-model="loginLogFilter.endDate" type="date"></label>
                <div class="form-actions">
                  <button type="button" class="ghost-button" @click="resetLoginLogFilter"><i data-lucide="rotate-ccw"></i><span>重置</span></button>
                  <button class="primary-button" type="submit"><i data-lucide="search"></i><span>查询</span></button>
                </div>
              </form>
            </div>
          </section>

        </main>

        <div v-if="announcementModalOpen" class="modal-backdrop" @click.self="closeAnnouncementModal">
          <div class="form-panel modal-panel wide-modal announcement-modal">
            <div class="section-title">
              <h2>公告中心</h2>
              <button type="button" class="icon-button" title="关闭" @click="closeAnnouncementModal"><i data-lucide="x"></i></button>
            </div>
            <form v-if="isAdmin" class="announcement-form" @submit.prevent="publishAnnouncement">
              <label><span>公告标题</span><input v-model.trim="announcementForm.title" maxlength="80" required></label>
              <label><span>公告内容</span><textarea v-model.trim="announcementForm.content" rows="5" maxlength="3000" required></textarea></label>
              <label class="checkbox-label"><input type="checkbox" v-model="announcementForm.isPinned"><span>置顶公告</span></label>
              <div class="form-actions">
                <button class="primary-button" type="submit" :disabled="announcementSubmitting"><i data-lucide="send"></i><span>发布公告</span></button>
              </div>
            </form>
            <div class="announcement-list">
              <article v-for="announcement in announcements" :key="announcement.id" :class="['announcement-item', { pinned: announcement.isPinned }]">
                <header>
                  <div>
                    <strong>{{ announcement.title }}</strong>
                    <span v-if="announcement.isPinned" class="pinned-tag">置顶</span>
                    <span>{{ announcement.publisherName || '管理员' }} · {{ formatDateTime(announcement.createdAt) }}</span>
                  </div>
                  <div class="announcement-header-actions">
                    <span :class="['read-state', { unread: !announcement.readFlag }]">{{ announcement.readFlag ? '已确认' : '未确认' }}</span>
                    <button v-if="isAdmin" class="ghost-button compact-button" @click="openAnnouncementReadStats(announcement)" title="已读统计">
                      <i data-lucide="bar-chart-3"></i>
                    </button>
                    <button v-if="isAdmin" class="ghost-button compact-button" @click="toggleAnnouncementPin(announcement)" :title="announcement.isPinned ? '取消置顶' : '置顶'">
                      <i :data-lucide="announcement.isPinned ? 'pin-off' : 'pin'"></i>
                    </button>
                    <button v-if="isAdmin" class="ghost-button compact-button danger-text" @click="unpublishAnnouncement(announcement)" title="撤回">
                      <i data-lucide="archive"></i>
                    </button>
                  </div>
                </header>
                <p>{{ announcement.content }}</p>
              </article>
              <p v-if="!announcements.length" class="empty-text">暂无公告</p>
            </div>
          </div>
        </div>

        <div v-if="announcementReadStatsOpen" class="modal-backdrop" @click.self="closeAnnouncementReadStats">
          <div class="form-panel modal-panel wide-modal read-stats-modal">
            <div class="section-title">
              <h2>已读统计</h2>
              <button type="button" class="icon-button" title="关闭" @click="closeAnnouncementReadStats"><i data-lucide="x"></i></button>
            </div>
            <div class="reply-context">
              <span>{{ announcementReadStatsTarget?.title || '-' }}</span>
            </div>
            <div class="read-stats-summary">
              <span>已读 <strong>{{ announcementReadStats.readCount }}</strong> 人</span>
              <span>未读 <strong>{{ announcementReadStats.unreadCount }}</strong> 人</span>
              <span>共 <strong>{{ announcementReadStats.total }}</strong> 人</span>
            </div>
            <div class="read-stats-tables">
              <div class="read-stats-section">
                <h3>已读人员</h3>
                <div class="table-wrap">
                  <table class="list-table file-detail-table">
                    <thead><tr><th>员工</th><th>部门</th><th>角色</th><th>确认时间</th></tr></thead>
                    <tbody>
                      <tr v-for="detail in announcementReadStats.readList" :key="detail.employeeId">
                        <td>{{ detail.employeeName }}</td>
                        <td>{{ detail.departmentName || '总部管理' }}</td>
                        <td><span :class="['role-tag', detail.role]">{{ roleLabel(detail.role) }}</span></td>
                        <td>{{ formatDateTime(detail.readAt) }}</td>
                      </tr>
                    </tbody>
                  </table>
                  <p v-if="!announcementReadStats.readList?.length" class="empty-text table-empty">暂无已读人员</p>
                </div>
              </div>
              <div class="read-stats-section">
                <h3>未读人员</h3>
                <div class="table-wrap">
                  <table class="list-table file-detail-table">
                    <thead><tr><th>员工</th><th>部门</th><th>角色</th></tr></thead>
                    <tbody>
                      <tr v-for="detail in announcementReadStats.unreadList" :key="detail.employeeId">
                        <td>{{ detail.employeeName }}</td>
                        <td>{{ detail.departmentName || '总部管理' }}</td>
                        <td><span :class="['role-tag', detail.role]">{{ roleLabel(detail.role) }}</span></td>
                      </tr>
                    </tbody>
                  </table>
                  <p v-if="!announcementReadStats.unreadList?.length" class="empty-text table-empty">全部已读</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="currentPendingAnnouncement" class="modal-backdrop force-modal">
          <form class="form-panel modal-panel announcement-read-panel" @submit.prevent="confirmAnnouncementRead">
            <div class="section-title">
              <h2>全体公告</h2>
              <span>需要确认阅读</span>
            </div>
            <article class="announcement-item required">
              <header>
                <div>
                  <strong>{{ currentPendingAnnouncement.title }}</strong>
                  <span>{{ currentPendingAnnouncement.publisherName || '管理员' }} · {{ formatDateTime(currentPendingAnnouncement.createdAt) }}</span>
                </div>
              </header>
              <p>{{ currentPendingAnnouncement.content }}</p>
            </article>
            <label>
              <span>请在下方输入：我已经阅读完成</span>
              <input v-model.trim="announcementConfirmText" autocomplete="off" required>
            </label>
            <button class="primary-button full" type="submit" :disabled="announcementConfirmText !== '我已经阅读完成' || announcementSubmitting">
              <i data-lucide="check"></i>
              <span>确认并关闭公告</span>
            </button>
          </form>
        </div>
        <div v-if="adminPasswordModalOpen" class="modal-backdrop force-modal">
          <form class="form-panel modal-panel" @submit.prevent="confirmAdminPassword">
            <div class="section-title">
              <h2>{{ adminPasswordTitle }}</h2>
              <button type="button" class="icon-button" title="关闭" @click="cancelAdminPassword"><i data-lucide="x"></i></button>
            </div>
            <label>
              <span>管理员当前密码</span>
              <input ref="adminPasswordInput" v-model="adminPasswordValue" type="password" autocomplete="current-password" required>
            </label>
            <p v-if="adminPasswordError" class="form-error">{{ adminPasswordError }}</p>
            <div class="form-actions">
              <button type="button" class="ghost-button" @click="cancelAdminPassword">取消</button>
              <button class="primary-button" type="submit"><i data-lucide="shield-check"></i><span>确认</span></button>
            </div>
          </form>
        </div>
      </div>

      <button v-if="!user" class="theme-toggle" :title="theme === 'dark' ? '切换浅色模式' : '切换深色模式'" @click="toggleTheme">
        <i :data-lucide="theme === 'dark' ? 'sun' : 'moon'"></i>
      </button>

      <div v-if="user && screenLocked" class="lock-screen">
        <div class="lock-panel">
          <div class="lock-brand">
            <span class="brand-mark">EMS</span>
            <h1>屏幕已锁定</h1>
            <p>{{ user.name }}，请输入锁屏密码解锁</p>
          </div>
          <form class="lock-form" @submit.prevent="unlock">
            <label>
              <span>锁屏密码</span>
              <input v-model="unlockPassword" type="password" autocomplete="off" placeholder="请输入锁屏密码" autofocus>
            </label>
            <p v-if="unlockError" class="form-error">{{ unlockError }}</p>
            <button class="primary-button full" type="submit" :disabled="unlockSubmitting">
              <i data-lucide="lock-open"></i>
              <span>{{ unlockSubmitting ? '解锁中' : '解锁' }}</span>
            </button>
          </form>
          <div class="lock-footer">
            <p v-if="lockPasswordResetRemaining" class="lock-reset-hint">
              已发起重置请求，锁屏密码将于 <strong>{{ lockPasswordResetRemaining }}</strong> 后自动清除
            </p>
            <button v-else class="ghost-button compact-button" @click="requestLockPasswordReset">
              <span>忘记锁屏密码？</span>
            </button>
          </div>
        </div>
      </div>
    `,

    data() {
      return {
        loading: false,
        submitting: false,
        theme: 'light',
        user: null,
        csrfToken: '',
        loginForm: { username: '', password: '' },
        loginError: '',
        view: 'profile',
        feedback: { type: 'success', text: '' },
        profile: null,
        dashboardOpen: false,
        dashboardStats: {
          employeeTotal: 0,
          departmentTotal: 0,
          resignedTotal: 0,
          leaveTotal: 0,
          monthlySalaryExpense: 0,
          pendingApprovalTotal: 0,
          todayAttendanceSigned: 0,
          todayAttendanceMissing: 0
        },
        unreadCount: 0,
        departments: [],
        departmentPage: { total: 0, page: 1, size: LIST_PAGE_SIZE, records: [] },
        departmentFilter: { name: '', managerName: '', page: 1, size: LIST_PAGE_SIZE },
        employeeOptions: [],
        employees: { total: 0, records: [] },
        employeeFilter: {
          name: '',
          nameMode: 'fuzzy',
          gender: '',
          departmentId: '',
          status: '',
          minSalary: '',
          maxSalary: '',
          hireDateStart: '',
          hireDateEnd: '',
          page: 1,
          size: LIST_PAGE_SIZE
        },
        employeeForm: defaultEmployee(),
        employeeEditing: false,
        employeeOriginalStatus: '',
        messages: [],
        messagePage: { total: 0, page: 1, size: LIST_PAGE_SIZE, records: [] },
        messageFilter: { page: 1, size: LIST_PAGE_SIZE },
        messageModalOpen: false,
        expandedMessageId: null,
        contacts: [],
        messageForm: { receiverId: '', replyToMessageId: null, receiverName: '', receiverRole: '', content: '' },
        announcements: [],
        pendingAnnouncements: [],
        announcementForm: { title: '', content: '', isPinned: false },
        announcementConfirmText: '',
        announcementModalOpen: false,
        announcementSubmitting: false,
        salaries: { total: 0, records: [] },
        salaryFilter: {
          employeeName: '',
          changeType: '',
          startDate: '',
          endDate: '',
          page: 1,
          size: LIST_PAGE_SIZE
        },
        auditLogs: { total: 0, records: [] },
        auditFilter: { module: '', operatorName: '', page: 1, size: LIST_PAGE_SIZE },
        auditFilterModalOpen: false,
        auditUnreadCount: 0,
        leavePage: { total: 0, page: 1, size: LIST_PAGE_SIZE, records: [] },
        leaves: [],
        leaveFilter: { page: 1, size: LIST_PAGE_SIZE },
        leaveUnreadCount: 0,
        leaveForm: defaultLeaveForm(),
        leaveModalOpen: false,
        leaveReviewModalOpen: false,
        leaveReviewAction: '',
        leaveReviewTarget: null,
        approvals: [],
        approvalPage: { total: 0, page: 1, size: LIST_PAGE_SIZE, records: [] },
        approvalFilter: { page: 1, size: LIST_PAGE_SIZE },
        approvalUnreadCount: 0,
        approvalForm: defaultApprovalForm(),
        approvalModalOpen: false,
        approvalReviewModalOpen: false,
        approvalReviewAction: '',
        approvalReviewTarget: null,
        approvalExportModalOpen: false,
        approvalExportForm: { type: '', status: '', startDate: '', endDate: '' },
        notifications: [],
        notificationPage: { total: 0, page: 1, size: LIST_PAGE_SIZE, records: [] },
        notificationFilter: { page: 1, size: LIST_PAGE_SIZE },
        notificationUnreadCount: 0,
        salaryForm: { employeeId: '', amount: '', remark: '' },
        salaryHistory: [],
        departmentForm: defaultDepartment(),
        departmentEditing: false,
        employeeModalOpen: false,
        employeeFilterModalOpen: false,
        employeeExportModalOpen: false,
        employeeExportForm: { name: '', gender: '', departmentId: '', status: '' },
        departmentModalOpen: false,
        departmentFilterModalOpen: false,
        salaryModalOpen: false,
        salaryFilterModalOpen: false,
        salaryExportModalOpen: false,
        salaryExportForm: { employeeName: '', changeType: '', startDate: '', endDate: '' },
        files: [],
        filePage: { total: 0, page: 1, size: LIST_PAGE_SIZE, records: [] },
        fileFilter: { page: 1, size: LIST_PAGE_SIZE },
        fileUnreadCount: 0,
        fileModalOpen: false,
        fileDetailModalOpen: false,
        fileDetailTarget: null,
        fileRecipientDetails: [],
        fileSubmitting: false,
        fileForm: { departmentIds: [], employeeIds: [], file: null, fileName: '' },
        attendances: [],
        attendancePage: { total: 0, page: 1, size: LIST_PAGE_SIZE, records: [] },
        attendanceFilter: { employeeId: '', startDate: '', endDate: '', page: 1, size: LIST_PAGE_SIZE },
        attendanceExportForm: { employeeId: '', startDate: '', endDate: '' },
        attendanceUnreadCount: 0,
        checkInWindow: null,
        attendanceFilterModalOpen: false,
        attendanceExportModalOpen: false,
        attendanceSettingsModalOpen: false,
        attendanceSettingsForm: defaultAttendanceSettingsForm(),
        attendanceSettingsSubmitting: false,
        attendanceForceAbsentForm: defaultAttendanceForceAbsentForm(),
        attendanceForceAbsentSubmitting: false,
        attendanceAnomalies: [],
        attendanceAnomalyPage: { total: 0, page: 1, size: LIST_PAGE_SIZE, records: [] },
        attendanceAnomalyFilter: { employeeId: '', departmentId: '', type: '', startDate: new Date().toISOString().slice(0, 10), endDate: new Date().toISOString().slice(0, 10), page: 1, size: LIST_PAGE_SIZE },
        attendanceAnomalyFilterModalOpen: false,
        bulkAttendanceModalOpen: false,
        bulkAttendanceForm: defaultBulkAttendanceForm(),
        changePasswordModalOpen: false,
        changePasswordForm: { oldPassword: '', newPassword: '', confirmPassword: '' },
        changePasswordSubmitting: false,
        changePasswordError: '',
        captchaModalOpen: false,
        captchaData: null,
        captchaInput: '',
        captchaError: '',
        captchaSubmitting: false,
        announcementReadStatsOpen: false,
        announcementReadStatsTarget: null,
        announcementReadStats: { readCount: 0, unreadCount: 0, total: 0, readList: [], unreadList: [] },
        screenLocked: false,
        hasLockPassword: false,
        lockPasswordResetAt: null,
        lockPasswordModalOpen: false,
        lockPasswordForm: { lockPassword: '', confirmLockPassword: '' },
        lockPasswordSubmitting: false,
        lockPasswordError: '',
        clearLockPasswordModalOpen: false,
        clearLockPasswordForm: { lockPassword: '' },
        clearLockPasswordSubmitting: false,
        clearLockPasswordError: '',
        unlockPassword: '',
        unlockError: '',
        unlockSubmitting: false,
        loginLogs: { total: 0, page: 1, size: LIST_PAGE_SIZE, records: [] },
        loginLogFilter: { username: '', ipAddress: '', result: '', startDate: '', endDate: '', page: 1, size: LIST_PAGE_SIZE },
        loginLogFilterModalOpen: false,
        adminPasswordModalOpen: false,
        adminPasswordTitle: '管理员密码验证',
        adminPasswordValue: '',
        adminPasswordError: '',
        adminPasswordResolver: null,
        poller: null,
        feedbackTimer: null
      };
    },

    computed: {
      isAdmin() {
        return this.user?.role === 'ADMIN';
      },
      isSupervisor() {
        return this.user?.role === 'SUPERVISOR';
      },
      lockPasswordResetRemaining() {
        if (!this.lockPasswordResetAt) return '';
        const resetAt = new Date(this.lockPasswordResetAt);
        const unlockAt = new Date(resetAt.getTime() + 7 * 24 * 60 * 60 * 1000);
        const now = new Date();
        const diff = unlockAt - now;
        if (diff <= 0) return '即将';
        const days = Math.floor(diff / (24 * 60 * 60 * 1000));
        const hours = Math.floor((diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000));
        if (days > 0) return days + ' 天 ' + hours + ' 小时';
        if (hours > 0) return hours + ' 小时';
        const minutes = Math.floor((diff % (60 * 60 * 1000)) / (60 * 1000));
        return minutes + ' 分钟';
      },
      navigation() {
        return [
          { view: 'profile', label: '个人中心', icon: 'user-round' },
          { view: 'approvals', label: '审批中心', icon: 'clipboard-check' },
          { view: 'notifications', label: '通知中心', icon: 'bell' },
          { view: 'messages', label: '消息', icon: 'mail' },
          { view: 'files', label: '文件', icon: 'folder' },
          { view: 'attendance', label: this.isAdmin ? '考勤管理' : '我的考勤', icon: 'calendar-check' }
        ];
      },
      quickActions() {
        const items = [
          { view: 'approvals', label: '审批中心', icon: 'clipboard-check' },
          { view: 'notifications', label: '通知中心', icon: 'bell' },
          { view: 'messages', label: '消息', icon: 'mail' },
          { view: 'files', label: '文件', icon: 'folder' },
          { view: 'attendance', label: this.isAdmin ? '考勤管理' : '我的考勤', icon: 'calendar-check' }
        ];
        if (this.isSupervisor || this.isAdmin) {
          items.push({ view: 'employees', label: '员工管理', icon: 'users-round' });
        }
        if (this.isAdmin) {
          items.push({ view: 'departments', label: '部门管理', icon: 'building-2' });
          items.push({ view: 'salaries', label: '工资管理', icon: 'wallet' });
          items.push({ view: 'attendanceAnomalies', label: '考勤异常', icon: 'triangle-alert' });
          items.push({ view: 'audit', label: '操作审计', icon: 'clipboard-list' });
          items.push({ view: 'loginLogs', label: '登录日志', icon: 'shield-check' });
        }
        if (this.isSupervisor) {
          items.push({ view: 'attendanceAnomalies', label: '考勤异常', icon: 'triangle-alert' });
        }
        return items;
      },
      currentTitle() {
        const all = this.navigation.concat(this.quickActions);
        const found = all.find(item => item.view === this.view);
        return found ? found.label : '工作台';
      },
      unreadMessages() {
        return this.messages.filter(message => !message.readFlag && message.receiverId === this.user?.id);
      },
      replyTargetText() {
        if (!this.messageForm.receiverName) {
          return '-';
        }
        return this.messageForm.receiverName + '（' + this.roleLabel(this.messageForm.receiverRole) + '）';
      },
      supervisorOptions() {
        return this.employeeOptions.filter(employee => employee.role === 'SUPERVISOR');
      },
      attendanceEmployeeOptions() {
        if (this.isAdmin) {
          return this.employeeOptions;
        }
        if (this.isSupervisor) {
          return this.employeeOptions;
        }
        return [];
      },
      attendanceActionEmployeeOptions() {
        return this.employeeOptions.filter(employee => employee.role !== 'ADMIN' && employee.status === 'WORKING');
      },
      selectedSalaryEmployee() {
        const id = Number(this.salaryForm.employeeId);
        return this.employeeOptions.find(employee => Number(employee.id) === id);
      },
      currentPendingAnnouncement() {
        return this.pendingAnnouncements.length ? this.pendingAnnouncements[0] : null;
      },
      announcementCount() {
        return this.announcements.length;
      },
      notificationBadgeCount() {
        return Number(this.notificationUnreadCount || 0) + Number(this.attendanceUnreadCount || 0);
      }
    },

    mounted() {
      this.initTheme();
      this.bootstrap();
      this.poller = window.setInterval(() => {
        if (this.user) {
          this.refreshMessageState();
        }
      }, 8000);
    },

    updated() {
      this.refreshIcons();
    },

    unmounted() {
      window.clearInterval(this.poller);
    },

    methods: {
      requestAdminPassword(title) {
        if (!this.isAdmin) {
          return Promise.resolve('');
        }
        if (this.adminPasswordResolver) {
          this.adminPasswordResolver(null);
        }
        this.adminPasswordTitle = title || '管理员密码验证';
        this.adminPasswordValue = '';
        this.adminPasswordError = '';
        this.adminPasswordModalOpen = true;
        this.$nextTick(() => {
          this.$refs.adminPasswordInput?.focus();
        });
        return new Promise(resolve => {
          this.adminPasswordResolver = resolve;
        });
      },
      confirmAdminPassword() {
        const password = this.adminPasswordValue;
        if (!password) {
          this.adminPasswordError = '请输入管理员当前密码';
          return;
        }
        const resolve = this.adminPasswordResolver;
        this.adminPasswordResolver = null;
        this.adminPasswordModalOpen = false;
        this.adminPasswordValue = '';
        this.adminPasswordError = '';
        if (resolve) {
          resolve(password);
        }
      },
      cancelAdminPassword() {
        const resolve = this.adminPasswordResolver;
        this.adminPasswordResolver = null;
        this.adminPasswordModalOpen = false;
        this.adminPasswordValue = '';
        this.adminPasswordError = '';
        if (resolve) {
          resolve(null);
        }
      },
      async api(url, options = {}) {
        const method = (options.method || 'GET').toUpperCase();
        const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
        if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && this.csrfToken) {
          headers['X-CSRF-Token'] = this.csrfToken;
        }
        const controller = new AbortController();
        const timer = window.setTimeout(() => controller.abort(), requestTimeout(options));
        try {
          const { timeout, ...fetchOptions } = options;
          const response = await fetch(url, {
            credentials: 'include',
            headers,
            ...fetchOptions,
            signal: controller.signal
          });
          const payload = await response.json().catch(() => ({ success: false, message: '服务器返回异常' }));
          if (!response.ok || payload.success === false) {
            if (response.status === 401 && this.user) {
              this.handleSessionExpired(payload.message || '账号已在其他设备登录，请重新登录');
            }
            if (response.status === 423 && this.user) {
              this.screenLocked = true;
              this.user.screenLocked = true;
              this.unlockPassword = '';
            }
            throw new Error(payload.message || '请求失败');
          }
          return payload.data;
        } catch (error) {
          if (error.name === 'AbortError') {
            throw new Error('请求超时，请检查服务器或数据库连接');
          }
          throw error;
        } finally {
          window.clearTimeout(timer);
        }
      },
      async downloadCsv(url, payload, filename) {
        const headers = { 'Content-Type': 'application/json' };
        if (this.csrfToken) {
          headers['X-CSRF-Token'] = this.csrfToken;
        }
        const response = await fetch(url, {
          method: 'POST',
          credentials: 'include',
          headers,
          body: JSON.stringify(compactPayload(payload))
        });
        if (!response.ok) {
          const errorPayload = await response.json().catch(() => ({ message: '导出失败' }));
          if (response.status === 401 && this.user) {
            this.handleSessionExpired(errorPayload.message || '账号已在其他设备登录，请重新登录');
            return;
          }
          if (response.status === 423 && this.user) {
            this.screenLocked = true;
            this.user.screenLocked = true;
            this.unlockPassword = '';
            return;
          }
          throw new Error(errorPayload.message || '导出失败');
        }
        const blob = await response.blob();
        const objectUrl = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = objectUrl;
        link.download = filename;
        link.rel = 'noopener';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(objectUrl);
      },
      async bootstrap() {
        try {
          this.user = await this.api('/api/auth/me', { timeout: 3000 });
          this.csrfToken = this.user?.csrfToken || '';
          this.hasLockPassword = !!(this.user && this.user.hasLockPassword);
          this.lockPasswordResetAt = this.user?.lockPasswordResetAt || null;
          this.checkLockStatus();
          await this.autoClearLockPasswords();
          this.checkLockStatus();
          if (!this.screenLocked) {
            await this.loadBaseData();
          }
        } catch (error) {
          this.user = null;
          this.csrfToken = '';
        } finally {
          this.loading = false;
          this.refreshIcons();
        }
      },
      async login() {
        this.submitting = true;
        this.loginError = '';
        try {
          this.user = await this.api('/api/auth/login', {
            method: 'POST',
            body: JSON.stringify(this.loginForm)
          });
          this.csrfToken = this.user?.csrfToken || '';
          this.hasLockPassword = !!(this.user && this.user.hasLockPassword);
          this.lockPasswordResetAt = this.user?.lockPasswordResetAt || null;
          this.checkLockStatus();
          await this.autoClearLockPasswords();
          this.view = 'profile';
          this.checkLockStatus();
          if (!this.screenLocked) {
            await this.loadBaseData();
          }
        } catch (error) {
          this.loginError = error.message;
        } finally {
          this.submitting = false;
          this.refreshIcons();
        }
      },
      async logout() {
        await this.api('/api/auth/logout', { method: 'POST' }).catch(() => null);
        this.user = null;
        this.csrfToken = '';
        this.profile = null;
        this.announcements = [];
        this.pendingAnnouncements = [];
        this.announcementConfirmText = '';
        this.announcementModalOpen = false;
        this.messageModalOpen = false;
        this.expandedMessageId = null;
        this.screenLocked = false;
        this.hasLockPassword = false;
        this.view = 'profile';
      },
      openChangePasswordModal() {
        this.changePasswordForm = { oldPassword: '', newPassword: '', confirmPassword: '' };
        this.changePasswordError = '';
        this.changePasswordModalOpen = true;
      },
      closeChangePasswordModal() {
        this.changePasswordModalOpen = false;
        this.changePasswordForm = { oldPassword: '', newPassword: '', confirmPassword: '' };
        this.changePasswordError = '';
      },
      async submitChangePassword() {
        this.changePasswordError = '';
        if (!this.changePasswordForm.oldPassword || !this.changePasswordForm.newPassword) {
          this.changePasswordError = '请填写旧密码和新密码';
          return;
        }
        if (this.changePasswordForm.newPassword.length < 8) {
          this.changePasswordError = '新密码至少 8 位';
          return;
        }
        if (this.changePasswordForm.newPassword.length > 72) {
          this.changePasswordError = '新密码不能超过 72 位';
          return;
        }
        if (this.changePasswordForm.newPassword !== this.changePasswordForm.confirmPassword) {
          this.changePasswordError = '两次输入的新密码不一致';
          return;
        }
        if (this.changePasswordForm.oldPassword === this.changePasswordForm.newPassword) {
          this.changePasswordError = '新密码不能与旧密码相同';
          return;
        }
        this.changePasswordSubmitting = true;
        try {
          await this.api('/api/profile/change-password', {
            method: 'POST',
            body: JSON.stringify({
              oldPassword: this.changePasswordForm.oldPassword,
              newPassword: this.changePasswordForm.newPassword
            })
          });
          this.showFeedback('success', '密码修改成功，请使用新密码重新登录');
          this.closeChangePasswordModal();
          await this.logout();
        } catch (error) {
          this.changePasswordError = error.message || '修改失败';
        } finally {
          this.changePasswordSubmitting = false;
        }
      },
      openLockPasswordModal() {
        this.lockPasswordForm = { lockPassword: '', confirmLockPassword: '' };
        this.lockPasswordError = '';
        this.lockPasswordModalOpen = true;
      },
      closeLockPasswordModal() {
        this.lockPasswordModalOpen = false;
        this.lockPasswordForm = { lockPassword: '', confirmLockPassword: '' };
        this.lockPasswordError = '';
      },
      async submitLockPassword() {
        this.lockPasswordError = '';
        if (!this.lockPasswordForm.lockPassword) {
          this.lockPasswordError = '请输入锁屏密码';
          return;
        }
        if (this.lockPasswordForm.lockPassword.length < 4) {
          this.lockPasswordError = '锁屏密码至少 4 位';
          return;
        }
        if (this.lockPasswordForm.lockPassword.length > 72) {
          this.lockPasswordError = '锁屏密码不能超过 72 位';
          return;
        }
        if (this.lockPasswordForm.lockPassword !== this.lockPasswordForm.confirmLockPassword) {
          this.lockPasswordError = '两次输入的密码不一致';
          return;
        }
        this.lockPasswordSubmitting = true;
        try {
          await this.api('/api/profile/lock-password', {
            method: 'POST',
            body: JSON.stringify({ lockPassword: this.lockPasswordForm.lockPassword })
          });
          this.hasLockPassword = true;
          if (this.user) {
            this.user.hasLockPassword = true;
            this.user.screenLocked = false;
          }
          this.showFeedback('success', '锁屏密码设置成功');
          this.closeLockPasswordModal();
        } catch (error) {
          this.lockPasswordError = error.message || '设置失败';
        } finally {
          this.lockPasswordSubmitting = false;
        }
      },
      openClearLockPasswordModal() {
        this.clearLockPasswordForm = { lockPassword: '' };
        this.clearLockPasswordError = '';
        this.clearLockPasswordModalOpen = true;
      },
      closeClearLockPasswordModal() {
        this.clearLockPasswordModalOpen = false;
        this.clearLockPasswordForm = { lockPassword: '' };
        this.clearLockPasswordError = '';
      },
      async submitClearLockPassword() {
        this.clearLockPasswordError = '';
        if (!this.clearLockPasswordForm.lockPassword) {
          this.clearLockPasswordError = '请输入锁屏密码';
          return;
        }
        this.clearLockPasswordSubmitting = true;
        try {
          await this.api('/api/profile/lock-password', {
            method: 'DELETE',
            body: JSON.stringify({ lockPassword: this.clearLockPasswordForm.lockPassword })
          });
          this.hasLockPassword = false;
          this.lockPasswordResetAt = null;
          if (this.user) {
            this.user.hasLockPassword = false;
            this.user.lockPasswordResetAt = null;
            this.user.screenLocked = false;
          }
          this.screenLocked = false;
          this.showFeedback('success', '锁屏密码已清除');
          this.closeClearLockPasswordModal();
        } catch (error) {
          this.clearLockPasswordError = error.message || '清除失败';
        } finally {
          this.clearLockPasswordSubmitting = false;
        }
      },
      async requestLockPasswordReset() {
        try {
          await this.api('/api/profile/lock-password-reset-request', { method: 'POST' });
          const now = new Date();
          this.lockPasswordResetAt = now.toISOString();
          if (this.user) this.user.lockPasswordResetAt = now.toISOString();
          this.showFeedback('success', '已发起重置请求，请等待 7 天后自动解锁');
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      async lockScreen() {
        this.unlockPassword = '';
        this.unlockError = '';
        try {
          await this.api('/api/profile/lock-screen', { method: 'POST' });
          this.screenLocked = true;
          if (this.user) this.user.screenLocked = true;
        } catch (error) {
          this.showFeedback('error', error.message || '锁屏失败');
        }
      },
      async unlock() {
        this.unlockError = '';
        if (!this.unlockPassword) {
          this.unlockError = '请输入锁屏密码';
          return;
        }
        this.unlockSubmitting = true;
        try {
          await this.api('/api/profile/verify-lock-password', {
            method: 'POST',
            body: JSON.stringify({ lockPassword: this.unlockPassword })
          });
          this.user = await this.api('/api/auth/me', { timeout: 3000 });
          this.csrfToken = this.user?.csrfToken || this.csrfToken;
          this.hasLockPassword = !!(this.user && this.user.hasLockPassword);
          this.lockPasswordResetAt = this.user?.lockPasswordResetAt || null;
          this.checkLockStatus();
          this.unlockPassword = '';
          if (!this.screenLocked) {
            await this.loadBaseData();
          }
        } catch (error) {
          this.unlockError = error.message || '密码错误';
        } finally {
          this.unlockSubmitting = false;
        }
      },
      checkLockStatus() {
        this.hasLockPassword = !!(this.user && this.user.hasLockPassword);
        this.lockPasswordResetAt = this.user?.lockPasswordResetAt || null;
        this.screenLocked = !!(this.user && this.user.hasLockPassword && this.user.screenLocked);
      },
      async autoClearLockPasswords() {
        try {
          const cleared = await this.api('/api/profile/lock-password-auto-clear', { method: 'POST' });
          if (cleared && this.user) {
            this.user = await this.api('/api/auth/me', { timeout: 3000 });
            this.hasLockPassword = !!(this.user && this.user.hasLockPassword);
            this.lockPasswordResetAt = this.user?.lockPasswordResetAt || null;
            this.checkLockStatus();
          }
        } catch (e) {
          // ignore auto-clear errors
        }
      },
      handleSessionExpired(message) {
        this.user = null;
        this.csrfToken = '';
        this.profile = null;
        this.announcements = [];
        this.pendingAnnouncements = [];
        this.announcementConfirmText = '';
        this.announcementModalOpen = false;
        this.messageModalOpen = false;
        this.expandedMessageId = null;
        this.screenLocked = false;
        this.hasLockPassword = false;
        this.view = 'profile';
        this.loginError = message;
      },
      async loadBaseData() {
        await Promise.all([
          this.loadProfile(),
          this.loadUnread(),
          this.loadDepartments(),
          this.loadMessages(),
          this.loadContacts(),
          this.loadEmployeeOptions(),
          this.loadAnnouncements(),
          this.loadPendingAnnouncements(),
          this.loadFiles(),
          this.loadFileUnread(),
          this.loadAttendance(),
          this.loadAttendanceUnread(),
          this.loadCheckInWindow(),
          this.loadLeaves(),
          this.loadLeaveUnreadCount(),
          this.loadApprovals(),
          this.loadApprovalUnreadCount(),
          this.loadNotifications(),
          this.loadNotificationUnreadCount()
        ]);
        if (this.isAdmin || this.isSupervisor) {
          await this.loadEmployees();
        }
        if (this.isAdmin) {
          await Promise.all([this.loadDepartmentPage(), this.loadSalaries(), this.loadAuditLogs(), this.loadAuditUnreadCount(), this.loadDashboardStats()]);
        }
      },
      async changeView(nextView) {
        this.view = nextView;
        await this.refreshCurrent();
      },
      async refreshCurrent() {
        if (this.view === 'profile') {
          await this.loadProfile();
          await this.loadUnread();
          if (this.isAdmin) {
            await this.loadDashboardStats();
          }
        }
        if (this.view === 'employees') {
          await this.loadEmployees();
        }
        if (this.view === 'departments') {
          await Promise.all([this.loadDepartments(), this.loadDepartmentPage(), this.loadEmployeeOptions()]);
        }
        if (this.view === 'salaries') {
          await Promise.all([this.loadSalaries(), this.loadEmployeeOptions()]);
        }
        if (this.view === 'audit') {
          await this.loadAuditLogs();
          this.markAuditSeen();
        }
        if (this.view === 'loginLogs') {
          await this.loadLoginLogs();
        }
        if (this.view === 'leaves') {
          await Promise.all([this.loadLeaves(), this.loadLeaveUnreadCount()]);
        }
        if (this.view === 'approvals') {
          await Promise.all([this.loadApprovals(), this.loadApprovalUnreadCount(), this.loadDepartments()]);
        }
        if (this.view === 'notifications') {
          await Promise.all([this.loadNotifications(), this.loadNotificationUnreadCount(), this.loadAttendanceUnread()]);
        }
        if (this.view === 'messages') {
          await Promise.all([this.loadMessages(), this.loadContacts(), this.loadUnread()]);
        }
        if (this.view === 'files') {
          await Promise.all([this.loadFiles(), this.loadFileUnread()]);
        }
        if (this.view === 'attendance') {
          await Promise.all([this.loadAttendance(), this.loadAttendanceUnread(), this.loadCheckInWindow()]);
        }
        if (this.view === 'attendanceAnomalies') {
          await Promise.all([this.loadAttendanceAnomalies(), this.loadDepartments(), this.loadEmployeeOptions()]);
        }
      },
      async loadProfile() {
        this.profile = await this.api('/api/profile');
      },
      async loadUnread() {
        this.unreadCount = await this.api('/api/profile/unread-count');
      },
      async loadDashboardStats() {
        if (!this.isAdmin) {
          return;
        }
        this.dashboardStats = await this.api('/api/dashboard/admin');
      },
      async refreshMessageState() {
        try {
          await this.loadUnread();
          await Promise.all([
            this.loadAnnouncements(),
            this.loadPendingAnnouncements(),
            this.loadFileUnread(),
            this.loadAttendanceUnread(),
            this.loadLeaveUnreadCount(),
            this.loadAuditUnreadCount(),
            this.loadApprovalUnreadCount(),
            this.loadNotificationUnreadCount()
          ]);
          if (this.view === 'messages') {
            await this.loadMessages();
          }
          if (this.view === 'files') {
            await this.loadFiles();
          }
          if (this.view === 'attendance') {
            await this.loadAttendance();
          }
          if (this.view === 'approvals') {
            await this.loadApprovals();
          }
          if (this.view === 'notifications') {
            await this.loadNotifications();
          }
        } catch (error) {
          // Session expiration or transient network errors are handled by the next user action.
        }
      },
      async loadDepartments() {
        this.departments = await this.api('/api/departments');
      },
      async loadDepartmentPage() {
        const params = toParams(this.departmentFilter);
        this.departmentPage = await this.api('/api/departments/page?' + params);
      },
      async loadEmployeeOptions() {
        this.employeeOptions = await this.api('/api/employees/options');
      },
      async loadEmployees() {
        const params = toParams(this.employeeFilter);
        this.employees = await this.api('/api/employees?' + params);
      },
      async loadAuditLogs() {
        const params = toParams(this.auditFilter);
        this.auditLogs = await this.api('/api/audit-logs?' + params);
      },
      async loadAuditUnreadCount() {
        if (!this.isAdmin) {
          this.auditUnreadCount = 0;
          return;
        }
        const afterId = this.auditLastSeenId();
        this.auditUnreadCount = await this.api('/api/audit-logs/unread-count?afterId=' + afterId);
      },
      auditSeenKey() {
        return 'ems.audit.lastSeen.' + (this.user?.id || 'anonymous');
      },
      auditLastSeenId() {
        const value = window.localStorage.getItem(this.auditSeenKey());
        const parsed = Number(value);
        return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
      },
      markAuditSeen() {
        if (!this.isAdmin || !this.auditLogs.records || !this.auditLogs.records.length) {
          this.auditUnreadCount = 0;
          return;
        }
        const latestId = Math.max(...this.auditLogs.records.map(record => Number(record.id) || 0));
        const current = this.auditLastSeenId();
        if (latestId > current) {
          window.localStorage.setItem(this.auditSeenKey(), String(latestId));
        }
        this.auditUnreadCount = 0;
      },
      async loadLeaves() {
        const params = toParams(this.leaveFilter);
        this.leavePage = await this.api('/api/leaves?' + params);
        this.leaves = this.leavePage.records || [];
        if (!this.leaves.length && this.leaveFilter.page > 1 && this.leavePage.total > 0) {
          this.leaveFilter.page -= 1;
          await this.loadLeaves();
        }
      },
      async loadLeaveUnreadCount() {
        this.leaveUnreadCount = await this.api('/api/leaves/unread-count');
      },
      async loadApprovals() {
        const params = toParams(this.approvalFilter);
        this.approvalPage = await this.api('/api/approvals?' + params);
        this.approvals = this.approvalPage.records || [];
        if (!this.approvals.length && this.approvalFilter.page > 1 && this.approvalPage.total > 0) {
          this.approvalFilter.page -= 1;
          await this.loadApprovals();
        }
      },
      async loadApprovalUnreadCount() {
        this.approvalUnreadCount = await this.api('/api/approvals/unread-count');
      },
      async loadNotifications() {
        const params = toParams(this.notificationFilter);
        this.notificationPage = await this.api('/api/notifications?' + params);
        this.notifications = this.notificationPage.records || [];
        if (!this.notifications.length && this.notificationFilter.page > 1 && this.notificationPage.total > 0) {
          this.notificationFilter.page -= 1;
          await this.loadNotifications();
        }
      },
      async loadNotificationUnreadCount() {
        this.notificationUnreadCount = await this.api('/api/notifications/unread-count');
      },
      async loadMessages() {
        const params = toParams(this.messageFilter);
        this.messagePage = await this.api('/api/messages?' + params);
        this.messages = this.messagePage.records || [];
        if (!this.messages.length && this.messageFilter.page > 1 && this.messagePage.total > 0) {
          this.messageFilter.page -= 1;
          await this.loadMessages();
        }
      },
      async loadContacts() {
        this.contacts = await this.api('/api/messages/contacts');
      },
      async loadFiles() {
        const params = toParams(this.fileFilter);
        const result = await this.api('/api/files/page?' + params);
        if (Array.isArray(result)) {
          this.files = result;
          this.filePage = { total: result.length, page: 1, size: result.length || this.fileFilter.size, records: result };
          return;
        }
        this.filePage = result || { total: 0, page: this.fileFilter.page, size: this.fileFilter.size, records: [] };
        this.files = this.filePage.records || [];
        if (!this.files.length && this.fileFilter.page > 1 && this.filePage.total > 0) {
          this.fileFilter.page -= 1;
          await this.loadFiles();
        }
      },
      async loadFileUnread() {
        this.fileUnreadCount = await this.api('/api/files/unread-count');
      },
      async loadAttendance() {
        const params = toParams(this.attendanceFilter);
        this.attendancePage = await this.api('/api/attendance?' + params);
        this.attendances = this.attendancePage.records || [];
        if (!this.attendances.length && this.attendanceFilter.page > 1 && this.attendancePage.total > 0) {
          this.attendanceFilter.page -= 1;
          await this.loadAttendance();
        }
      },
      async loadAttendanceUnread() {
        this.attendanceUnreadCount = await this.api('/api/attendance/unread-count');
      },
      async loadCheckInWindow() {
        try {
          this.checkInWindow = await this.api('/api/attendance/check-in-window');
        } catch (e) {
          this.checkInWindow = null;
        }
      },
      async loadAttendanceSettings() {
        const settings = await this.api('/api/attendance/settings');
        this.attendanceSettingsForm = {
          checkInStart: settings.checkInStart || settings.start || '',
          checkInEnd: settings.checkInEnd || settings.end || '',
          lateAfter: settings.lateAfter || ''
        };
        this.checkInWindow = settings;
      },
      async loadAttendanceAnomalies() {
        const params = toParams(this.attendanceAnomalyFilter);
        this.attendanceAnomalyPage = await this.api('/api/attendance/anomalies?' + params);
        this.attendanceAnomalies = this.attendanceAnomalyPage.records || [];
        if (!this.attendanceAnomalies.length && this.attendanceAnomalyFilter.page > 1 && this.attendanceAnomalyPage.total > 0) {
          this.attendanceAnomalyFilter.page -= 1;
          await this.loadAttendanceAnomalies();
        }
      },
      async loadLoginLogs() {
        const params = toParams(this.loginLogFilter);
        this.loginLogs = await this.api('/api/login-logs?' + params);
      },
      attendancePageTo(page) {
        this.attendanceFilter.page = page;
        this.loadAttendance();
      },
      attendanceAnomalyPageTo(page) {
        this.attendanceAnomalyFilter.page = page;
        this.loadAttendanceAnomalies();
      },
      loginLogPageTo(page) {
        this.loginLogFilter.page = page;
        this.loadLoginLogs();
      },
      openAttendanceFilterModal() {
        this.attendanceFilterModalOpen = true;
      },
      closeAttendanceFilterModal() {
        this.attendanceFilterModalOpen = false;
      },
      async searchAttendance() {
        this.attendanceFilter.page = 1;
        await this.loadAttendance();
        this.closeAttendanceFilterModal();
      },
      async resetAttendanceFilter() {
        Object.assign(this.attendanceFilter, { employeeId: '', startDate: '', endDate: '', page: 1, size: LIST_PAGE_SIZE });
        await this.loadAttendance();
      },
      openAttendanceAnomalyFilterModal() {
        this.attendanceAnomalyFilterModalOpen = true;
      },
      closeAttendanceAnomalyFilterModal() {
        this.attendanceAnomalyFilterModalOpen = false;
      },
      async searchAttendanceAnomalies() {
        this.attendanceAnomalyFilter.page = 1;
        await this.loadAttendanceAnomalies();
        this.closeAttendanceAnomalyFilterModal();
      },
      async resetAttendanceAnomalyFilter() {
        Object.assign(this.attendanceAnomalyFilter, {
          employeeId: '',
          departmentId: '',
          type: '',
          startDate: new Date().toISOString().slice(0, 10),
          endDate: new Date().toISOString().slice(0, 10),
          page: 1,
          size: LIST_PAGE_SIZE
        });
        await this.loadAttendanceAnomalies();
      },
      openLoginLogFilterModal() {
        this.loginLogFilterModalOpen = true;
      },
      closeLoginLogFilterModal() {
        this.loginLogFilterModalOpen = false;
      },
      async searchLoginLogs() {
        this.loginLogFilter.page = 1;
        await this.loadLoginLogs();
        this.closeLoginLogFilterModal();
      },
      async resetLoginLogFilter() {
        Object.assign(this.loginLogFilter, {
          username: '',
          ipAddress: '',
          result: '',
          startDate: '',
          endDate: '',
          page: 1,
          size: LIST_PAGE_SIZE
        });
        await this.loadLoginLogs();
      },
      async checkInAttendance() {
        await this.openCaptchaModal();
      },
      async openCaptchaModal() {
        this.captchaError = '';
        this.captchaInput = '';
        this.captchaModalOpen = true;
        this.captchaSubmitting = false;
        await this.refreshCaptcha();
      },
      closeCaptchaModal() {
        this.captchaModalOpen = false;
        this.captchaData = null;
        this.captchaInput = '';
        this.captchaError = '';
        this.captchaSubmitting = false;
      },
      async refreshCaptcha() {
        this.captchaInput = '';
        this.captchaError = '';
        await this.fetchCaptcha();
      },
      async fetchCaptcha() {
        try {
          this.captchaData = await this.api('/api/captcha/check-in', { timeout: 5000 });
        } catch (error) {
          this.captchaError = this.captchaError || error.message || '验证码加载失败';
        }
      },
      async submitCaptchaCheckIn() {
        if (!this.captchaData || this.captchaSubmitting) return;
        if (!this.captchaInput || this.captchaInput.length < 6) {
          this.captchaError = '请输入完整的验证码';
          return;
        }
        this.captchaSubmitting = true;
        this.captchaError = '';
        try {
          await this.api('/api/attendance/check-in', {
            method: 'POST',
            body: JSON.stringify({
              captchaToken: this.captchaData.token,
              captchaAnswer: this.captchaInput
            }),
            timeout: 8000
          });
          this.showFeedback('success', '今日签到已完成');
          this.closeCaptchaModal();
          await Promise.all([this.loadAttendance(), this.loadAttendanceUnread()]);
        } catch (error) {
          this.captchaError = error.message || '签到失败，请重试';
          this.captchaInput = '';
          await this.fetchCaptcha();
        } finally {
          this.captchaSubmitting = false;
        }
      },
      openBulkAttendanceModal() {
        this.bulkAttendanceForm = defaultBulkAttendanceForm();
        this.bulkAttendanceModalOpen = true;
      },
      closeBulkAttendanceModal() {
        this.bulkAttendanceModalOpen = false;
        this.bulkAttendanceForm = defaultBulkAttendanceForm();
      },
      async bulkFullAttendance() {
        if (!this.bulkAttendanceForm.employeeIds.length && !this.bulkAttendanceForm.departmentIds.length) {
          this.showFeedback('error', '请选择至少一个员工或部门');
          return;
        }
        try {
          const count = await this.api('/api/attendance/bulk-check-in', {
            method: 'POST',
            body: JSON.stringify({
              employeeIds: this.bulkAttendanceForm.employeeIds.map(Number),
              departmentIds: this.bulkAttendanceForm.departmentIds.map(Number),
              attendanceDate: this.bulkAttendanceForm.attendanceDate,
              remark: this.bulkAttendanceForm.remark
            })
          });
          this.showFeedback('success', '已为 ' + count + ' 人设置全勤');
          this.closeBulkAttendanceModal();
          await Promise.all([this.loadAttendance(), this.loadAttendanceUnread(), this.loadAttendanceAnomalies()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      openAttendanceExportModal() {
        this.attendanceExportForm = {
          employeeId: this.attendanceFilter.employeeId || '',
          startDate: this.attendanceFilter.startDate || '',
          endDate: this.attendanceFilter.endDate || ''
        };
        this.attendanceExportModalOpen = true;
      },
      closeAttendanceExportModal() {
        this.attendanceExportModalOpen = false;
      },
      async exportAttendance() {
        try {
          const adminPassword = this.isAdmin ? await this.requestAdminPassword('导出考勤记录') : '';
          if (adminPassword === null) return;
          await this.downloadCsv('/api/attendance/export', { ...this.attendanceExportForm, adminPassword }, 'attendance.csv');
          this.closeAttendanceExportModal();
        } catch (error) {
          this.showFeedback('error', error.message || '导出失败');
        }
      },
      async openAttendanceSettingsModal() {
        if (!this.isAdmin) {
          return;
        }
        this.attendanceForceAbsentForm = defaultAttendanceForceAbsentForm();
        try {
          await this.loadAttendanceSettings();
          this.attendanceSettingsModalOpen = true;
          this.refreshIcons();
        } catch (error) {
          this.showFeedback('error', error.message || '考勤设置加载失败');
        }
      },
      closeAttendanceSettingsModal() {
        this.attendanceSettingsModalOpen = false;
        this.attendanceSettingsSubmitting = false;
        this.attendanceForceAbsentSubmitting = false;
        this.attendanceForceAbsentForm = defaultAttendanceForceAbsentForm();
      },
      async saveAttendanceSettings() {
        if (this.attendanceSettingsSubmitting) {
          return;
        }
        this.attendanceSettingsSubmitting = true;
        try {
          const adminPassword = await this.requestAdminPassword('设置考勤时间');
          if (adminPassword === null) return;
          const settings = await this.api('/api/attendance/settings', {
            method: 'PUT',
            body: JSON.stringify({ ...this.attendanceSettingsForm, adminPassword })
          });
          this.attendanceSettingsForm = {
            checkInStart: settings.checkInStart || settings.start || '',
            checkInEnd: settings.checkInEnd || settings.end || '',
            lateAfter: settings.lateAfter || ''
          };
          this.checkInWindow = settings;
          this.showFeedback('success', '考勤时间已更新');
          await Promise.all([this.loadAttendanceUnread(), this.loadAttendanceAnomalies(), this.loadDashboardStats()]);
        } catch (error) {
          this.showFeedback('error', error.message || '保存失败');
        } finally {
          this.attendanceSettingsSubmitting = false;
        }
      },
      async forceAbsentAttendance() {
        if (this.attendanceForceAbsentSubmitting) {
          return;
        }
        if (!this.attendanceForceAbsentForm.employeeId || !this.attendanceForceAbsentForm.attendanceDate) {
          this.showFeedback('error', '请选择员工和日期');
          return;
        }
        this.attendanceForceAbsentSubmitting = true;
        try {
          const adminPassword = await this.requestAdminPassword('强制缺勤');
          if (adminPassword === null) return;
          await this.api('/api/attendance/force-absent', {
            method: 'POST',
            body: JSON.stringify({
              employeeId: Number(this.attendanceForceAbsentForm.employeeId),
              attendanceDate: this.attendanceForceAbsentForm.attendanceDate,
              remark: this.attendanceForceAbsentForm.remark,
              adminPassword
            })
          });
          this.showFeedback('success', '已强制标记为缺勤');
          this.attendanceForceAbsentForm = defaultAttendanceForceAbsentForm();
          await Promise.all([
            this.loadAttendance(),
            this.loadAttendanceUnread(),
            this.loadAttendanceAnomalies(),
            this.loadDashboardStats()
          ]);
        } catch (error) {
          this.showFeedback('error', error.message || '处理失败');
        } finally {
          this.attendanceForceAbsentSubmitting = false;
        }
      },
      openEmployeeExportModal() {
        this.employeeExportForm = {
          name: this.employeeFilter.name || '',
          gender: this.employeeFilter.gender || '',
          departmentId: this.employeeFilter.departmentId || '',
          status: this.employeeFilter.status || ''
        };
        this.employeeExportModalOpen = true;
      },
      closeEmployeeExportModal() {
        this.employeeExportModalOpen = false;
      },
      async exportEmployees() {
        try {
          const adminPassword = await this.requestAdminPassword('导出员工列表');
          if (adminPassword === null) return;
          await this.downloadCsv('/api/employees/export', { ...this.employeeExportForm, adminPassword }, '员工列表.csv');
          this.closeEmployeeExportModal();
        } catch (error) {
          this.showFeedback('error', error.message || '导出失败');
        }
      },
      openSalaryExportModal() {
        this.salaryExportForm = {
          employeeName: this.salaryFilter.employeeName || '',
          changeType: this.salaryFilter.changeType || '',
          startDate: this.salaryFilter.startDate || '',
          endDate: this.salaryFilter.endDate || ''
        };
        this.salaryExportModalOpen = true;
      },
      closeSalaryExportModal() {
        this.salaryExportModalOpen = false;
      },
      async exportSalaries() {
        try {
          const adminPassword = await this.requestAdminPassword('导出薪资记录');
          if (adminPassword === null) return;
          await this.downloadCsv('/api/salaries/export', { ...this.salaryExportForm, adminPassword }, '薪资记录.csv');
          this.closeSalaryExportModal();
        } catch (error) {
          this.showFeedback('error', error.message || '导出失败');
        }
      },
      openApprovalExportModal() {
        this.approvalExportForm = { type: '', status: '', startDate: '', endDate: '' };
        this.approvalExportModalOpen = true;
      },
      closeApprovalExportModal() {
        this.approvalExportModalOpen = false;
      },
      async exportApprovals() {
        try {
          const adminPassword = await this.requestAdminPassword('导出审批记录');
          if (adminPassword === null) return;
          await this.downloadCsv('/api/approvals/export', { ...this.approvalExportForm, adminPassword }, '审批记录.csv');
          this.closeApprovalExportModal();
        } catch (error) {
          this.showFeedback('error', error.message || '导出失败');
        }
      },
      async openAnnouncementReadStats(announcement) {
        this.announcementReadStatsTarget = announcement;
        this.announcementReadStatsOpen = true;
        try {
          this.announcementReadStats = await this.api('/api/announcements/' + announcement.id + '/read-stats');
        } catch (error) {
          this.showFeedback('error', error.message);
          this.closeAnnouncementReadStats();
        }
      },
      closeAnnouncementReadStats() {
        this.announcementReadStatsOpen = false;
        this.announcementReadStatsTarget = null;
        this.announcementReadStats = { readCount: 0, unreadCount: 0, total: 0, readList: [], unreadList: [] };
      },
      auditPage(page) {
        this.auditFilter.page = page;
        this.loadAuditLogs();
      },
      async searchAuditLogs() {
        this.auditFilter.page = 1;
        await this.loadAuditLogs();
        this.markAuditSeen();
        this.auditFilterModalOpen = false;
      },
      async resetAuditFilter() {
        Object.assign(this.auditFilter, { module: '', operatorName: '', page: 1, size: LIST_PAGE_SIZE });
        await this.loadAuditLogs();
      },
      leavePageTo(page) {
        this.leaveFilter.page = page;
        this.loadLeaves();
      },
      openLeaveModal() {
        this.leaveForm = defaultLeaveForm();
        this.leaveModalOpen = true;
      },
      closeLeaveModal() {
        this.leaveModalOpen = false;
        this.leaveForm = defaultLeaveForm();
      },
      async submitLeave() {
        try {
          await this.api('/api/leaves', {
            method: 'POST',
            body: JSON.stringify({
              startDate: this.leaveForm.startDate,
              endDate: this.leaveForm.endDate,
              reason: this.leaveForm.reason
            })
          });
          this.showFeedback('success', '休假申请已提交');
          this.closeLeaveModal();
          await Promise.all([this.loadLeaves(), this.loadLeaveUnreadCount()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      canReviewLeave(item) {
        if (!item || item.status !== 'PENDING') {
          return false;
        }
        if (this.isAdmin) {
          return true;
        }
        return this.isSupervisor && item.employeeId !== this.user?.id;
      },
      openLeaveReview(item, action) {
        this.leaveReviewTarget = item;
        this.leaveReviewAction = action;
        this.leaveForm.reviewComment = '';
        this.leaveReviewModalOpen = true;
      },
      closeLeaveReview() {
        this.leaveReviewModalOpen = false;
        this.leaveReviewTarget = null;
        this.leaveReviewAction = '';
        this.leaveForm.reviewComment = '';
      },
      async submitLeaveReview() {
        if (!this.leaveReviewTarget || !this.leaveReviewAction) {
          return;
        }
        try {
          await this.api('/api/leaves/' + this.leaveReviewTarget.id + '/' + (this.leaveReviewAction === 'approve' ? 'approve' : 'reject'), {
            method: 'POST',
            body: JSON.stringify({ reviewComment: this.leaveForm.reviewComment })
          });
          this.showFeedback('success', this.leaveReviewAction === 'approve' ? '休假申请已通过' : '休假申请已驳回');
          this.closeLeaveReview();
          await Promise.all([this.loadLeaves(), this.loadLeaveUnreadCount(), this.loadEmployees(), this.loadProfile()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      approvalPageTo(page) {
        this.approvalFilter.page = page;
        this.loadApprovals();
      },
      async openApprovalModal() {
        this.approvalForm = defaultApprovalForm();
        this.approvalModalOpen = true;
        if (!this.departments.length) {
          await this.loadDepartments().catch(() => null);
        }
      },
      closeApprovalModal() {
        this.approvalModalOpen = false;
        this.approvalForm = defaultApprovalForm();
      },
      async submitApproval() {
        const payload = {
          type: this.approvalForm.type,
          targetDepartmentId: this.approvalForm.targetDepartmentId === '' ? null : Number(this.approvalForm.targetDepartmentId),
          amount: this.approvalForm.amount === '' ? null : Number(this.approvalForm.amount),
          startDate: this.approvalForm.startDate,
          endDate: this.approvalForm.endDate,
          fileName: this.approvalForm.fileName,
          reason: this.approvalForm.reason
        };
        try {
          await this.api('/api/approvals', {
            method: 'POST',
            body: JSON.stringify(payload)
          });
          this.showFeedback('success', '审批申请已提交');
          this.closeApprovalModal();
          await Promise.all([this.loadApprovals(), this.loadApprovalUnreadCount(), this.loadNotifications(), this.loadNotificationUnreadCount()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      canReviewApproval(item) {
        if (!item || item.status !== 'PENDING') {
          return false;
        }
        if (this.isAdmin) {
          return true;
        }
        return this.isSupervisor
          && item.applicantId !== this.user?.id
          && item.applicantDepartmentId === this.user?.departmentId
          && item.applicantRole === 'EMPLOYEE'
          && ['LEAVE', 'FILE_REQUEST'].includes(item.type);
      },
      openApprovalReview(item, action) {
        this.approvalReviewTarget = item;
        this.approvalReviewAction = action;
        this.approvalForm.reviewComment = '';
        this.approvalReviewModalOpen = true;
      },
      closeApprovalReview() {
        this.approvalReviewModalOpen = false;
        this.approvalReviewTarget = null;
        this.approvalReviewAction = '';
        this.approvalForm.reviewComment = '';
      },
      async submitApprovalReview() {
        if (!this.approvalReviewTarget || !this.approvalReviewAction) {
          return;
        }
        try {
          await this.api('/api/approvals/' + this.approvalReviewTarget.id + '/' + (this.approvalReviewAction === 'approve' ? 'approve' : 'reject'), {
            method: 'POST',
            body: JSON.stringify({ reviewComment: this.approvalForm.reviewComment })
          });
          this.showFeedback('success', this.approvalReviewAction === 'approve' ? '审批已通过' : '审批已驳回');
          this.closeApprovalReview();
          await Promise.all([
            this.loadApprovals(),
            this.loadApprovalUnreadCount(),
            this.loadNotifications(),
            this.loadNotificationUnreadCount(),
            this.loadEmployees(),
            this.loadEmployeeOptions(),
            this.loadProfile()
          ]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      notificationPageTo(page) {
        this.notificationFilter.page = page;
        this.loadNotifications();
      },
      async markNotificationRead(item) {
        if (!item || item.readFlag) {
          return;
        }
        try {
          await this.api('/api/notifications/' + item.id + '/read', { method: 'PUT' });
          item.readFlag = true;
          await Promise.all([this.loadNotifications(), this.loadNotificationUnreadCount()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      async markAllNotificationsRead() {
        try {
          await this.api('/api/notifications/read-all', { method: 'PUT' });
          await Promise.all([this.loadNotifications(), this.loadNotificationUnreadCount()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      async openNotificationSource(item) {
        if (!item) {
          return;
        }
        if (!item.readFlag) {
          await this.markNotificationRead(item);
        }
        if (item.linkView === 'announcements') {
          await this.openAnnouncementModal();
          return;
        }
        if (item.linkView) {
          await this.changeView(item.linkView);
        }
      },
      filePageTo(page) {
        this.fileFilter.page = page;
        this.loadFiles();
      },
      openFileModal() {
        this.resetFileForm();
        this.fileModalOpen = true;
        this.refreshIcons();
      },
      closeFileModal() {
        this.fileModalOpen = false;
        this.resetFileForm();
      },
      resetFileForm() {
        this.fileForm = { departmentIds: [], employeeIds: [], file: null, fileName: '' };
      },
      onFileChange(event) {
        const selected = event.target.files && event.target.files[0] ? event.target.files[0] : null;
        this.fileForm.file = selected;
        this.fileForm.fileName = selected ? selected.name : '';
      },
      async uploadFile() {
        if (!this.fileForm.file) {
          this.showFeedback('error', '请选择要分发的文件');
          return;
        }
        if (!this.fileForm.departmentIds.length && !this.fileForm.employeeIds.length) {
          this.showFeedback('error', '请至少选择一个部门或人员');
          return;
        }
        this.fileSubmitting = true;
        try {
          const form = new FormData();
          form.append('file', this.fileForm.file);
          this.fileForm.departmentIds.forEach(id => form.append('departmentIds', id));
          this.fileForm.employeeIds.forEach(id => form.append('employeeIds', id));
          const response = await fetch('/api/files', {
            method: 'POST',
            credentials: 'include',
            headers: this.csrfToken ? { 'X-CSRF-Token': this.csrfToken } : {},
            body: form
          });
          const payload = await response.json().catch(() => ({ success: false, message: '服务器返回异常' }));
          if (!response.ok || payload.success === false) {
            if (response.status === 401) {
              this.handleSessionExpired(payload.message || '账号已在其他设备登录，请重新登录');
              return;
            }
            if (response.status === 423 && this.user) {
              this.screenLocked = true;
              this.user.screenLocked = true;
              return;
            }
            throw new Error(payload.message || '分发失败');
          }
          this.showFeedback('success', '文件已分发给所选范围');
          this.closeFileModal();
          await Promise.all([this.loadFiles(), this.loadFileUnread()]);
        } catch (error) {
          this.showFeedback('error', error.message || '分发失败');
        } finally {
          this.fileSubmitting = false;
        }
      },
      downloadFile(file) {
        const link = document.createElement('a');
        link.href = '/api/files/' + file.id + '/download';
        link.rel = 'noopener';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        if (!this.isAdmin) {
          window.setTimeout(() => {
            this.api('/api/files/' + file.id + '/read', { method: 'POST' })
              .catch(() => null)
              .finally(() => {
                this.loadFiles();
                this.loadFileUnread();
              });
          }, 800);
        }
      },
      async openFileDetail(file) {
        this.fileDetailTarget = file;
        this.fileDetailModalOpen = true;
        try {
          this.fileRecipientDetails = await this.api('/api/files/' + file.id + '/recipients');
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      closeFileDetail() {
        this.fileDetailModalOpen = false;
        this.fileDetailTarget = null;
        this.fileRecipientDetails = [];
      },
      async removeFile(file) {
        if (!window.confirm('确认删除已分发文件 ' + file.originalName + '？')) {
          return;
        }
        try {
          const adminPassword = await this.requestAdminPassword('删除分发文件');
          if (adminPassword === null) return;
          await this.api('/api/files/' + file.id, {
            method: 'DELETE',
            body: JSON.stringify({ adminPassword })
          });
          this.showFeedback('success', '文件已删除');
          await Promise.all([this.loadFiles(), this.loadFileUnread()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      formatSize(bytes) {
        const value = Number(bytes);
        if (!value || value <= 0) {
          return '0 B';
        }
        if (value < 1024) {
          return value + ' B';
        }
        if (value < 1024 * 1024) {
          return (value / 1024).toFixed(1) + ' KB';
        }
        return (value / (1024 * 1024)).toFixed(1) + ' MB';
      },
      async loadAnnouncements() {
        this.announcements = await this.api('/api/announcements');
      },
      async loadPendingAnnouncements() {
        this.pendingAnnouncements = await this.api('/api/announcements/pending');
      },
      async loadSalaries() {
        const params = toParams(this.salaryFilter);
        this.salaries = await this.api('/api/salaries?' + params);
      },
      async loadSalaryHistory() {
        this.salaryHistory = [];
        if (this.salaryForm.employeeId) {
          this.salaryHistory = await this.api('/api/salaries/history/' + this.salaryForm.employeeId);
        }
      },
      async searchEmployees() {
        this.employeeFilter.page = 1;
        await this.loadEmployees();
        this.closeEmployeeFilterModal();
      },
      async resetEmployeeFilter() {
        Object.assign(this.employeeFilter, {
          name: '',
          nameMode: 'fuzzy',
          gender: '',
          departmentId: '',
          status: '',
          minSalary: '',
          maxSalary: '',
          hireDateStart: '',
          hireDateEnd: '',
          page: 1,
          size: LIST_PAGE_SIZE
        });
        await this.loadEmployees();
      },
      employeePage(page) {
        this.employeeFilter.page = page;
        this.loadEmployees();
      },
      departmentPageTo(page) {
        this.departmentFilter.page = page;
        this.loadDepartmentPage();
      },
      openEmployeeModal() {
        this.resetEmployeeForm();
        this.employeeModalOpen = true;
      },
      closeEmployeeModal() {
        this.employeeModalOpen = false;
        this.resetEmployeeForm();
      },
      openEmployeeFilterModal() {
        this.employeeFilterModalOpen = true;
      },
      closeEmployeeFilterModal() {
        this.employeeFilterModalOpen = false;
      },
      editEmployee(employee) {
        this.employeeEditing = true;
        this.employeeForm = {
          ...defaultEmployee(),
          ...employee,
          departmentId: employee.departmentId || '',
          password: '',
          leaveEndDate: employee.leaveEndDate || ''
        };
        this.employeeOriginalStatus = employee.status || '';
        this.employeeModalOpen = true;
      },
      resetEmployeeForm() {
        this.employeeEditing = false;
        this.employeeOriginalStatus = '';
        this.employeeForm = defaultEmployee();
      },
      async saveEmployee() {
        const payload = { ...this.employeeForm };
        payload.departmentId = payload.departmentId === '' ? null : Number(payload.departmentId);
        if (this.isAdmin) {
          payload.salary = Number(payload.salary || 0);
        }
        if (this.employeeEditing && this.employeeOriginalStatus !== 'RESIGNED' && payload.status === 'RESIGNED') {
          if (!window.confirm('确认将员工 ' + (payload.name || '') + ' 标记为离职？')) {
            return;
          }
        }
        try {
          if (this.employeeEditing) {
            await this.api('/api/employees/' + payload.id, { method: 'PUT', body: JSON.stringify(payload) });
          } else {
            await this.api('/api/employees', { method: 'POST', body: JSON.stringify(payload) });
          }
          this.showFeedback('success', '员工信息已保存');
          this.closeEmployeeModal();
          await Promise.all([this.loadEmployees(), this.loadEmployeeOptions(), this.loadDepartments()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      async removeEmployee(employee) {
        if (!window.confirm('确认删除员工 ' + employee.name + '？')) {
          return;
        }
        try {
          const adminPassword = await this.requestAdminPassword('删除员工');
          if (adminPassword === null) return;
          await this.api('/api/employees/' + employee.id, {
            method: 'DELETE',
            body: JSON.stringify({ adminPassword })
          });
          this.showFeedback('success', '员工已删除');
          await Promise.all([this.loadEmployees(), this.loadEmployeeOptions(), this.loadDepartments()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      async adminClearLockPassword(employee) {
        if (!window.confirm('确认强制解除 ' + employee.name + ' 的锁屏密码？')) return;
        try {
          const adminPassword = await this.requestAdminPassword('解除锁屏密码');
          if (adminPassword === null) return;
          await this.api('/api/employees/' + employee.id + '/lock-password', {
            method: 'DELETE',
            body: JSON.stringify({ adminPassword })
          });
          this.showFeedback('success', '已解除 ' + employee.name + ' 的锁屏密码');
          await this.loadEmployees();
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      openDepartmentModal() {
        this.resetDepartmentForm();
        this.departmentModalOpen = true;
      },
      closeDepartmentModal() {
        this.departmentModalOpen = false;
        this.resetDepartmentForm();
      },
      openDepartmentFilterModal() {
        this.departmentFilterModalOpen = true;
      },
      closeDepartmentFilterModal() {
        this.departmentFilterModalOpen = false;
      },
      async searchDepartments() {
        this.departmentFilter.page = 1;
        await this.loadDepartmentPage();
        this.closeDepartmentFilterModal();
      },
      async resetDepartmentFilter() {
        Object.assign(this.departmentFilter, {
          name: '',
          managerName: '',
          page: 1,
          size: LIST_PAGE_SIZE
        });
        await this.loadDepartmentPage();
      },
      editDepartment(department) {
        this.departmentEditing = true;
        this.departmentForm = {
          id: department.id,
          name: department.name,
          description: department.description || '',
          managerId: department.managerId || ''
        };
        this.departmentModalOpen = true;
      },
      resetDepartmentForm() {
        this.departmentEditing = false;
        this.departmentForm = defaultDepartment();
      },
      async saveDepartment() {
        const payload = {
          ...this.departmentForm,
          managerId: this.departmentForm.managerId === '' ? null : Number(this.departmentForm.managerId)
        };
        try {
          if (this.departmentEditing) {
            await this.api('/api/departments/' + payload.id, { method: 'PUT', body: JSON.stringify(payload) });
          } else {
            await this.api('/api/departments', { method: 'POST', body: JSON.stringify(payload) });
          }
          this.showFeedback('success', '部门信息已保存');
          this.closeDepartmentModal();
          await Promise.all([this.loadDepartments(), this.loadDepartmentPage()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      async removeDepartment(department) {
        if (!window.confirm('确认删除部门 ' + department.name + '？')) {
          return;
        }
        try {
          const adminPassword = await this.requestAdminPassword('删除部门');
          if (adminPassword === null) return;
          await this.api('/api/departments/' + department.id, {
            method: 'DELETE',
            body: JSON.stringify({ adminPassword })
          });
          this.showFeedback('success', '部门已删除');
          await Promise.all([this.loadDepartments(), this.loadDepartmentPage()]);
          if (!this.departmentPage.records.length && this.departmentFilter.page > 1) {
            this.departmentFilter.page -= 1;
            await this.loadDepartmentPage();
          }
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      openSalaryModal() {
        this.salaryModalOpen = true;
      },
      closeSalaryModal() {
        this.salaryModalOpen = false;
      },
      openSalaryFilterModal() {
        this.salaryFilterModalOpen = true;
      },
      closeSalaryFilterModal() {
        this.salaryFilterModalOpen = false;
      },
      async searchSalaries() {
        this.salaryFilter.page = 1;
        await this.loadSalaries();
        this.closeSalaryFilterModal();
      },
      async resetSalaryFilter() {
        Object.assign(this.salaryFilter, {
          employeeName: '',
          changeType: '',
          startDate: '',
          endDate: '',
          page: 1,
          size: LIST_PAGE_SIZE
        });
        await this.loadSalaries();
      },
      salaryPage(page) {
        this.salaryFilter.page = page;
        this.loadSalaries();
      },
      async paySalary() {
        await this.salaryAction('/api/salaries/pay', false);
      },
      async changeSalary(type) {
        await this.salaryAction(type === 'raise' ? '/api/salaries/raise' : '/api/salaries/decrease', true);
      },
      async salaryAction(url, requireAmount) {
        if (!this.salaryForm.employeeId) {
          this.showFeedback('error', '请选择员工');
          return;
        }
        if (requireAmount && (!this.salaryForm.amount || Number(this.salaryForm.amount) <= 0)) {
          this.showFeedback('error', '请输入有效调整金额');
          return;
        }
        let adminPassword = '';
        if (url.endsWith('/decrease')) {
          const employeeName = this.selectedSalaryEmployee ? this.selectedSalaryEmployee.name : '该员工';
          if (!window.confirm('确认对 ' + employeeName + ' 降薪 ' + this.money(this.salaryForm.amount) + '？')) {
            return;
          }
          adminPassword = await this.requestAdminPassword('执行降薪');
          if (adminPassword === null) return;
        }
        try {
          await this.api(url, {
            method: 'POST',
            body: JSON.stringify({
              employeeId: Number(this.salaryForm.employeeId),
              amount: Number(this.salaryForm.amount || 0),
              remark: this.salaryForm.remark,
              adminPassword
            })
          });
          this.showFeedback('success', '工资操作已完成');
          this.salaryForm.amount = '';
          this.salaryForm.remark = '';
          await Promise.all([this.loadSalaries(), this.loadEmployeeOptions(), this.loadSalaryHistory()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      async openAnnouncementModal() {
        this.announcementModalOpen = true;
        try {
          await this.loadAnnouncements();
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      closeAnnouncementModal() {
        this.announcementModalOpen = false;
      },
      async publishAnnouncement() {
        if (!this.announcementForm.title || !this.announcementForm.content) {
          this.showFeedback('error', '请填写公告标题和内容');
          return;
        }
        this.announcementSubmitting = true;
        try {
          await this.api('/api/announcements', {
            method: 'POST',
            body: JSON.stringify(this.announcementForm)
          });
          this.announcementForm = { title: '', content: '', isPinned: false };
          this.showFeedback('success', '公告已发布');
          await Promise.all([this.loadAnnouncements(), this.loadPendingAnnouncements()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        } finally {
          this.announcementSubmitting = false;
        }
      },
      async toggleAnnouncementPin(announcement) {
        try {
          await this.api('/api/announcements/' + announcement.id + '/pin', { method: 'PUT' });
          this.showFeedback('success', announcement.isPinned ? '已取消置顶' : '已置顶');
          await this.loadAnnouncements();
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      async unpublishAnnouncement(announcement) {
        if (!window.confirm('确认撤回公告「' + announcement.title + '」？撤回后所有用户将不可见。')) {
          return;
        }
        try {
          await this.api('/api/announcements/' + announcement.id, { method: 'DELETE' });
          this.showFeedback('success', '公告已撤回');
          await Promise.all([this.loadAnnouncements(), this.loadPendingAnnouncements()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      async confirmAnnouncementRead() {
        if (!this.currentPendingAnnouncement) {
          return;
        }
        this.announcementSubmitting = true;
        try {
          await this.api('/api/announcements/' + this.currentPendingAnnouncement.id + '/read', {
            method: 'POST',
            body: JSON.stringify({ confirmText: this.announcementConfirmText })
          });
          this.announcementConfirmText = '';
          await Promise.all([this.loadAnnouncements(), this.loadPendingAnnouncements()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        } finally {
          this.announcementSubmitting = false;
        }
      },
      async sendMessage() {
        if (!this.messageForm.receiverId || !this.messageForm.content) {
          this.showFeedback('error', '请选择收件人并填写消息内容');
          return;
        }
        try {
          await this.api('/api/messages', {
            method: 'POST',
            body: JSON.stringify({
              receiverId: Number(this.messageForm.receiverId),
              replyToMessageId: this.messageForm.replyToMessageId ? Number(this.messageForm.replyToMessageId) : null,
              content: this.messageForm.content
            })
          });
          this.resetMessageForm();
          this.closeMessageModal();
          this.showFeedback('success', '消息已发送');
          await Promise.all([this.loadMessages(), this.loadContacts(), this.loadUnread()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      async markRead(message) {
        try {
          await this.api('/api/messages/' + message.id + '/read', { method: 'PUT' });
          message.readFlag = true;
          await Promise.all([this.loadMessages(), this.loadUnread()]);
        } catch (error) {
          this.showFeedback('error', error.message);
        }
      },
      replyTo(message) {
        this.messageForm.receiverId = message.senderId;
        this.messageForm.replyToMessageId = message.id;
        this.messageForm.receiverName = message.senderName;
        this.messageForm.receiverRole = message.senderRole;
        this.messageForm.content = '';
        this.view = 'messages';
        this.messageModalOpen = true;
        if (message.receiverId === this.user?.id && !message.readFlag) {
          this.markRead(message);
        }
      },
      resetMessageForm() {
        this.messageForm = { receiverId: '', replyToMessageId: null, receiverName: '', receiverRole: '', content: '' };
      },
      openMessage(message) {
        this.view = 'messages';
        this.expandedMessageId = message.id;
        if (message.receiverId === this.user?.id && !message.readFlag) {
          this.markRead(message);
        }
      },
      openMessageModal() {
        this.resetMessageForm();
        this.messageModalOpen = true;
      },
      closeMessageModal() {
        this.messageModalOpen = false;
        this.resetMessageForm();
      },
      messagePageTo(page) {
        this.messageFilter.page = page;
        this.expandedMessageId = null;
        this.loadMessages();
      },
      toggleMessage(message) {
        this.expandedMessageId = this.expandedMessageId === message.id ? null : message.id;
        if (message.receiverId === this.user?.id && !message.readFlag) {
          this.markRead(message);
        }
      },
      messagePreview(content) {
        const value = String(content || '');
        return value.length > 3 ? value.slice(0, 3) + '...' : value;
      },
      canReply(message) {
        if (!this.user || message.senderId === this.user.id) {
          return false;
        }
        if (this.isAdmin) {
          return true;
        }
        return message.receiverId === this.user.id;
      },
      readStateText(message) {
        if (message.senderId === this.user?.id) {
          return message.readFlag ? '对方已读' : '对方未读';
        }
        if (message.receiverId === this.user?.id) {
          return message.readFlag ? '已读' : '未读';
        }
        return message.readFlag ? '收件人已读' : '收件人未读';
      },
      approvalTypeLabel(type) {
        return APPROVAL_TYPE_LABELS[type] || '-';
      },
      approvalStatusLabel(status) {
        return APPROVAL_STATUS_LABELS[status] || '-';
      },
      notificationTypeLabel(type) {
        return NOTIFICATION_TYPE_LABELS[type] || '-';
      },
      approvalDetail(item) {
        if (!item) {
          return '-';
        }
        const reason = item.reason ? '；' + item.reason : '';
        if (item.type === 'LEAVE') {
          return (item.startDate || '-') + ' 至 ' + (item.endDate || '-') + reason;
        }
        if (item.type === 'TRANSFER') {
          return '目标部门：' + (item.targetDepartmentName || '-') + reason;
        }
        if (item.type === 'SALARY_RAISE') {
          return '申请金额：' + this.money(item.amount) + reason;
        }
        if (item.type === 'RESIGNATION') {
          return '离职申请' + reason;
        }
        if (item.type === 'FILE_REQUEST') {
          return '文件：' + (item.fileName || '-') + reason;
        }
        return item.reason || '-';
      },
      roleLabel(role) {
        return ROLE_LABELS[role] || '-';
      },
      genderLabel(gender) {
        return GENDER_LABELS[gender] || '-';
      },
      salaryType(type) {
        return SALARY_TYPES[type] || '-';
      },
      attendanceSourceLabel(source) {
        if (source === 'SIGN_IN') {
          return '签到';
        }
        if (source === 'IMPORT') {
          return '导入';
        }
        if (source === 'ADMIN_FILL') {
          return '管理员全勤';
        }
        return '-';
      },
      attendanceAnomalyLabel(type) {
        return ATTENDANCE_ANOMALY_LABELS[type] || '-';
      },
      loginResultLabel(result) {
        return LOGIN_RESULT_LABELS[result] || result || '-';
      },
      leaveStatusLabel(status) {
        return LEAVE_STATUS_LABELS[status] || '-';
      },
      leaveDays(item) {
        if (!item?.startDate || !item?.endDate) {
          return 0;
        }
        const start = new Date(item.startDate + 'T00:00:00');
        const end = new Date(item.endDate + 'T00:00:00');
        return Math.max(1, Math.floor((end - start) / 86400000) + 1);
      },
      statusText(employee) {
        if (!employee) {
          return '-';
        }
        if (employee.status === 'LEAVE' && employee.leaveEndDate) {
          const today = new Date();
          const end = new Date(employee.leaveEndDate + 'T00:00:00');
          const days = Math.max(0, Math.ceil((end - today) / 86400000));
          return '休假，剩余 ' + days + ' 天';
        }
        return STATUS_LABELS[employee.status] || '-';
      },
      money(value) {
        const amount = Number(value || 0);
        return amount.toLocaleString('zh-CN', { style: 'currency', currency: 'CNY' });
      },
      formatDate(value) {
        const date = value instanceof Date ? value : new Date(value);
        return date.toLocaleDateString('zh-CN');
      },
      formatDateTime(value) {
        if (!value) {
          return '-';
        }
        return String(value).replace('T', ' ').slice(0, 16);
      },
      showFeedback(type, text) {
        this.feedback = { type, text };
        window.clearTimeout(this.feedbackTimer);
        this.feedbackTimer = window.setTimeout(() => {
          this.feedback.text = '';
        }, 3000);
      },
      refreshIcons() {
        this.$nextTick(() => {
          if (window.lucide) {
            window.lucide.createIcons();
          }
        });
      },
      initTheme() {
        let stored = null;
        try {
          stored = window.localStorage.getItem('ems-theme');
        } catch (error) {
          stored = null;
        }
        if (stored !== 'dark' && stored !== 'light') {
          const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
          stored = prefersDark ? 'dark' : 'light';
        }
        this.applyTheme(stored);
      },
      applyTheme(theme) {
        this.theme = theme === 'dark' ? 'dark' : 'light';
        document.documentElement.setAttribute('data-theme', this.theme);
        this.refreshIcons();
      },
      toggleTheme() {
        const next = this.theme === 'dark' ? 'light' : 'dark';
        this.applyTheme(next);
        try {
          window.localStorage.setItem('ems-theme', next);
        } catch (error) {
          /* localStorage unavailable — theme still applies for this session */
        }
      }
    }
  });

  app.config.errorHandler = function (error, instance) {
    if (instance && instance.proxy) {
      instance.proxy.loading = false;
      instance.proxy.user = null;
      instance.proxy.loginError = error?.message || '页面初始化失败，请刷新后重试';
    }
  };

  try {
    app.mount('#app');
  } catch (error) {
    const root = document.getElementById('app');
    if (root) {
      root.textContent = '';
      const boot = document.createElement('div');
      boot.className = 'boot';
      const panel = document.createElement('div');
      panel.className = 'boot-error';
      const title = document.createElement('strong');
      title.textContent = '页面渲染失败';
      const detail = document.createElement('span');
      detail.textContent = String(error && error.message ? error.message : error);
      panel.append(title, detail);
      boot.appendChild(panel);
      root.appendChild(boot);
    }
  }
})();
