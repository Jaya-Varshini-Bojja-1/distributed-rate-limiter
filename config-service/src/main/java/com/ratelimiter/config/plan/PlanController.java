package com.ratelimiter.config.plan;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public List<PlanResponse> list() {
        return planService.list();
    }

    @PostMapping
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody PlanCreateRequest request) {
        PlanResponse body = planService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(body.id())
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{planId}")
    public PlanResponse get(@PathVariable Long planId) {
        return planService.get(planId);
    }

    @PatchMapping("/{planId}")
    public PlanResponse patch(@PathVariable Long planId, @Valid @RequestBody PlanPatchRequest request) {
        return planService.patch(planId, request);
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<Void> delete(@PathVariable Long planId) {
        planService.delete(planId);
        return ResponseEntity.noContent().build();
    }
}
