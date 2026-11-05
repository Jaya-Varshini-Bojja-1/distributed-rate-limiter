package com.ratelimiter.config.snapshot;

import com.ratelimiter.config.error.ApiException;
import com.ratelimiter.config.error.ErrorCodes;
import com.ratelimiter.config.web.RequestIds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SnapshotController {

    private static final Logger log = LoggerFactory.getLogger(SnapshotController.class);

    private final SnapshotService snapshotService;
    private final SnapshotJson snapshotJson;

    public SnapshotController(SnapshotService snapshotService, SnapshotJson snapshotJson) {
        this.snapshotService = snapshotService;
        this.snapshotJson = snapshotJson;
    }

    @GetMapping(path = "/internal/v1/snapshot", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> snapshot(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        byte[] body;
        try {
            body = snapshotJson.write(snapshotService.load());
        } catch (DataAccessResourceFailureException | CannotCreateTransactionException exception) {
            log.error("Snapshot storage is unavailable requestId={}", RequestIds.current(), exception);
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCodes.INTERNAL_ERROR,
                    "Configuration storage is unavailable.");
        }
        String etag = Etags.strong(body);
        if (Etags.noneMatch(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).eTag(etag).body(body);
    }
}
