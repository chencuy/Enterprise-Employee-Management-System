package com.ssm.controller;

import com.ssm.dto.ApiResponse;
import com.ssm.dto.MessageRequest;
import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.Employee;
import com.ssm.entity.Message;
import com.ssm.service.AuthService;
import com.ssm.service.MessageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final AuthService authService;
    private final MessageService messageService;

    public MessageController(AuthService authService, MessageService messageService) {
        this.authService = authService;
        this.messageService = messageService;
    }

    @GetMapping
    public ApiResponse<PageResult<Message>> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            HttpSession session
    ) {
        SessionUser user = authService.currentUser(session);
        return ApiResponse.ok(messageService.page(user, page, size));
    }

    @GetMapping("/contacts")
    public ApiResponse<List<Employee>> contacts(HttpSession session) {
        SessionUser user = authService.currentUser(session);
        return ApiResponse.ok(messageService.contacts(user));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Integer> unreadCount(HttpSession session) {
        return ApiResponse.ok(messageService.unreadCount(authService.currentUser(session)));
    }

    @PostMapping
    public ApiResponse<Message> send(@RequestBody MessageRequest request, HttpSession session) {
        return ApiResponse.ok(messageService.send(request, authService.currentUser(session)));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable(name = "id") Long id, HttpSession session) {
        messageService.markRead(id, authService.currentUser(session));
        return ApiResponse.ok();
    }
}
