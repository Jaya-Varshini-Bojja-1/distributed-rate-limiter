package com.ratelimiter.config.client;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public interface ClientRepository extends JpaRepository<Client, Long>, JpaSpecificationExecutor<Client> {

    @Override
    @EntityGraph(attributePaths = "plan")
    Page<Client> findAll(Specification<Client> spec, Pageable pageable);

    boolean existsByPlan_Id(Long planId);
}
