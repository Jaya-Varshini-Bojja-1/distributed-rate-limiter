package com.ratelimiter.config.plan;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    List<Plan> findAllByOrderByIdAsc();

    Optional<Plan> findByCode(String code);

    boolean existsByCode(String code);
}
