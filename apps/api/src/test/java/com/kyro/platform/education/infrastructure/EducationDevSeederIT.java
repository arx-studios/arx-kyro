package com.kyro.platform.education.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kyro.platform.common.config.KyroProperties;
import com.kyro.platform.support.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Runs inside a rolled-back transaction so the shared test database stays free of demo data. */
@Transactional
class EducationDevSeederIT extends IntegrationTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired KyroProperties props;

    @Test
    void seedsThreeApprovedRequestsWithFullHistoryAndIsIdempotent() throws Exception {
        UUID recipient = jdbc.queryForObject(
                """
                INSERT INTO users (email, email_normalized, password_hash, display_name, country_code, status, email_verified_at)
                VALUES ('recipient.demo@kyro.local', 'recipient.demo@kyro.local', 'x', 'Demo Recipient', 'IN', 'ACTIVE', now())
                RETURNING id
                """,
                UUID.class);
        jdbc.update("INSERT INTO recipient_profiles (user_id, public_display_name) VALUES (?, 'Demo Recipient')", recipient);

        KyroProperties enabled = new KyroProperties(
                props.webBaseUrl(), props.mailFrom(), props.policy(), props.auth(), props.storage(), props.documents(),
                props.discovery(), new KyroProperties.Seed(true, "unused"));
        EducationDevSeeder seeder = new EducationDevSeeder(jdbc, enabled);
        seeder.run(new DefaultApplicationArguments());
        seeder.run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery_public_requests WHERE recipient_user_id = ?", Integer.class, recipient))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM funding_request_status_history h JOIN funding_requests r ON r.id = h.request_id
                        WHERE r.recipient_user_id = ?
                        """,
                        Integer.class, recipient))
                .isEqualTo(12);
    }
}
