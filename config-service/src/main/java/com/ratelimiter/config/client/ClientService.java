package com.ratelimiter.config.client;

import java.util.ArrayList;
import java.util.List;

import com.ratelimiter.config.error.ApiException;
import com.ratelimiter.config.plan.Plan;
import com.ratelimiter.config.plan.PlanRepository;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService {

    static final int MAX_PAGE_SIZE = 100;

    private final ClientRepository clientRepository;
    private final PlanRepository planRepository;

    public ClientService(ClientRepository clientRepository, PlanRepository planRepository) {
        this.clientRepository = clientRepository;
        this.planRepository = planRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> list(Long planId, String status, int page, int size) {
        if (page < 0) {
            throw ApiException.validation("page", "must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.validation("size", "must be between 1 and 100");
        }
        ClientStatus parsedStatus = parseStatus(status);
        Page<Client> result = clientRepository.findAll(
                matches(planId, parsedStatus),
                PageRequest.of(page, size, Sort.by("id").ascending()));
        List<ClientResponse> content = result.getContent().stream().map(ClientService::toResponse).toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ClientResponse get(Long clientId) {
        return toResponse(requireClient(clientId));
    }

    @Transactional
    public ClientResponse create(ClientCreateRequest request) {
        Plan plan = requirePlan(request.planId());
        Client client = new Client();
        client.setPlan(plan);
        client.setName(request.name());
        client.setStatus(ClientStatus.ACTIVE);
        return toResponse(clientRepository.save(client));
    }

    @Transactional
    public ClientResponse patch(Long clientId, ClientPatchRequest request) {
        Client client = requireClient(clientId);
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw ApiException.validation("name", "must not be blank");
            }
            client.setName(request.name());
        }
        if (request.planId() != null) {
            client.setPlan(requirePlan(request.planId()));
        }
        if (request.status() != null) {
            client.setStatus(request.status());
        }
        return toResponse(clientRepository.save(client));
    }

    private ClientStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ClientStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw ApiException.validation("status", "must be ACTIVE or SUSPENDED");
        }
    }

    private static Specification<Client> matches(Long planId, ClientStatus status) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (planId != null) {
                predicates.add(builder.equal(root.get("plan").get("id"), planId));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (predicates.isEmpty()) {
                return builder.conjunction();
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Client requireClient(Long clientId) {
        return clientRepository.findById(clientId).orElseThrow(() -> ApiException.notFound("Client not found."));
    }

    private Plan requirePlan(Long planId) {
        return planRepository.findById(planId).orElseThrow(() -> ApiException.notFound("Plan not found."));
    }

    private static ClientResponse toResponse(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getPlan().getId(),
                client.getStatus(),
                client.getCreatedAt(),
                client.getUpdatedAt());
    }
}
