package com.ssm.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class ConcurrencyIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("employee_management")
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("drop table if exists approval_requests");
            s.execute("drop table if exists leave_requests");
            s.execute("drop table if exists employees");
            s.execute("create table employees (id bigint primary key, salary decimal(12,2) not null)");
            s.execute("create table approval_requests (id bigint primary key, status varchar(20) not null, reviewer_id bigint, review_comment varchar(500), reviewed_at datetime, updated_at datetime)");
            s.execute("create table leave_requests (id bigint primary key, status varchar(20) not null, approver_id bigint, review_comment varchar(500), reviewed_at datetime, updated_at datetime)");
            s.execute("insert into employees (id,salary) values (1,1000.00)");
            s.execute("insert into approval_requests (id,status) values (10,'PENDING')");
            s.execute("insert into leave_requests (id,status) values (20,'PENDING')");
        }
    }

    @Test
    void concurrentApprovalReviewAllowsOnlyOneWinner() throws Exception {
        List<Integer> results = concurrently(() -> reviewApproval("APPROVED"), () -> reviewApproval("REJECTED"));
        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(queryString("select status from approval_requests where id=10")).isIn("APPROVED", "REJECTED");
    }

    @Test
    void concurrentLeaveReviewAllowsOnlyOneWinner() throws Exception {
        List<Integer> results = concurrently(() -> reviewLeave("APPROVED"), () -> reviewLeave("REJECTED"));
        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(queryString("select status from leave_requests where id=20")).isIn("APPROVED", "REJECTED");
    }

    @Test
    void concurrentSalaryUpdatesDoNotLoseAnUpdate() throws Exception {
        List<Integer> results = concurrently(
                () -> updateSalary(new BigDecimal("1100.00")),
                () -> updateSalary(new BigDecimal("1200.00")));
        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(queryDecimal("select salary from employees where id=1"))
                .isIn(new BigDecimal("1100.00"), new BigDecimal("1200.00"));
    }

    private int reviewApproval(String status) throws Exception {
        try (Connection c = connection(); var p = c.prepareStatement("update approval_requests set status=?, reviewed_at=now() where id=10 and status='PENDING'")) {
            p.setString(1, status); return p.executeUpdate();
        }
    }

    private int reviewLeave(String status) throws Exception {
        try (Connection c = connection(); var p = c.prepareStatement("update leave_requests set status=?, reviewed_at=now() where id=20 and status='PENDING'")) {
            p.setString(1, status); return p.executeUpdate();
        }
    }

    private int updateSalary(BigDecimal salary) throws Exception {
        try (Connection c = connection(); var p = c.prepareStatement("update employees set salary=? where id=1 and salary <=> ?")) {
            p.setBigDecimal(1, salary); p.setBigDecimal(2, new BigDecimal("1000.00")); return p.executeUpdate();
        }
    }

    private Connection connection() throws Exception { return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()); }
    private String queryString(String sql) throws Exception { try (Connection c=connection(); var s=c.createStatement(); var r=s.executeQuery(sql)) { r.next(); return r.getString(1); } }
    private BigDecimal queryDecimal(String sql) throws Exception { try (Connection c=connection(); var s=c.createStatement(); var r=s.executeQuery(sql)) { r.next(); return r.getBigDecimal(1); } }

    @SafeVarargs
    private final List<Integer> concurrently(java.util.concurrent.Callable<Integer>... calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        CountDownLatch ready = new CountDownLatch(calls.length);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (var call : calls) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return call.call();
            }));
        }
        ready.await();
        start.countDown();
        List<Integer> results = new ArrayList<>();
        for (Future<Integer> future : futures) results.add(future.get());
        pool.shutdownNow();
        return results;
    }
}
