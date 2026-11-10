package com.kyro.platform.audit.infrastructure;

import com.kyro.platform.audit.application.AuditEventReader;
import com.kyro.platform.audit.application.AuditEventRecord;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcAuditEventReader implements AuditEventReader {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;

    JdbcAuditEventReader(JdbcTemplate jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public List<AuditEventRecord> find(
            String entityType, UUID entityId, UUID actorUserId, Instant afterOccurredAt, UUID afterId, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, occurred_at, actor_user_id, action, entity_type, entity_id, correlation_id, details::text AS details"
                        + " FROM audit_events WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (entityType != null) {
            sql.append(" AND entity_type = ?");
            args.add(entityType);
        }
        if (entityId != null) {
            sql.append(" AND entity_id = ?");
            args.add(entityId);
        }
        if (actorUserId != null) {
            sql.append(" AND actor_user_id = ?");
            args.add(actorUserId);
        }
        if (afterOccurredAt != null && afterId != null) {
            sql.append(" AND (occurred_at, id) < (?, ?)");
            args.add(Timestamp.from(afterOccurredAt));
            args.add(afterId);
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, i) -> new AuditEventRecord(
                rs.getObject("id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("actor_user_id", UUID.class),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                rs.getString("correlation_id"),
                mapper.readValue(rs.getString("details"), MAP)), args.toArray());
    }
}
