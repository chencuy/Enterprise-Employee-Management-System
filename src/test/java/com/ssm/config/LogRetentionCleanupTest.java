package com.ssm.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogRetentionCleanupTest {
    @Test
    void removesArchivesOlderThanThreeNaturalDays() throws Exception {
        Path directory = Files.createTempDirectory("ems-log-retention-");
        Path logFile = directory.resolve("application.log");
        LocalDate today = LocalDate.now();
        Path retained = directory.resolve("application.log." + today.minusDays(2) + ".0.gz");
        Path expired = directory.resolve("application.log." + today.minusDays(3) + ".0.gz");
        Files.createFile(retained);
        Files.createFile(expired);

        LogRetentionCleanup cleanup = new LogRetentionCleanup(logFile.toString(), 3, true);
        cleanup.cleanExpiredArchives();

        assertTrue(Files.exists(retained));
        assertFalse(Files.exists(expired));
    }
}
