package com.kyro.platform.discovery.application;

import com.kyro.platform.common.errors.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Keyset position: base64url of {@code <instant>|<uuid>}. Malformed values give 400 MALFORMED_REQUEST. */
record PageCursor(Instant at, UUID id) {

    String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((at + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    static PageCursor decode(String raw) {
        try {
            String s = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            int i = s.indexOf('|');
            return new PageCursor(Instant.parse(s.substring(0, i)), UUID.fromString(s.substring(i + 1)));
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The cursor is not valid.", null);
        }
    }
}
