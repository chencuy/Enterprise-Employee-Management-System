package com.ssm.entity;

import java.time.LocalDateTime;

public class NotificationItem {
    public Long id;
    public Long receiverId;
    public String type;
    public String title;
    public String content;
    public String sourceType;
    public Long sourceId;
    public String linkView;
    public Boolean readFlag;
    public LocalDateTime readAt;
    public LocalDateTime createdAt;

    public String receiverName;
}
