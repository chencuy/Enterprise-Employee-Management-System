package com.ssm.service;

import com.ssm.dto.EmployeeLeaveWindow;
import com.ssm.mapper.LeaveStatusMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class LeaveStatusService {
    private static final Logger log = LoggerFactory.getLogger(LeaveStatusService.class);

    private final LeaveStatusMapper leaveStatusMapper;

    public LeaveStatusService(LeaveStatusMapper leaveStatusMapper) {
        this.leaveStatusMapper = leaveStatusMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        refreshAllLeaveStatuses();
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")
    public void refreshOnSchedule() {
        refreshAllLeaveStatuses();
    }

    @Transactional
    public int refreshAllLeaveStatuses() {
        LocalDate today = LocalDate.now();
        int activated = leaveStatusMapper.activateCurrentApprovedLeaves(today);
        int deactivated = leaveStatusMapper.deactivateInactiveLeaveStatuses(today);
        int total = activated + deactivated;
        if (total > 0) {
            log.info("Synchronized leave statuses, activated={}, deactivated={}", activated, deactivated);
        }
        return total;
    }

    @Transactional
    public int refreshEmployeeLeaveStatus(Long employeeId) {
        if (employeeId == null) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        return leaveStatusMapper.activateCurrentApprovedLeave(employeeId, today)
                + leaveStatusMapper.deactivateInactiveLeaveStatus(employeeId, today);
    }

    public boolean isOnApprovedLeave(Long employeeId, LocalDate date) {
        if (employeeId == null || date == null) {
            return false;
        }
        return leaveStatusMapper.countApprovedLeaveOnDate(employeeId, date) > 0;
    }

    public Set<Long> findEmployeesOnApprovedLeave(Collection<Long> employeeIds, LocalDate date) {
        if (employeeIds == null || employeeIds.isEmpty() || date == null) {
            return Set.of();
        }
        List<Long> ids = employeeIds.stream().filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(leaveStatusMapper.findApprovedLeaveEmployeeIdsOnDate(ids, date));
    }

    public Set<String> leaveKeysForRange(Collection<Long> employeeIds, LocalDate startDate, LocalDate endDate) {
        if (employeeIds == null || employeeIds.isEmpty() || startDate == null || endDate == null) {
            return Set.of();
        }
        List<Long> ids = employeeIds.stream().filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (EmployeeLeaveWindow window : leaveStatusMapper.findApprovedLeaveWindows(ids, startDate, endDate)) {
            LocalDate from = window.startDate.isBefore(startDate) ? startDate : window.startDate;
            LocalDate to = window.endDate.isAfter(endDate) ? endDate : window.endDate;
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                keys.add(window.employeeId + "|" + date);
            }
        }
        return keys;
    }
}
