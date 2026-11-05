package com.ratelimiter.config.plan;

import java.util.List;

import com.ratelimiter.config.client.ClientRepository;
import com.ratelimiter.config.error.ApiException;
import com.ratelimiter.config.error.ErrorCodes;
import com.ratelimiter.config.policy.HttpMethodMatch;
import com.ratelimiter.config.policy.RateLimitPolicy;
import com.ratelimiter.config.policy.RateLimitPolicyRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final RateLimitPolicyRepository policyRepository;
    private final ClientRepository clientRepository;

    public PlanService(
            PlanRepository planRepository,
            RateLimitPolicyRepository policyRepository,
            ClientRepository clientRepository) {
        this.planRepository = planRepository;
        this.policyRepository = policyRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> list() {
        return planRepository.findAllByOrderByIdAsc().stream().map(PlanService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse get(Long planId) {
        return toResponse(requirePlan(planId));
    }

    @Transactional
    public PlanResponse create(PlanCreateRequest request) {
        if (planRepository.existsByCode(request.code())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE, "Plan code already exists.");
        }
        Plan plan = new Plan();
        plan.setCode(request.code());
        plan.setName(request.name());
        plan.setDescription(request.description());
        planRepository.save(plan);

        RateLimitPolicy policy = new RateLimitPolicy();
        policy.setPlan(plan);
        policy.setPathPattern("/**");
        policy.setHttpMethod(HttpMethodMatch.ANY);
        policy.setCapacity(request.defaultPolicy().capacity());
        policy.setRefillTokens(request.defaultPolicy().refillTokens());
        policy.setRefillPeriodSeconds(request.defaultPolicy().refillPeriodSeconds());
        policy.setEnabled(true);
        policyRepository.save(policy);
        return toResponse(plan);
    }

    @Transactional
    public PlanResponse patch(Long planId, PlanPatchRequest request) {
        Plan plan = requirePlan(planId);
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw ApiException.validation("name", "must not be blank");
            }
            plan.setName(request.name());
        }
        if (request.description() != null) {
            plan.setDescription(request.description());
        }
        return toResponse(planRepository.save(plan));
    }

    @Transactional
    public void delete(Long planId) {
        Plan plan = requirePlan(planId);
        if (clientRepository.existsByPlan_Id(plan.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.PLAN_IN_USE, "Plan still has clients.");
        }
        planRepository.delete(plan);
    }

    private Plan requirePlan(Long planId) {
        return planRepository.findById(planId).orElseThrow(() -> ApiException.notFound("Plan not found."));
    }

    private static PlanResponse toResponse(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }
}
