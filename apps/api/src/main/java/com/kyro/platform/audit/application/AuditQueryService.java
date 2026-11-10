package com.kyro.platform.audit.application;

import com.kyro.platform.common.errors.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {

    public record Page(List<AuditEventRecord> items, String nextCursor) {}

    private final AuditEventReader reader;

    public AuditQueryService(AuditEventReader reader) {
        this.reader = reader;
    }

    public Page list(String entityType, UUID entityId, UUID actorUserId, String cursor, int limit) {
        Instant at = null;
        UUID id = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int sep = raw.indexOf('|');
                at = Instant.parse(raw.substring(0, sep));
                id = UUID.fromString(raw.substring(sep + 1));
            } catch (RuntimeException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Invalid cursor.", null);
            }
        }
        List<AuditEventRecord> rows = reader.find(entityType, entityId, actorUserId, at, id, limit + 1);
        String next = null;
        if (rows.size() > limit) {
            rows = rows.subList(0, limit);
            AuditEventRecord last = rows.get(rows.size() - 1);
            next = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString((last.occurredAt() + "|" + last.id()).getBytes(StandardCharsets.UTF_8));
        }
        return new Page(rows, next);
    }
}
