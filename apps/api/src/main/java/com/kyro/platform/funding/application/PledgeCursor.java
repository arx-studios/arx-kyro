package com.kyro.platform.funding.application;

import com.kyro.platform.common.errors.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Keyset position (created_at, id), base64url of {@code <instant>|<uuid>}. Malformed values give 400. */
record PledgeCursor(Instant at, UUID id) {

    String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((at + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    static PledgeCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int bar = raw.indexOf('|');
            return new PledgeCursor(Instant.parse(raw.substring(0, bar)), UUID.fromString(raw.substring(bar + 1)));
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The cursor is not valid.", null);
        }
    }
}
