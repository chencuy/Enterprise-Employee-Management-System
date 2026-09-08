package com.ssm.service;

import com.ssm.dto.AnnouncementReadDetail;
import com.ssm.dto.AnnouncementReadRequest;
import com.ssm.dto.AnnouncementReadStats;
import com.ssm.dto.AnnouncementRequest;
import com.ssm.dto.SessionUser;
import com.ssm.entity.Announcement;
import com.ssm.mapper.EmployeeMapper;
import com.ssm.mapper.AnnouncementMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AnnouncementService {
    public static final String CONFIRM_TEXT = "我已经阅读完成";

    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_CONTENT_LENGTH = 3000;

    private final AnnouncementMapper announcementMapper;
    private final EmployeeMapper employeeMapper;
    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final NotificationCenterService notificationCenterService;

    public AnnouncementService(
            AnnouncementMapper announcementMapper,
            EmployeeMapper employeeMapper,
            AuthService authService,
            AuditLogService auditLogService,
            NotificationCenterService notificationCenterService
    ) {
        this.announcementMapper = announcementMapper;
        this.employeeMapper = employeeMapper;
        this.authService = authService;
        this.auditLogService = auditLogService;
        this.notificationCenterService = notificationCenterService;
    }

    public List<Announcement> list(SessionUser user) {
        List<Announcement> announcements = announcementMapper.findAllForUser(user.id);
        if ("ADMIN".equals(user.role)) {
            announcements.forEach(announcement -> announcement.readFlag = true);
        }
        return announcements;
    }

    public List<Announcement> pending(SessionUser user) {
        if ("ADMIN".equals(user.role)) {
            return List.of();
        }
        return announcementMapper.findUnreadForUser(user.id);
    }

    @Transactional
    public Announcement create(AnnouncementRequest request, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        if (request == null || AuthService.isBlank(request.title) || AuthService.isBlank(request.content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "公告标题和内容不能为空");
        }
        String title = request.title.trim();
        String content = request.content.trim();
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "公告标题不能超过 80 个字符");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "公告内容不能超过 3000 个字符");
        }
        Announcement announcement = new Announcement();
        announcement.title = title;
        announcement.content = content;
        announcement.publisherId = user.id;
        announcement.isPinned = Boolean.TRUE.equals(request.isPinned);
        announcementMapper.insert(announcement);
        announcementMapper.markRead(announcement.id, user.id);
        Announcement created = announcementMapper.findById(announcement.id);
        notificationCenterService.sendToMany(
                employeeMapper.findAllActiveExcept(user.id).stream().map(employee -> employee.id).toList(),
                "ANNOUNCEMENT",
                "新的全体公告",
                created.title,
                "announcement",
                created.id,
                "announcements"
        );
        auditLogService.record(user, "公告管理", "发布公告", "announcement", created.id, created.title, created.content);
        return created;
    }

    @Transactional
    public void markRead(Long id, AnnouncementReadRequest request, SessionUser user) {
        if (id == null || announcementMapper.findById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在");
        }
        String confirmText = request == null ? null : request.confirmText;
        if (!CONFIRM_TEXT.equals(confirmText == null ? null : confirmText.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入指定确认文字后再关闭公告");
        }
        announcementMapper.markRead(id, user.id);
    }

    @Transactional
    public Announcement togglePin(Long id, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        Announcement announcement = announcementMapper.findById(id);
        if (announcement == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在");
        }
        boolean newPinned = !Boolean.TRUE.equals(announcement.isPinned);
        announcementMapper.updatePinned(id, newPinned);
        String action = newPinned ? "置顶公告" : "取消置顶公告";
        auditLogService.record(user, "公告管理", action, "announcement", id, announcement.title, null);
        return announcementMapper.findById(id);
    }

    @Transactional
    public void unpublish(Long id, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        Announcement announcement = announcementMapper.findById(id);
        if (announcement == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在");
        }
        if ("ARCHIVED".equals(announcement.status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "公告已撤回");
        }
        announcementMapper.updateStatus(id, "ARCHIVED");
        auditLogService.record(user, "公告管理", "撤回公告", "announcement", id, announcement.title, announcement.content);
    }

    public AnnouncementReadStats readStats(Long id, SessionUser user) {
        authService.requireRole(user, "ADMIN");
        Announcement announcement = announcementMapper.findById(id);
        if (announcement == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在");
        }
        List<AnnouncementReadDetail> readList = announcementMapper.readDetails(id);
        List<AnnouncementReadDetail> unreadList = announcementMapper.unreadEmployees(id, announcement.publisherId);
        AnnouncementReadStats stats = new AnnouncementReadStats();
        stats.readCount = readList.size();
        stats.unreadCount = unreadList.size();
        stats.total = stats.readCount + stats.unreadCount;
        stats.readList = readList;
        stats.unreadList = unreadList;
        return stats;
    }
}
