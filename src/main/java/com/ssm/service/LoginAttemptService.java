package com.ssm.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {
    private static final int MAX_FAILURES = 5;
    private static final int MAX_TRACKED_ATTEMPTS = 5000;
    private static final long LOCK_SECONDS = 15 * 60;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void assertNotLocked(String key) {
        cleanup();
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return;
        }
        if (attempt.lockedUntilEpochSecond <= Instant.now().getEpochSecond()) {
            attempts.remove(key);
            return;
        }
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录失败次数过多，请稍后再试");
    }

    public void recordFailure(String key) {
        cleanup();
        attempts.compute(key, (ignored, current) -> {
            Attempt attempt = current == null ? new Attempt() : current;
            attempt.failures++;
            attempt.lastAttemptEpochSecond = Instant.now().getEpochSecond();
            if (attempt.failures >= MAX_FAILURES) {
                attempt.lockedUntilEpochSecond = Instant.now().getEpochSecond() + LOCK_SECONDS;
            }
            return attempt;
        });
    }

    public void clear(String key) {
        attempts.remove(key);
    }

    private void cleanup() {
        long now = Instant.now().getEpochSecond();
        attempts.entrySet().removeIf(entry -> {
            Attempt attempt = entry.getValue();
            if (attempt.lockedUntilEpochSecond > 0) {
                return attempt.lockedUntilEpochSecond <= now;
            }
            return attempt.lastAttemptEpochSecond > 0 && now - attempt.lastAttemptEpochSecond >= LOCK_SECONDS;
        });
        if (attempts.size() <= MAX_TRACKED_ATTEMPTS) {
            return;
        }
        int removeCount = attempts.size() - MAX_TRACKED_ATTEMPTS;
        for (String key : attempts.keySet()) {
            if (removeCount-- <= 0) {
                break;
            }
            attempts.remove(key);
        }
    }

    private static class Attempt {
        int failures;
        long lockedUntilEpochSecond;
        long lastAttemptEpochSecond;
    }
}
