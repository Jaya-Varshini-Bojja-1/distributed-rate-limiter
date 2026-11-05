package com.ratelimiter.config.apikey;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/clients/{clientId}/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public List<ApiKeyResponse> list(@PathVariable Long clientId) {
        return apiKeyService.list(clientId);
    }

    @PostMapping
    public ResponseEntity<ApiKeyCreatedResponse> create(
            @PathVariable Long clientId,
            @Valid @RequestBody ApiKeyCreateRequest request) {
        ApiKeyCreatedResponse body = apiKeyService.create(clientId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @PostMapping("/{keyId}/revoke")
    public ApiKeyResponse revoke(@PathVariable Long clientId, @PathVariable Long keyId) {
        return apiKeyService.revoke(clientId, keyId);
    }
}
