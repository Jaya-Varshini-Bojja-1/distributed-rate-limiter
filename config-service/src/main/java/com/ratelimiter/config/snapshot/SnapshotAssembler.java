package com.ratelimiter.config.snapshot;

import java.util.ArrayList;
import java.util.List;

import com.ratelimiter.snapshot.ApiKeySnapshot;
import com.ratelimiter.snapshot.ConfigurationSnapshot;
import com.ratelimiter.snapshot.PlanSnapshot;
import com.ratelimiter.snapshot.PolicySnapshot;

public final class SnapshotAssembler {

    private SnapshotAssembler() {
    }

    public static ConfigurationSnapshot assemble(List<EnabledPolicyRow> policies, List<ActiveKeyRow> keys) {
        List<PlanSnapshot> plans = new ArrayList<>();
        List<PolicySnapshot> policiesForPlan = new ArrayList<>();
        long openPlanId = 0;
        String openCode = null;
        boolean open = false;
        for (EnabledPolicyRow row : policies) {
            if (!open || openPlanId != row.planId()) {
                if (open) {
                    plans.add(new PlanSnapshot(openPlanId, openCode, List.copyOf(policiesForPlan)));
                }
                open = true;
                openPlanId = row.planId();
                openCode = row.planCode();
                policiesForPlan = new ArrayList<>();
            }
            policiesForPlan.add(new PolicySnapshot(
                    row.policyId(),
                    row.pathPattern(),
                    row.httpMethod(),
                    row.capacity(),
                    row.refillTokens(),
                    row.refillPeriodSeconds()));
        }
        if (open) {
            plans.add(new PlanSnapshot(openPlanId, openCode, List.copyOf(policiesForPlan)));
        }
        List<ApiKeySnapshot> apiKeys = keys.stream()
                .map(key -> new ApiKeySnapshot(key.keyHash(), key.clientId(), key.planId(), key.clientStatus()))
                .toList();
        return new ConfigurationSnapshot(List.copyOf(plans), apiKeys);
    }
}
