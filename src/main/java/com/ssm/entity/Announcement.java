package com.ssm.entity;

import java.time.LocalDateTime;

public class Announcement {
    public Long id;
    public String title;
    public String content;
    public Long publisherId;
    public Boolean isPinned;
    public String status;
    public LocalDateTime createdAt;

    public String publisherName;
    public Boolean readFlag;
    public LocalDateTime readAt;
}
