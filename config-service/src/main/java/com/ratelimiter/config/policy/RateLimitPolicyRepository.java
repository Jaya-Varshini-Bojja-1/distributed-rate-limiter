package com.ratelimiter.config.policy;

import java.util.List;
import java.util.Optional;

import com.ratelimiter.config.snapshot.EnabledPolicyRow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RateLimitPolicyRepository extends JpaRepository<RateLimitPolicy, Long> {

    @Query("""
            select new com.ratelimiter.config.snapshot.EnabledPolicyRow(
                plan.id, plan.code, policy.id, policy.pathPattern, policy.httpMethod,
                policy.capacity, policy.refillTokens, policy.refillPeriodSeconds)
            from RateLimitPolicy policy
            join policy.plan plan
            where policy.enabled = true
            order by plan.id asc, policy.id asc
            """)
    List<EnabledPolicyRow> findEnabledForSnapshot();

    List<RateLimitPolicy> findByPlan_IdOrderByIdAsc(Long planId);

    Optional<RateLimitPolicy> findByIdAndPlan_Id(Long id, Long planId);

    Optional<RateLimitPolicy> findByPlan_IdAndPathPatternAndHttpMethod(
            Long planId,
            String pathPattern,
            HttpMethodMatch httpMethod);
}
