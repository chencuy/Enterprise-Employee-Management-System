package com.ssm.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ActiveSessionService {
    private static final int MAX_ACTIVE_SESSIONS = 10000;

    private final Map<Long, HttpSession> activeSessions = new ConcurrentHashMap<>();

    public boolean register(Long userId, HttpSession session) {
        if (userId == null || session == null) {
            return false;
        }
        cleanupInvalidSessions();
        HttpSession previous = activeSessions.put(userId, session);
        trimIfNeeded();
        if (previous != null && !sameSession(previous, session)) {
            invalidateQuietly(previous);
            return true;
        }
        return false;
    }

    public boolean isCurrent(Long userId, HttpSession session) {
        if (userId == null || session == null) {
            return false;
        }
        cleanupInvalidSessions();
        HttpSession current = activeSessions.get(userId);
        return current != null && sameSession(current, session);
    }

    public void unregister(Long userId, HttpSession session) {
        if (userId == null || session == null) {
            return;
        }
        activeSessions.computeIfPresent(userId, (id, current) -> sameSession(current, session) ? null : current);
    }

    private boolean sameSession(HttpSession left, HttpSession right) {
        try {
            return left.getId().equals(right.getId());
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private void invalidateQuietly(HttpSession session) {
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // Already invalidated.
        }
    }

    private void cleanupInvalidSessions() {
        activeSessions.entrySet().removeIf(entry -> {
            try {
                entry.getValue().getId();
                return false;
            } catch (IllegalStateException exception) {
                return true;
            }
        });
    }

    private void trimIfNeeded() {
        if (activeSessions.size() <= MAX_ACTIVE_SESSIONS) {
            return;
        }
        int removeCount = activeSessions.size() - MAX_ACTIVE_SESSIONS;
        for (Long userId : activeSessions.keySet()) {
            if (removeCount-- <= 0) {
                break;
            }
            HttpSession removed = activeSessions.remove(userId);
            if (removed != null) {
                invalidateQuietly(removed);
            }
        }
    }
}
