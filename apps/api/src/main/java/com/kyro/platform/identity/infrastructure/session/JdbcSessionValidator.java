package com.kyro.platform.identity.infrastructure.session;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.security.SessionValidator;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Session revalidation against {@code users.status} and {@code users.credentials_version}. */
@Component
class JdbcSessionValidator implements SessionValidator {

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    JdbcSessionValidator(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    public boolean stillValid(UUID userId, long credentialsVersionAtLogin) {
        return jdbc.query(
                        "SELECT status, credentials_version FROM users WHERE id = ?",
                        (rs, i) -> !"SUSPENDED".equals(rs.getString("status"))
                                && rs.getLong("credentials_version") == credentialsVersionAtLogin,
                        userId)
                .stream()
                .findFirst()
                .orElse(false);
    }

    @Override
    public void sessionRevoked(UUID userId) {
        audit.record(new AuditEntry(
                AuditActions.SESSION_REVOKED, "USER", userId, Map.of("reason", "STATUS_OR_CREDENTIALS_CHANGED")));
    }
}
