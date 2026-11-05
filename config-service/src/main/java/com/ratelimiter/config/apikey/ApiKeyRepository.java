package com.ratelimiter.config.apikey;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    List<ApiKey> findByClient_IdOrderByIdAsc(Long clientId);

    Optional<ApiKey> findByIdAndClient_Id(Long id, Long clientId);
}
