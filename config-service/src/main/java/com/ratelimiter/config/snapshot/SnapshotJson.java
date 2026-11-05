package com.ratelimiter.config.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ratelimiter.snapshot.ConfigurationSnapshot;

import org.springframework.stereotype.Component;

@Component
public class SnapshotJson {

    private final JsonMapper mapper = JsonMapper.builder().build();

    public byte[] write(ConfigurationSnapshot snapshot) {
        try {
            return mapper.writeValueAsBytes(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Snapshot JSON serialization failed", exception);
        }
    }
}
