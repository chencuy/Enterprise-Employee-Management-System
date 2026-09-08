package com.ssm.service;

import com.ssm.dto.MessageRequest;
import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.Employee;
import com.ssm.entity.Message;
import com.ssm.mapper.EmployeeMapper;
import com.ssm.mapper.MessageMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class MessageService {
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final MessageMapper messageMapper;
    private final EmployeeMapper employeeMapper;
    private final NotificationCenterService notificationCenterService;

    public MessageService(MessageMapper messageMapper, EmployeeMapper employeeMapper, NotificationCenterService notificationCenterService) {
        this.messageMapper = messageMapper;
        this.employeeMapper = employeeMapper;
        this.notificationCenterService = notificationCenterService;
    }

    public List<Message> list(SessionUser user) {
        if ("ADMIN".equals(user.role)) {
            return messageMapper.findAll();
        }
        return messageMapper.findForUser(user.id);
    }

    public PageResult<Message> page(SessionUser user, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 5);
        int offset = (safePage - 1) * safeSize;
        if ("ADMIN".equals(user.role)) {
            return new PageResult<>(messageMapper.countAll(), safePage, safeSize, messageMapper.pageAll(offset, safeSize));
        }
        return new PageResult<>(messageMapper.countForUser(user.id), safePage, safeSize, messageMapper.pageForUser(user.id, offset, safeSize));
    }

    public int unreadCount(SessionUser user) {
        return messageMapper.countUnread(user.id);
    }

    public List<Employee> contacts(SessionUser user) {
        if ("ADMIN".equals(user.role)) {
            return employeeMapper.findAllActiveExcept(user.id);
        }
        if ("SUPERVISOR".equals(user.role)) {
            if (user.departmentId == null) {
                return List.of();
            }
            return new ArrayList<>(employeeMapper.findDepartmentEmployees(user.departmentId, user.id));
        }
        Employee employee = employeeMapper.findById(user.id);
        List<Employee> result = new ArrayList<>();
        if (employee != null && employee.departmentId != null) {
            result.addAll(employeeMapper.findDepartmentEmployees(employee.departmentId, user.id));
        }
        return result;
    }

    @Transactional
    public Message send(MessageRequest request, SessionUser user) {
        if (request == null || request.receiverId == null || AuthService.isBlank(request.content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择收件人并填写消息内容");
        }
        if (Objects.equals(request.receiverId, user.id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能给自己发送消息");
        }
        Employee receiver = employeeMapper.findById(request.receiverId);
        if (receiver == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "收件人不存在");
        }
        ensureCanSend(user, receiver, request.replyToMessageId);
        String content = request.content.trim();
        if (content.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息内容不能超过 1000 字");
        }
        Message message = new Message();
        message.senderId = user.id;
        message.receiverId = receiver.id;
        message.content = content;
        messageMapper.insert(message);
        notificationCenterService.send(
                receiver.id,
                "MESSAGE",
                "新的消息",
                user.name + " 给您发送了一条消息。",
                "message",
                message.id,
                "messages"
        );
        return message;
    }

    public void markRead(Long id, SessionUser user) {
        Message message = messageMapper.findById(id);
        if (message == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "消息不存在");
        }
        if (!Objects.equals(message.receiverId, user.id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权标记该消息");
        }
        messageMapper.markRead(id);
    }

    private void ensureCanSend(SessionUser sender, Employee receiver, Long replyToMessageId) {
        if ("ADMIN".equals(sender.role)) {
            return;
        }
        if ("SUPERVISOR".equals(sender.role)) {
            boolean departmentEmployee = "EMPLOYEE".equals(receiver.role)
                    && sender.departmentId != null
                    && Objects.equals(receiver.departmentId, sender.departmentId);
            boolean adminReply = "ADMIN".equals(receiver.role)
                    && isReplyToReceivedMessage(sender, receiver, replyToMessageId);
            if (departmentEmployee || adminReply) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "主管只能主动联系本部门普通员工，或回复管理员消息");
        }
        Employee employee = employeeMapper.findById(sender.id);
        boolean departmentEmployee = employee != null
                && employee.departmentId != null
                && "EMPLOYEE".equals(receiver.role)
                && Objects.equals(employee.departmentId, receiver.departmentId);
        boolean managerOrAdminReply = ("ADMIN".equals(receiver.role) || "SUPERVISOR".equals(receiver.role))
                && isReplyToReceivedMessage(sender, receiver, replyToMessageId);
        if (departmentEmployee || managerOrAdminReply) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "普通员工只能主动联系本部门普通员工，或回复管理员/主管已发送的消息");
    }

    private boolean isReplyToReceivedMessage(SessionUser sender, Employee receiver, Long replyToMessageId) {
        if (replyToMessageId == null) {
            return false;
        }
        Message original = messageMapper.findById(replyToMessageId);
        return original != null
                && Objects.equals(original.senderId, receiver.id)
                && Objects.equals(original.receiverId, sender.id);
    }

}
