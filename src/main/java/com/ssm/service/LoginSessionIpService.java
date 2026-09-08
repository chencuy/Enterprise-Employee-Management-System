package com.ssm.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginSessionIpService {
    private final Map<Long, String> activeIps = new ConcurrentHashMap<>();

    public boolean replace(Long userId, String ipAddress) {
        if (userId == null) {
            return false;
        }
        String currentIp = ipAddress == null ? "" : ipAddress;
        String previousIp = activeIps.put(userId, currentIp);
        return previousIp != null && !Objects.equals(previousIp, currentIp);
    }

    public void unregister(Long userId) {
        if (userId != null) {
            activeIps.remove(userId);
        }
    }
}
