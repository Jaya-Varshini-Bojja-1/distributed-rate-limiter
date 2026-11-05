package com.ratelimiter.config.snapshot;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.ratelimiter.config.apikey.ApiKeyRepository;
import com.ratelimiter.config.policy.RateLimitPolicyRepository;
import com.ratelimiter.snapshot.ConfigurationSnapshot;

import jakarta.persistence.EntityManager;

import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnapshotService {

    private static final ThreadLocal<Runnable> BETWEEN_QUERIES = new ThreadLocal<>();

    private final RateLimitPolicyRepository policies;
    private final ApiKeyRepository apiKeys;
    private final EntityManager entityManager;

    public SnapshotService(
            RateLimitPolicyRepository policies,
            ApiKeyRepository apiKeys,
            EntityManager entityManager) {
        this.policies = policies;
        this.apiKeys = apiKeys;
        this.entityManager = entityManager;
    }

    /**
     * Test seam for the repeatable-read check. Production leaves it empty.
     * Stored on the calling thread so the Spring proxy and the target share it.
     */
    void setBetweenQueries(Runnable betweenQueries) {
        BETWEEN_QUERIES.set(betweenQueries);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConfigurationSnapshot load() {
        try {
            // First statement, so PostgreSQL applies this isolation before either data query.
            entityManager.unwrap(Session.class).doWork(SnapshotService::readOnlyRepeatableRead);
            List<EnabledPolicyRow> policyRows = policies.findEnabledForSnapshot();
            Runnable betweenQueries = BETWEEN_QUERIES.get();
            if (betweenQueries != null) {
                betweenQueries.run();
            }
            List<ActiveKeyRow> keyRows = apiKeys.findActiveForSnapshot();
            return SnapshotAssembler.assemble(policyRows, keyRows);
        } finally {
            BETWEEN_QUERIES.remove();
        }
    }

    private static void readOnlyRepeatableRead(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY");
        }
    }
}
