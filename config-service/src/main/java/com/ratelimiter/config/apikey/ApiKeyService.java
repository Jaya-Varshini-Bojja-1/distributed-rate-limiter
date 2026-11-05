package com.ratelimiter.config.apikey;

import java.time.Instant;
import java.util.List;

import com.ratelimiter.config.apikey.ApiKeyGenerator.GeneratedKey;
import com.ratelimiter.config.client.Client;
import com.ratelimiter.config.client.ClientRepository;
import com.ratelimiter.config.client.ClientStatus;
import com.ratelimiter.config.error.ApiException;
import com.ratelimiter.config.error.ErrorCodes;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyService {

    static final String RAW_KEY_WARNING = "Store this key securely. It will not be shown again.";

    private final ClientRepository clientRepository;
    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyService(ClientRepository clientRepository, ApiKeyRepository apiKeyRepository) {
        this.clientRepository = clientRepository;
        this.apiKeyRepository = apiKeyRepository;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(Long clientId) {
        requireClient(clientId);
        return apiKeyRepository.findByClient_IdOrderByIdAsc(clientId).stream()
                .map(ApiKeyService::toResponse)
                .toList();
    }

    @Transactional
    public ApiKeyCreatedResponse create(Long clientId, ApiKeyCreateRequest request) {
        Client client = requireClient(clientId);
        if (client.getStatus() == ClientStatus.SUSPENDED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCodes.CLIENT_SUSPENDED,
                    "Cannot create an API key for a suspended client.");
        }
        GeneratedKey generated = ApiKeyGenerator.generate();
        ApiKey apiKey = new ApiKey();
        apiKey.setClient(client);
        apiKey.setKeyHash(generated.hash());
        apiKey.setKeyPrefix(generated.prefix());
        apiKey.setLabel(blankToNull(request.label()));
        ApiKey saved = apiKeyRepository.save(apiKey);
        return new ApiKeyCreatedResponse(
                saved.getId(),
                saved.getLabel(),
                saved.getKeyPrefix(),
                generated.rawKey(),
                saved.getCreatedAt(),
                RAW_KEY_WARNING);
    }

    @Transactional
    public ApiKeyResponse revoke(Long clientId, Long keyId) {
        requireClient(clientId);
        ApiKey apiKey = apiKeyRepository.findByIdAndClient_Id(keyId, clientId)
                .orElseThrow(() -> ApiException.notFound("API key not found."));
        if (apiKey.getRevokedAt() == null) {
            apiKey.setRevokedAt(Instant.now());
            apiKeyRepository.save(apiKey);
        }
        return toResponse(apiKey);
    }

    private static String blankToNull(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        return label;
    }

    private Client requireClient(Long clientId) {
        return clientRepository.findById(clientId).orElseThrow(() -> ApiException.notFound("Client not found."));
    }

    private static ApiKeyResponse toResponse(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getLabel(),
                apiKey.getKeyPrefix(),
                apiKey.getCreatedAt(),
                apiKey.getRevokedAt());
    }
}
