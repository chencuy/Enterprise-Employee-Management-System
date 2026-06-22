package com.ssm.controller;

import com.ssm.dto.ApiResponse;
import com.ssm.dto.AdminVerificationRequest;
import com.ssm.dto.FileRecipientDetail;
import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.SharedFile;
import com.ssm.service.AuthService;
import com.ssm.service.FileService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final AuthService authService;
    private final FileService fileService;

    public FileController(AuthService authService, FileService fileService) {
        this.authService = authService;
        this.fileService = fileService;
    }

    @GetMapping
    public ApiResponse<List<SharedFile>> list(HttpSession session) {
        SessionUser user = authService.currentUser(session);
        return ApiResponse.ok(fileService.list(user));
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<SharedFile>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            HttpSession session
    ) {
        SessionUser user = authService.currentUser(session);
        return ApiResponse.ok(fileService.page(user, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Integer> unreadCount(HttpSession session) {
        return ApiResponse.ok(fileService.unreadCount(authService.currentUser(session)));
    }

    @PostMapping
    public ApiResponse<SharedFile> distribute(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "departmentIds", required = false) List<Long> departmentIds,
            @RequestParam(value = "employeeIds", required = false) List<Long> employeeIds,
            HttpSession session
    ) {
        SessionUser user = authService.currentUser(session);
        return ApiResponse.ok(fileService.distribute(file, departmentIds, employeeIds, user));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable(name = "id") Long id,
            @RequestBody(required = false) AdminVerificationRequest request,
            HttpSession session
    ) {
        var user = authService.currentUser(session);
        authService.requireCurrentPassword(user, request == null ? null : request.adminPassword);
        fileService.delete(id, user);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/recipients")
    public ApiResponse<List<FileRecipientDetail>> recipients(@PathVariable(name = "id") Long id, HttpSession session) {
        return ApiResponse.ok(fileService.recipientDetails(id, authService.currentUser(session)));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable(name = "id") Long id, HttpSession session) {
        fileService.markRead(id, authService.currentUser(session));
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable(name = "id") Long id, HttpSession session) {
        SessionUser user = authService.currentUser(session);
        FileService.DownloadResource download = fileService.download(id, user);
        Resource resource = new FileSystemResource(download.path);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.downloadName, StandardCharsets.UTF_8)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        if (download.size > 0) {
            builder.contentLength(download.size);
        }
        return builder.body(resource);
    }
}
