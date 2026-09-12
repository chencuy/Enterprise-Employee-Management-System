package com.ssm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Removes archived files older than the configured natural-day window. */
@Component
public class LogRetentionCleanup {
    private static final Logger log = LoggerFactory.getLogger(LogRetentionCleanup.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Path logFile;
    private final int retentionDays;
    private final boolean cleanOnStartup;
    private final Pattern archivePattern;

    public LogRetentionCleanup(
            @Value("${logging.file.name:${user.dir}/data/logs/application.log}") String logFileName,
            @Value("${LOG_RETENTION_NATURAL_DAYS:3}") int retentionDays,
            @Value("${logging.logback.rollingpolicy.clean-history-on-start:true}") boolean cleanOnStartup) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("LOG_RETENTION_NATURAL_DAYS must be at least 1");
        }
        this.logFile = Path.of(logFileName).toAbsolutePath().normalize();
        this.retentionDays = retentionDays;
        this.cleanOnStartup = cleanOnStartup;
        String baseName = Pattern.quote(this.logFile.getFileName().toString());
        this.archivePattern = Pattern.compile(baseName + "\\.(\\d{4}-\\d{2}-\\d{2})\\..*\\.gz");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanOnStartup() {
        if (cleanOnStartup) {
            cleanExpiredArchives();
        }
    }

    @Scheduled(fixedDelayString = "${LOG_RETENTION_CLEANUP_INTERVAL_MS:3600000}")
    public void cleanScheduled() {
        cleanExpiredArchives();
    }

    void cleanExpiredArchives() {
        Path directory = logFile.getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        LocalDate firstRetainedDate = LocalDate.now().minusDays(retentionDays - 1L);
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile).forEach(path -> deleteIfExpired(path, firstRetainedDate));
        } catch (IOException exception) {
            log.warn("Unable to scan log archive directory {}", directory, exception);
        }
    }

    private void deleteIfExpired(Path path, LocalDate firstRetainedDate) {
        Matcher matcher = archivePattern.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return;
        }
        try {
            LocalDate archiveDate = LocalDate.parse(matcher.group(1), DATE_FORMAT);
            if (archiveDate.isBefore(firstRetainedDate)) {
                Files.deleteIfExists(path);
            }
        } catch (DateTimeParseException exception) {
            log.warn("Ignoring log archive with invalid date {}", path.getFileName());
        } catch (IOException exception) {
            log.warn("Unable to delete expired log archive {}", path, exception);
        }
    }
}
