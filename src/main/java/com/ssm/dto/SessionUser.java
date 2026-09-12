package com.ssm.dto;

public class SessionUser {
    public Long id;
    public String name;
    public String role;
    public String status;
    public Long departmentId;
    public String departmentName;
    public String avatarPath;
    public String csrfToken;
    public boolean hasLockPassword;
    public String lockPasswordResetAt;
    public boolean screenLocked;

    public SessionUser() {
    }

    public SessionUser(Long id, String name, String role, String status, Long departmentId, String departmentName, String avatarPath, String csrfToken, boolean hasLockPassword, String lockPasswordResetAt, boolean screenLocked) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.status = status;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.avatarPath = avatarPath;
        this.csrfToken = csrfToken;
        this.hasLockPassword = hasLockPassword;
        this.lockPasswordResetAt = lockPasswordResetAt;
        this.screenLocked = screenLocked;
    }
}
