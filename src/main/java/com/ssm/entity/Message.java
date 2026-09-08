package com.ssm.entity;

import java.time.LocalDateTime;

public class Message {
    public Long id;
    public Long senderId;
    public Long receiverId;
    public String content;
    public Boolean readFlag;
    public LocalDateTime createdAt;

    public String senderName;
    public String senderRole;
    public String receiverName;
    public String receiverRole;
}
