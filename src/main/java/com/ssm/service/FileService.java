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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
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
        validateUploadFile(ext, file.getContentType(), file);
        String storedName = UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
        Path target = storageDir.resolve(storedName);
        Path temporaryTarget = storageDir.resolve(storedName + ".upload");
        try {
            Files.createDirectories(storageDir);
            try (var input = file.getInputStream()) {
                Files.copy(input, temporaryTarget, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporaryTarget, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryTarget, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            deleteQuietly(temporaryTarget);
            deleteQuietly(target);
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
            deleteQuietly(temporaryTarget);
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
        Path path = storageDir.resolve(file.storedName).normalize();
        if (!path.startsWith(storageDir)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件路径无效");
        }
        Path deletingPath = storageDir.resolve(file.storedName + ".deleting-" + UUID.randomUUID()).normalize();
        boolean moved = false;
        try {
            if (Files.exists(path)) {
                try {
                    Files.move(path, deletingPath, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(path, deletingPath);
                }
                moved = true;
            }
            if (fileMapper.delete(id) != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "文件已被其他操作删除，请刷新后重试");
            }
            auditLogService.record(user, "文件管理", "删除文件", "file", file.id, file.originalName, "删除已分发文件");
            Files.deleteIfExists(deletingPath);
        } catch (IOException e) {
            restoreAfterDeleteFailure(path, deletingPath, moved);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件清理失败，删除操作已取消");
        } catch (RuntimeException e) {
            restoreAfterDeleteFailure(path, deletingPath, moved);
            throw e;
        }
    }

    private void restoreAfterDeleteFailure(Path original, Path deleting, boolean moved) {
        if (!moved) {
            return;
        }
        try {
            if (Files.exists(deleting) && !Files.exists(original)) {
                Files.move(deleting, original, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // A cleanup task can reconcile a remaining .deleting-* file.
        }
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

    private void validateUploadFile(String ext, String contentType, MultipartFile file) {
        if (ext == null || ext.isBlank() || !allowedExtensions.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的文件类型");
        }
        String normalizedContentType = normalizeContentType(contentType);
        if (!normalizedContentType.isEmpty() && !allowedContentTypes.contains(normalizedContentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的文件内容类型");
        }
        if (!normalizedContentType.isEmpty() && !isContentTypeCompatible(ext, normalizedContentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件内容类型与扩展名不匹配");
        }
        try {
            byte[] header = readHeader(file.getInputStream());
            if (!matchesDeclaredType(ext, header)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件扩展名与实际内容不匹配");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取文件内容");
        }
    }

    private static byte[] readHeader(InputStream input) throws IOException {
        try (input) {
            byte[] header = new byte[8192];
            int offset = 0;
            int read;
            while (offset < header.length && (read = input.read(header, offset, header.length - offset)) > 0) {
                offset += read;
            }
            return java.util.Arrays.copyOf(header, offset);
        }
    }

    private static boolean matchesDeclaredType(String ext, byte[] data) {
        if (data.length == 0) {
            return false;
        }
        return switch (ext) {
            case "png" -> startsWith(data, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "jpg", "jpeg" -> data.length >= 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8 && (data[2] & 0xff) == 0xff;
            case "pdf" -> startsWith(data, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "zip", "docx", "xlsx", "pptx" -> isZip(data);
            case "doc", "xls", "ppt" -> startsWith(data, new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1});
            case "txt", "csv" -> isText(data);
            default -> false;
        };
    }

    private static boolean isContentTypeCompatible(String ext, String contentType) {
        return switch (ext) {
            case "png" -> "image/png".equals(contentType);
            case "jpg", "jpeg" -> "image/jpeg".equals(contentType);
            case "pdf" -> "application/pdf".equals(contentType);
            case "zip" -> Set.of("application/zip", "application/x-zip-compressed").contains(contentType);
            case "txt" -> "text/plain".equals(contentType);
            case "csv" -> Set.of("text/csv", "application/csv").contains(contentType);
            case "doc" -> "application/msword".equals(contentType);
            case "xls" -> "application/vnd.ms-excel".equals(contentType);
            case "ppt" -> "application/vnd.ms-powerpoint".equals(contentType);
            case "docx" -> Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip").contains(contentType);
            case "xlsx" -> Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip").contains(contentType);
            case "pptx" -> Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/zip").contains(contentType);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }

    private static boolean isZip(byte[] data) {
        return data.length >= 4 && data[0] == 'P' && data[1] == 'K'
                && ((data[2] == 3 && data[3] == 4) || (data[2] == 5 && data[3] == 6) || (data[2] == 7 && data[3] == 8));
    }

    private static boolean isText(byte[] data) {
        for (byte value : data) {
            if (value == 0) return false;
        }
        return true;
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
