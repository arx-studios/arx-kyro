package com.kyro.platform.education.infrastructure;

import com.kyro.platform.common.config.KyroProperties;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Profile {@code local} only: three fictional APPROVED requests for recipient.demo@kyro.local so the donor feed has
 * content. Runs after IdentityDevSeeder (order 0). Idempotent: skipped when that recipient already has requests.
 * No documents are seeded (there are no real objects to point at).
 */
@Component
@Profile("local")
@Order(10)
class EducationDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EducationDevSeeder.class);

    private record Demo(String title, String story, String expenseType, String institution, String city, String course,
                        String feeRef, long amountMinor, int dueInDays) {}

    private static final List<Demo> DEMOS = List.of(
            new Demo(
                    "Semester 3 tuition fee",
                    "I am a second-year computer science student and my family cannot cover this semester's tuition. "
                            + "The fee covers lectures and lab access. This is a fictional demo request.",
                    "TUITION", "Fictional Institute of Technology", "Pune", "B.Tech Computer Science", "DEMO-INV-2026-0001",
                    4_500_000L, 45),
            new Demo(
                    "Board examination fee",
                    "I need to pay the final board examination registration fee before the deadline. "
                            + "A fictional demo request used to populate the donor feed.",
                    "EXAMINATION_FEE", "Fictional Public College", "Jaipur", "B.Com Honours", "DEMO-EXAM-2026-0002",
                    850_000L, 20),
            new Demo(
                    "Engineering course materials",
                    "I need textbooks and a lab kit for my mechanical drawing and workshop practice courses. "
                            + "This is a fictional demo request with made-up details.",
                    "COURSE_MATERIALS", "Fictional Polytechnic", "Nagpur", "Diploma in Mechanical Engineering",
                    "DEMO-MAT-2026-0003", 1_250_000L, 30));

    private final JdbcTemplate jdbc;
    private final KyroProperties props;

    EducationDevSeeder(JdbcTemplate jdbc, KyroProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled()) {
            return;
        }
        UUID recipient = idOf("recipient.demo@kyro.local");
        if (recipient == null) {
            log.warn("Demo recipient not found; skipping education seed");
            return;
        }
        Integer existing = jdbc.queryForObject(
                "SELECT count(*) FROM funding_requests WHERE recipient_user_id = ?", Integer.class, recipient);
        if (existing != null && existing > 0) {
            return;
        }
        UUID verifier = idOf("verifier@kyro.local");
        Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
        for (Demo d : DEMOS) {
            UUID id = jdbc.queryForObject(
                    """
                    INSERT INTO funding_requests (recipient_user_id, status, title, story, expense_type, institution_name,
                        institution_city, institution_country, course_name, fee_reference, fee_reference_normalized,
                        amount_minor, currency, due_date, submission_count, submitted_at, approved_at, created_at)
                    VALUES (?, 'APPROVED', ?, ?, ?, ?, ?, 'IN', ?, ?, ?, ?, 'INR', ?, 1, ?, ?, ?) RETURNING id
                    """,
                    UUID.class,
                    recipient,
                    d.title(),
                    d.story(),
                    d.expenseType(),
                    d.institution(),
                    d.city(),
                    d.course(),
                    d.feeRef(),
                    d.feeRef().replaceAll("[^A-Z0-9]", ""),
                    d.amountMinor(),
                    java.sql.Date.valueOf(LocalDate.now().plusDays(d.dueInDays())),
                    Timestamp.from(base.plus(1, ChronoUnit.DAYS)),
                    Timestamp.from(base.plus(3, ChronoUnit.DAYS)),
                    Timestamp.from(base));
            history(id, null, "DRAFT", recipient, null, base);
            history(id, "DRAFT", "SUBMITTED", recipient, null, base.plus(1, ChronoUnit.DAYS));
            history(id, "SUBMITTED", "IN_REVIEW", verifier, null, base.plus(2, ChronoUnit.DAYS));
            history(id, "IN_REVIEW", "APPROVED", verifier, "Documents verified (fictional demo data).", base.plus(3, ChronoUnit.DAYS));
            base = base.plus(1, ChronoUnit.HOURS);
            log.info("Seeded demo request '{}'", d.title());
        }
    }

    private UUID idOf(String email) {
        List<UUID> ids = jdbc.queryForList("SELECT id FROM users WHERE email_normalized = ?", UUID.class, email);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void history(UUID requestId, String from, String to, UUID by, String note, Instant at) {
        jdbc.update(
                "INSERT INTO funding_request_status_history (request_id, from_status, to_status, changed_by, note, changed_at) VALUES (?, ?, ?, ?, ?, ?)",
                requestId, from, to, by, note, Timestamp.from(at));
    }
}
