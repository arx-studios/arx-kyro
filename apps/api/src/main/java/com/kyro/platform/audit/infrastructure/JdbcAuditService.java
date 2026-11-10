package com.kyro.platform.audit.infrastructure;

import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.observability.CorrelationContext;
import com.kyro.platform.common.security.CurrentActor;
import com.kyro.platform.common.security.KyroPrincipal;
import com.kyro.platform.common.security.Role;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcAuditService implements AuditService {

    private final JdbcTemplate jdbc;
    private final CurrentActor actor;
    private final JsonMapper mapper;

    JdbcAuditService(JdbcTemplate jdbc, CurrentActor actor, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.actor = actor;
        this.mapper = mapper;
    }

    @Override
    public void record(AuditEntry entry) {
        var principal = actor.current();
        String roles = principal
                .map(KyroPrincipal::roles)
                .map(rs -> rs.stream().sorted(Comparator.naturalOrder()).map(Role::name).collect(Collectors.joining(",")))
                .orElse(null);
        jdbc.update(
                """
                INSERT INTO audit_events (actor_user_id, actor_roles, action, entity_type, entity_id,
                                          correlation_id, ip_address, details)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                principal.map(KyroPrincipal::userId).orElse(null),
                roles,
                entry.action(),
                entry.entityType(),
                entry.entityId(),
                CorrelationContext.current(),
                clientIp(),
                mapper.writeValueAsString(entry.details()));
    }

    private static String clientIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            String ip = sra.getRequest().getRemoteAddr();
            return ip != null && ip.length() > 45 ? ip.substring(0, 45) : ip;
        }
        return null;
    }
}
