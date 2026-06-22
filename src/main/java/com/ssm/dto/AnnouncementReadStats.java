package com.ssm.dto;

import java.util.List;

public class AnnouncementReadStats {
    public int total;
    public int readCount;
    public int unreadCount;
    public List<AnnouncementReadDetail> readList;
    public List<AnnouncementReadDetail> unreadList;
}
