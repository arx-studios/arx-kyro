package com.kyro.platform.audit.api;

import com.kyro.platform.audit.application.AuditEventRecord;
import com.kyro.platform.audit.application.AuditQueryService;
import com.kyro.platform.contract.api.AuditApi;
import com.kyro.platform.contract.model.AuditEvent;
import com.kyro.platform.contract.model.AuditEventPage;
import com.kyro.platform.contract.model.UserSummary;
import com.kyro.platform.identity.application.UserDirectory;
import com.kyro.platform.identity.application.UserSummaryView;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuditController implements AuditApi {

    private final AuditQueryService queries;
    private final UserDirectory users;

    AuditController(AuditQueryService queries, UserDirectory users) {
        this.queries = queries;
        this.users = users;
    }

    @Override
    public ResponseEntity<AuditEventPage> listAuditEvents(
            String entityType, UUID entityId, UUID actorUserId, String cursor, Integer limit) {
        var page = queries.list(entityType, entityId, actorUserId, cursor, limit == null ? 20 : limit);
        Map<UUID, UserSummaryView> actors = users.findSummaries(
                page.items().stream().map(AuditEventRecord::actorUserId).filter(Objects::nonNull).toList());
        List<AuditEvent> items = page.items().stream()
                .map(r -> {
                    AuditEvent e = new AuditEvent(
                            r.id(), r.occurredAt().atOffset(ZoneOffset.UTC), r.action(), r.entityType(), r.details());
                    UserSummaryView actor = r.actorUserId() == null ? null : actors.get(r.actorUserId());
                    if (actor != null) {
                        e.setActor(new UserSummary(actor.id(), actor.displayName()));
                    }
                    e.setEntityId(r.entityId());
                    e.setCorrelationId(r.correlationId());
                    return e;
                })
                .toList();
        return ResponseEntity.ok(new AuditEventPage(items, page.nextCursor()));
    }
}
