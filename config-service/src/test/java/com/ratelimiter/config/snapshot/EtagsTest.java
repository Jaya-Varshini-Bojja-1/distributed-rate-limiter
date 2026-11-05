package com.ratelimiter.config.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class EtagsTest {

    @Test
    void strongEtagIsQuotedLowercaseSha256OfTheBytes() throws Exception {
        byte[] body = "{\"plans\":[],\"apiKeys\":[]}".getBytes(StandardCharsets.UTF_8);
        String etag = Etags.strong(body);
        String again = Etags.strong(body);

        assertEquals(etag, again);
        assertTrue(etag.startsWith("\""));
        assertTrue(etag.endsWith("\""));
        assertFalse(etag.startsWith("W/"));
        String hex = etag.substring(1, etag.length() - 1);
        assertEquals(64, hex.length());
        assertEquals(hex, hex.toLowerCase());
        assertEquals(hex, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)));
    }

    @Test
    void aDifferentBodyHasADifferentEtag() {
        byte[] left = "{\"plans\":[]}".getBytes(StandardCharsets.UTF_8);
        byte[] right = "{\"plans\":[1]}".getBytes(StandardCharsets.UTF_8);
        assertFalse(Etags.strong(left).equals(Etags.strong(right)));
    }

    @Test
    void ifNoneMatchUsesWeakComparison() {
        String etag = Etags.strong("snapshot".getBytes(StandardCharsets.UTF_8));
        String other = Etags.strong("other".getBytes(StandardCharsets.UTF_8));

        assertFalse(Etags.noneMatch(null, etag));
        assertFalse(Etags.noneMatch("   ", etag));
        assertFalse(Etags.noneMatch(other, etag));
        assertFalse(Etags.noneMatch(etag.substring(1, etag.length() - 1), etag));
        assertTrue(Etags.noneMatch(etag, etag));
        assertTrue(Etags.noneMatch("W/" + etag, etag));
        assertTrue(Etags.noneMatch(other + ", " + etag, etag));
        assertTrue(Etags.noneMatch("*", etag));
    }
}
