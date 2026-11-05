package com.ratelimiter.config.apikey;

import java.util.List;
import java.util.Optional;

import com.ratelimiter.config.snapshot.ActiveKeyRow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    @Query("""
            select new com.ratelimiter.config.snapshot.ActiveKeyRow(
                key.keyHash, client.id, plan.id, client.status)
            from ApiKey key
            join key.client client
            join client.plan plan
            where key.revokedAt is null
            order by key.id asc
            """)
    List<ActiveKeyRow> findActiveForSnapshot();

    List<ApiKey> findByClient_IdOrderByIdAsc(Long clientId);

    Optional<ApiKey> findByIdAndClient_Id(Long id, Long clientId);
}
