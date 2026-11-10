package com.kyro.platform.identity.infrastructure;

import com.kyro.platform.identity.application.ReviewRecipientView;
import com.kyro.platform.identity.application.UserDirectory;
import com.kyro.platform.identity.application.UserSummaryView;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserDirectory implements UserDirectory {

    private final JdbcTemplate jdbc;

    JdbcUserDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, UserSummaryView> findSummaries(Collection<UUID> ids) {
        Map<UUID, UserSummaryView> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        UUID[] arr = ids.stream().distinct().toArray(UUID[]::new);
        jdbc.query(
                "SELECT id, display_name FROM users WHERE id = ANY (?)",
                rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    result.put(id, new UserSummaryView(id, rs.getString("display_name")));
                },
                (Object) arr);
        return result;
    }

    @Override
    public Optional<ReviewRecipientView> findRecipient(UUID userId) {
        return jdbc.query(
                "SELECT id, display_name, email, email_verified_at, created_at FROM users WHERE id = ?",
                (rs, i) -> new ReviewRecipientView(
                        rs.getObject("id", UUID.class),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getTimestamp("email_verified_at") != null,
                        rs.getTimestamp("created_at").toInstant()),
                userId)
                .stream()
                .findFirst();
    }
}
