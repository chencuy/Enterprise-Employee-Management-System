package com.ssm.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IpRateLimitService {
    private static final int MAX_CHECK_IN_PER_MINUTE = 10;
    private static final int MAX_CHECK_IN_PER_HOUR = 60;
    private static final int MAX_TRACKED_IPS = 5000;
    private static final long WINDOW_SECONDS = 60;
    private static final long HOUR_WINDOW_SECONDS = 3600;

    private final Map<String, IpRecord> records = new ConcurrentHashMap<>();

    public void assertAllowed(String ip) {
        Instant now = Instant.now();
        cleanup(now);
        IpRecord record = records.compute(ip, (key, existing) -> {
            if (existing == null) {
                return new IpRecord(now);
            }
            if (now.getEpochSecond() - existing.windowStartEpochSecond >= HOUR_WINDOW_SECONDS) {
                return new IpRecord(now);
            }
            if (now.getEpochSecond() - existing.minuteWindowStartEpochSecond >= WINDOW_SECONDS) {
                existing.minuteWindowStartEpochSecond = now.getEpochSecond();
                existing.minuteCount = 0;
            }
            return existing;
        });

        if (record.minuteCount >= MAX_CHECK_IN_PER_MINUTE) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "签到请求过于频繁，请 " + WINDOW_SECONDS + " 秒后再试");
        }
        if (record.hourCount >= MAX_CHECK_IN_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "本小时签到次数已达上限，请稍后再试");
        }
    }

    public void record(String ip) {
        records.computeIfPresent(ip, (key, record) -> {
            record.minuteCount++;
            record.hourCount++;
            return record;
        });
    }

    private void cleanup(Instant now) {
        long epochSecond = now.getEpochSecond();
        records.entrySet().removeIf(entry -> epochSecond - entry.getValue().windowStartEpochSecond >= HOUR_WINDOW_SECONDS);
        if (records.size() <= MAX_TRACKED_IPS) {
            return;
        }
        int removeCount = records.size() - MAX_TRACKED_IPS;
        for (String key : records.keySet()) {
            if (removeCount-- <= 0) {
                break;
            }
            records.remove(key);
        }
    }

    private static class IpRecord {
        long minuteWindowStartEpochSecond;
        long windowStartEpochSecond;
        int minuteCount;
        int hourCount;

        IpRecord(Instant now) {
            this.minuteWindowStartEpochSecond = now.getEpochSecond();
            this.windowStartEpochSecond = now.getEpochSecond();
            this.minuteCount = 0;
            this.hourCount = 0;
        }
    }
}
