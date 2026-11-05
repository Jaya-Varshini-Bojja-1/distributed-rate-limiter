package com.ratelimiter.config.policy;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitPolicyRepository extends JpaRepository<RateLimitPolicy, Long> {

    List<RateLimitPolicy> findByPlan_IdOrderByIdAsc(Long planId);

    Optional<RateLimitPolicy> findByIdAndPlan_Id(Long id, Long planId);

    Optional<RateLimitPolicy> findByPlan_IdAndPathPatternAndHttpMethod(
            Long planId,
            String pathPattern,
            HttpMethodMatch httpMethod);
}
