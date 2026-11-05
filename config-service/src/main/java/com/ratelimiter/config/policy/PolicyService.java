package com.ratelimiter.config.policy;

import java.util.List;

import com.ratelimiter.config.error.ApiException;
import com.ratelimiter.config.error.ErrorCodes;
import com.ratelimiter.config.plan.Plan;
import com.ratelimiter.config.plan.PlanRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyService {

    private final PlanRepository planRepository;
    private final RateLimitPolicyRepository policyRepository;

    public PolicyService(PlanRepository planRepository, RateLimitPolicyRepository policyRepository) {
        this.planRepository = planRepository;
        this.policyRepository = policyRepository;
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> list(Long planId) {
        requirePlan(planId);
        return policyRepository.findByPlan_IdOrderByIdAsc(planId).stream().map(PolicyService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PolicyResponse get(Long planId, Long policyId) {
        requirePlan(planId);
        return toResponse(requirePolicy(planId, policyId));
    }

    @Transactional
    public PolicyResponse create(Long planId, PolicyCreateRequest request) {
        Plan plan = requirePlan(planId);
        validatePath(request.pathPattern());
        rejectDuplicate(planId, request.pathPattern(), request.httpMethod(), null);
        RateLimitPolicy policy = new RateLimitPolicy();
        policy.setPlan(plan);
        policy.setPathPattern(request.pathPattern());
        policy.setHttpMethod(request.httpMethod());
        policy.setCapacity(request.capacity());
        policy.setRefillTokens(request.refillTokens());
        policy.setRefillPeriodSeconds(request.refillPeriodSeconds());
        policy.setEnabled(request.enabled() == null || request.enabled());
        return toResponse(policyRepository.save(policy));
    }

    @Transactional
    public PolicyResponse update(Long planId, Long policyId, PolicyUpdateRequest request) {
        requirePlan(planId);
        validatePath(request.pathPattern());
        RateLimitPolicy policy = requirePolicy(planId, policyId);
        if (DefaultPolicyRules.isProtectedChange(
                policy.getPathPattern(),
                policy.getHttpMethod(),
                request.pathPattern(),
                request.httpMethod(),
                request.enabled())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCodes.DEFAULT_POLICY_PROTECTED,
                    "The default policy cannot be disabled or given a different path or method.");
        }
        rejectDuplicate(planId, request.pathPattern(), request.httpMethod(), policy.getId());
        // Compare before save. Writing onto the loaded row would use the database version
        // and silently overwrite another admin's change.
        if (!request.version().equals(policy.getVersion())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCodes.POLICY_VERSION_CONFLICT,
                    "This policy was changed by someone else. Reload it.");
        }
        policy.setPathPattern(request.pathPattern());
        policy.setHttpMethod(request.httpMethod());
        policy.setCapacity(request.capacity());
        policy.setRefillTokens(request.refillTokens());
        policy.setRefillPeriodSeconds(request.refillPeriodSeconds());
        policy.setEnabled(request.enabled());
        return toResponse(policyRepository.saveAndFlush(policy));
    }

    @Transactional
    public void delete(Long planId, Long policyId) {
        requirePlan(planId);
        RateLimitPolicy policy = requirePolicy(planId, policyId);
        if (DefaultPolicyRules.isDefault(policy.getPathPattern(), policy.getHttpMethod())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCodes.DEFAULT_POLICY_PROTECTED,
                    "The default policy cannot be deleted.");
        }
        policyRepository.delete(policy);
    }

    private void validatePath(String pathPattern) {
        PathPatternValidator.error(pathPattern).ifPresent(message -> {
            throw ApiException.validation("pathPattern", message);
        });
    }

    private void rejectDuplicate(Long planId, String pathPattern, HttpMethodMatch method, Long currentId) {
        policyRepository.findByPlan_IdAndPathPatternAndHttpMethod(planId, pathPattern, method)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            ErrorCodes.DUPLICATE,
                            "A policy for this path and method already exists.");
                });
    }

    private Plan requirePlan(Long planId) {
        return planRepository.findById(planId).orElseThrow(() -> ApiException.notFound("Plan not found."));
    }

    private RateLimitPolicy requirePolicy(Long planId, Long policyId) {
        return policyRepository.findByIdAndPlan_Id(policyId, planId)
                .orElseThrow(() -> ApiException.notFound("Policy not found."));
    }

    private static PolicyResponse toResponse(RateLimitPolicy policy) {
        return new PolicyResponse(
                policy.getId(),
                policy.getPlan().getId(),
                policy.getPathPattern(),
                policy.getHttpMethod(),
                policy.getCapacity(),
                policy.getRefillTokens(),
                policy.getRefillPeriodSeconds(),
                policy.isEnabled(),
                DefaultPolicyRules.isDefault(policy.getPathPattern(), policy.getHttpMethod()),
                policy.getVersion(),
                policy.getCreatedAt(),
                policy.getUpdatedAt());
    }
}
