package com.ssm.service;

import com.ssm.dto.SessionUser;
import com.ssm.entity.Employee;
import com.ssm.entity.Message;
import com.ssm.mapper.MessageMapper;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class NotificationService {
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final MessageMapper messageMapper;
    private final NotificationCenterService notificationCenterService;

    public NotificationService(MessageMapper messageMapper, NotificationCenterService notificationCenterService) {
        this.messageMapper = messageMapper;
        this.notificationCenterService = notificationCenterService;
    }

    public void sendChangeNotice(SessionUser operator, Employee receiver, String content) {
        sendChangeNotice(operator, receiver, content, "SYSTEM", "系统消息");
    }

    public void sendChangeNotice(SessionUser operator, Employee receiver, String content, String notificationType, String title) {
        if (operator == null || receiver == null || receiver.id == null || AuthService.isBlank(content)) {
            return;
        }
        if (operator.id == null || Objects.equals(operator.id, receiver.id)) {
            return;
        }
        Message message = new Message();
        message.senderId = operator.id;
        message.receiverId = receiver.id;
        message.content = normalizeContent(content);
        messageMapper.insert(message);
        notificationCenterService.send(
                receiver.id,
                notificationType,
                title,
                message.content,
                "message",
                message.id,
                "messages"
        );
    }

    private String normalizeContent(String content) {
        String trimmed = content.trim();
        if (trimmed.length() <= MAX_MESSAGE_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_MESSAGE_LENGTH - 12) + "...";
    }
}
