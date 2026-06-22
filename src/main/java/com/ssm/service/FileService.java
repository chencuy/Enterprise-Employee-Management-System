package com.ssm.service;

import com.ssm.dto.FileRecipientDetail;
import com.ssm.dto.PageResult;
import com.ssm.dto.SessionUser;
import com.ssm.entity.Employee;
import com.ssm.entity.SharedFile;
import com.ssm.mapper.EmployeeMapper;
import com.ssm.mapper.FileMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {
    private static final long MAX_FILE_BYTES = 25L * 1024 * 1024;

    private final FileMapper fileMapper;
    private final EmployeeMapper employeeMapper;
    private final AuditLogService auditLogService;
    private final NotificationCenterService notificationCenterService;
    private final Path storageDir;
    private final Set<String> allowedExtensions;
    private final Set<String> allowedContentTypes;

    public FileService(
            FileMapper fileMapper,
            EmployeeMapper employeeMapper,
            AuditLogService auditLogService,
            NotificationCenterService notificationCenterService,
            @Value("${app.file.storage-dir:./data/files}") String storageDir,
            @Value("${app.file.allowed-extensions:pdf,doc,docx,xls,xlsx,ppt,pptx,txt,csv,png,jpg,jpeg,zip}") String allowedExtensions,
            @Value("${app.file.allowed-content-types:application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation,text/plain,text/csv,application/csv,image/png,image/jpeg,application/zip,application/x-zip-compressed}") String allowedContentTypes
    ) {
        this.fileMapper = fileMapper;
        this.employeeMapper = employeeMapper;
        this.auditLogService = auditLogService;
        this.notificationCenterService = notificationCenterService;
        this.storageDir = Paths.get(storageDir).toAbsolutePath().normalize();
        this.allowedExtensions = parseCsvSet(allowedExtensions);
        this.allowedContentTypes = parseCsvSet(allowedContentTypes);
    }

    public List<SharedFile> list(SessionUser user) {
        if ("ADMIN".equals(user.role)) {
            return fileMapper.findAllForAdmin();
        }
        return fileMapper.findForUser(user.id);
    }

    public PageResult<SharedFile> page(SessionUser user, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 5);
        int offset = (safePage - 1) * safeSize;
        if ("ADMIN".equals(user.role)) {
            return new PageResult<>(
                    fileMapper.countAllForAdmin(),
                    safePage,
                    safeSize,
                    fileMapper.pageAllForAdmin(offset, safeSize)
            );
        }
        return new PageResult<>(
                fileMapper.countForUser(user.id),
                safePage,
                safeSize,
                fileMapper.pageForUser(user.id, offset, safeSize)
        );
    }

    public int unreadCount(SessionUser user) {
        if ("ADMIN".equals(user.role)) {
            return 0;
        }
        return fileMapper.countUnreadForUser(user.id);
    }

    @Transactional
    public SharedFile distribute(MultipartFile file, List<Long> departmentIds, List<Long> employeeIds, SessionUser user) {
        if (!"ADMIN".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可以分发文件");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要分发的文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件大小不能超过 25MB");
        }

        Set<Long> recipients = resolveRecipients(departmentIds, employeeIds, user.id);
        if (recipients.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少选择一个部门或人员");
        }

        String originalName = sanitizeName(file.getOriginalFilename());
        String ext = extensionOf(originalName);
        validateUploadFile(ext, file.getContentType());
        String storedName = UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
        Path target = storageDir.resolve(storedName);
        try {
            Files.createDirectories(storageDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败");
        }

        try {
            SharedFile shared = new SharedFile();
            shared.originalName = originalName;
            shared.storedName = storedName;
            shared.contentType = normalizeContentType(file.getContentType());
            shared.sizeBytes = file.getSize();
            shared.uploaderId = user.id;
            fileMapper.insert(shared);
            for (Long employeeId : recipients) {
                fileMapper.insertRecipient(shared.id, employeeId);
            }
            shared.uploaderName = user.name;
            shared.recipientCount = recipients.size();
            shared.downloadedCount = 0;
            notificationCenterService.sendToMany(
                    recipients.stream().toList(),
                    "FILE",
                    "新的分发文件",
                    "管理员分发了文件：" + shared.originalName,
                    "file",
                    shared.id,
                    "files"
            );
            auditLogService.record(
                    user,
                    "文件管理",
                    "分发文件",
                    "file",
                    shared.id,
                    shared.originalName,
                    "接收人数：" + recipients.size()
            );
            return shared;
        } catch (RuntimeException exception) {
            deleteQuietly(target);
            throw exception;
        }
    }

    public DownloadResource download(Long id, SessionUser user) {
        SharedFile file = fileMapper.findById(id);
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        String downloadName;
        if ("ADMIN".equals(user.role)) {
            downloadName = file.originalName;
        } else {
            if (fileMapper.isRecipient(id, user.id) == 0) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权下载该文件");
            }
            String ext = extensionOf(file.originalName);
            downloadName = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                    + (ext.isEmpty() ? "" : "." + ext);
        }
        Path path = storageDir.resolve(file.storedName).normalize();
        if (!path.startsWith(storageDir) || !Files.isReadable(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件已丢失");
        }
        long size;
        try {
            size = Files.size(path);
        } catch (IOException e) {
            size = file.sizeBytes == null ? 0 : file.sizeBytes;
        }
        auditLogService.record(
                user,
                "文件管理",
                "下载文件",
                "file",
                file.id,
                file.originalName,
                "下载文件：" + file.originalName
        );
        return new DownloadResource(path, downloadName, file.contentType, size);
    }

    public void markRead(Long id, SessionUser user) {
        if ("ADMIN".equals(user.role)) {
            return;
        }
        if (fileMapper.findById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        if (fileMapper.markRead(id, user.id) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权处理该文件");
        }
    }

    public List<FileRecipientDetail> recipientDetails(Long id, SessionUser user) {
        if (!"ADMIN".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可以查看下载明细");
        }
        if (fileMapper.findById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        return fileMapper.recipientDetails(id);
    }

    @Transactional
    public void delete(Long id, SessionUser user) {
        if (!"ADMIN".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可以删除已分发文件");
        }
        SharedFile file = fileMapper.findById(id);
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        fileMapper.delete(id);
        Path path = storageDir.resolve(file.storedName).normalize();
        if (path.startsWith(storageDir)) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件记录已删除，但磁盘文件清理失败");
            }
        }
        auditLogService.record(user, "文件管理", "删除文件", "file", file.id, file.originalName, "删除已分发文件");
    }

    private Set<Long> resolveRecipients(List<Long> departmentIds, List<Long> employeeIds, Long uploaderId) {
        Set<Long> recipients = new LinkedHashSet<>();
        if (departmentIds != null) {
            for (Long departmentId : departmentIds) {
                if (departmentId != null) {
                    recipients.addAll(employeeMapper.findActiveIdsByDepartment(departmentId));
                }
            }
        }
        if (employeeIds != null) {
            for (Long employeeId : employeeIds) {
                if (employeeId == null) {
                    continue;
                }
                Employee employee = employeeMapper.findById(employeeId);
                if (employee != null && !"RESIGNED".equals(employee.status)) {
                    recipients.add(employee.id);
                }
            }
        }
        recipients.remove(uploaderId);
        return recipients;
    }

    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "未命名文件";
        }
        String cleaned = name.replace("\\", "/");
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        cleaned = cleaned.trim();
        if (cleaned.isEmpty()) {
            return "未命名文件";
        }
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    private static String extensionOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String ext = name.substring(dot + 1);
        if (ext.length() > 12 || !ext.matches("[A-Za-z0-9]+")) {
            return "";
        }
        return ext.toLowerCase(Locale.ROOT);
    }

    private void validateUploadFile(String ext, String contentType) {
        if (ext == null || ext.isBlank() || !allowedExtensions.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的文件类型");
        }
        String normalizedContentType = normalizeContentType(contentType);
        if (!normalizedContentType.isEmpty() && !allowedContentTypes.contains(normalizedContentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的文件内容类型");
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String normalized = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> parseCsvSet(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String item : value.split(",")) {
            String normalized = item.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The database transaction will roll back; a later cleanup task can remove the orphaned file.
        }
    }

    public static class DownloadResource {
        public final Path path;
        public final String downloadName;
        public final String contentType;
        public final long size;

        public DownloadResource(Path path, String downloadName, String contentType, long size) {
            this.path = path;
            this.downloadName = downloadName;
            this.contentType = contentType;
            this.size = size;
        }
    }
}
