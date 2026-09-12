package com.ssm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class AvatarStorageService {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final Pattern ACTIVE_NAME = Pattern.compile("[0-9]{1,10}_avatar_[0-9]{17}\\.(png|jpg)");
    private static final Pattern PENDING_NAME = Pattern.compile("pending/[0-9]{1,10}_avatar_[0-9]{17}\\.(png|jpg)");

    private final Path avatarDir;
    private final Path pendingDir;

    public AvatarStorageService(@Value("${app.avatar.storage-dir:${user.dir}/data/avatars}") String avatarDir) {
        this.avatarDir = Paths.get(avatarDir).toAbsolutePath().normalize();
        this.pendingDir = this.avatarDir.resolve("pending").normalize();
    }

    public String store(MultipartFile file, Long employeeId, boolean pending, boolean png) throws IOException {
        Path targetDir = pending ? pendingDir : avatarDir;
        if (pending) {
            deletePending(employeeId);
        }
        Files.createDirectories(targetDir);
        String name = employeeFileName(employeeId, png);
        Path target = targetDir.resolve(name).normalize();
        if (!target.startsWith(targetDir)) {
            throw new IOException("头像路径无效");
        }
        try (var input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return pending ? "pending/" + name : name;
    }

    public void deletePending(Long employeeId) {
        String prefix = employeePrefix(employeeId) + "avatar_";
        try {
            if (!Files.isDirectory(pendingDir)) return;
            try (var paths = Files.list(pendingDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().startsWith(prefix))
                        .forEach(this::deleteQuietly);
            }
        } catch (IOException ignored) { }
    }

    public String promote(String pendingPath, Long employeeId) throws IOException {
        if (!isPendingPath(pendingPath)) {
            throw new IOException("待审批头像路径无效");
        }
        String name = pendingPath.substring("pending/".length());
        if (!name.startsWith(employeePrefix(employeeId))) {
            throw new IOException("头像与申请人不匹配");
        }
        Path source = pendingDir.resolve(name).normalize();
        Path target = avatarDir.resolve(name).normalize();
        if (!source.startsWith(pendingDir) || !target.startsWith(avatarDir) || !Files.isReadable(source)) {
            throw new IOException("待审批头像不存在");
        }
        Files.createDirectories(avatarDir);
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    public String activeName(String storedPath) throws IOException {
        if (!isPendingPath(storedPath)) {
            throw new IOException("待审批头像路径无效");
        }
        return storedPath.substring("pending/".length());
    }

    public void cleanupOldActive(Long employeeId, String keepName) {
        String prefix = employeePrefix(employeeId);
        try {
            if (!Files.isDirectory(avatarDir)) return;
            try (var paths = Files.list(avatarDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().startsWith(prefix + "avatar_"))
                        .filter(path -> !Objects.equals(path.getFileName().toString(), keepName))
                        .forEach(this::deleteQuietly);
            }
        } catch (IOException ignored) {
            // A later reconciliation can remove an old unreferenced avatar.
        }
    }

    public void delete(String storedPath) {
        if (storedPath == null) return;
        Path path;
        if (isPendingPath(storedPath)) {
            path = pendingDir.resolve(storedPath.substring("pending/".length())).normalize();
            if (!path.startsWith(pendingDir)) return;
        } else if (isActivePath(storedPath)) {
            path = avatarDir.resolve(storedPath).normalize();
            if (!path.startsWith(avatarDir)) return;
        } else {
            return;
        }
        deleteQuietly(path);
    }

    public Path resolveActive(String name) {
        if (!isActivePath(name)) return null;
        Path path = avatarDir.resolve(name).normalize();
        return path.startsWith(avatarDir) ? path : null;
    }

    public boolean isActivePath(String name) {
        return name != null && ACTIVE_NAME.matcher(name).matches();
    }

    public boolean isPendingPath(String name) {
        return name != null && PENDING_NAME.matcher(name).matches();
    }

    private String employeeFileName(Long employeeId, boolean png) {
        return employeePrefix(employeeId) + "avatar_" + LocalDateTime.now().format(FILE_TIME) + (png ? ".png" : ".jpg");
    }

    private String employeePrefix(Long employeeId) {
        if (employeeId == null || employeeId < 0 || employeeId > 9_999_999_999L) {
            throw new IllegalArgumentException("员工编号无效");
        }
        return String.format("%02d_", employeeId);
    }

    private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }
}
