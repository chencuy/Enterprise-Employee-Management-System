package com.ssm.entity;

import java.time.LocalDateTime;

public class SharedFile {
    public Long id;
    public String originalName;
    public String storedName;
    public String contentType;
    public Long sizeBytes;
    public Long uploaderId;
    public LocalDateTime createdAt;

    // joined / per-view fields
    public String uploaderName;
    public Boolean readFlag;
    public LocalDateTime downloadedAt;
    public Integer recipientCount;
    public Integer downloadedCount;
}
