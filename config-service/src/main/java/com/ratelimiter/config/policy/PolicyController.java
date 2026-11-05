package com.ratelimiter.config.policy;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/plans/{planId}/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public List<PolicyResponse> list(@PathVariable Long planId) {
        return policyService.list(planId);
    }

    @PostMapping
    public ResponseEntity<PolicyResponse> create(
            @PathVariable Long planId,
            @Valid @RequestBody PolicyCreateRequest request) {
        PolicyResponse body = policyService.create(planId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{policyId}")
                .buildAndExpand(body.id())
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{policyId}")
    public PolicyResponse get(@PathVariable Long planId, @PathVariable Long policyId) {
        return policyService.get(planId, policyId);
    }

    @PutMapping("/{policyId}")
    public PolicyResponse update(
            @PathVariable Long planId,
            @PathVariable Long policyId,
            @Valid @RequestBody PolicyUpdateRequest request) {
        return policyService.update(planId, policyId, request);
    }

    @DeleteMapping("/{policyId}")
    public ResponseEntity<Void> delete(@PathVariable Long planId, @PathVariable Long policyId) {
        policyService.delete(planId, policyId);
        return ResponseEntity.noContent().build();
    }
}
