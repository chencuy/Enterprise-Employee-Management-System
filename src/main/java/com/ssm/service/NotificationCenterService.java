package com.ssm.service;

import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.NotificationItem;
import com.ssm.mapper.NotificationMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class NotificationCenterService {
    private static final int PAGE_SIZE_LIMIT = 5;

    private final NotificationMapper notificationMapper;

    public NotificationCenterService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    public PageResult<NotificationItem> page(SessionUser user, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), PAGE_SIZE_LIMIT);
        int offset = (safePage - 1) * safeSize;
        return new PageResult<>(
                notificationMapper.countForUser(user.id),
                safePage,
                safeSize,
                notificationMapper.pageForUser(user.id, offset, safeSize)
        );
    }

    public long unreadCount(SessionUser user) {
        return notificationMapper.countUnread(user.id);
    }

    public void markRead(Long id, SessionUser user) {
        if (id == null || notificationMapper.findById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "通知不存在");
        }
        if (notificationMapper.markRead(id, user.id) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权处理该通知");
        }
    }

    public void markAllRead(SessionUser user) {
        notificationMapper.markAllRead(user.id);
    }

    public void send(Long receiverId, String type, String title, String content, String sourceType, Long sourceId, String linkView) {
        if (receiverId == null || AuthService.isBlank(title) || AuthService.isBlank(content)) {
            return;
        }
        NotificationItem item = new NotificationItem();
        item.receiverId = receiverId;
        item.type = limit(type, 40, "SYSTEM");
        item.title = limit(title, 120, "系统通知");
        item.content = limit(content, 1000, "");
        item.sourceType = limit(sourceType, 40, null);
        item.sourceId = sourceId;
        item.linkView = limit(linkView, 40, null);
        notificationMapper.insert(item);
    }

    public void sendToMany(List<Long> receiverIds, String type, String title, String content, String sourceType, Long sourceId, String linkView) {
        if (receiverIds == null) {
            return;
        }
        receiverIds.stream().distinct().forEach(receiverId -> send(receiverId, type, title, content, sourceType, sourceId, linkView));
    }

    private String limit(String value, int max, String fallback) {
        if (AuthService.isBlank(value)) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
