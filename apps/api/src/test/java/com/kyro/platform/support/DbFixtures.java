package com.kyro.platform.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// FROZEN CONTRACT (test support)
/**
 * Inserts rows straight into the education tables so verification and discovery tests do not depend on API-2.
 * Requests are created in any status; APPROVED ones get {@code approved_at} set and therefore appear in the
 * {@code discovery_public_requests} view (the recipient needs a recipient_profiles row: use
 * {@code TestUsers.createActiveUser(Role.RECIPIENT)}).
 */
@Component
public class DbFixtures {

    private final JdbcTemplate jdbc;

    public DbFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Request with sensible defaults in the given status (DRAFT, SUBMITTED, IN_REVIEW, ..., APPROVED, ...). */
    public UUID insertRequest(UUID recipientUserId, String status) {
        return insertRequest(recipientUserId, status, "Semester tuition fee " + UUID.randomUUID().toString().substring(0, 8),
                "APPROVED".equals(status) ? Instant.now() : null, LocalDate.now().plusDays(45));
    }

    public UUID insertRequest(UUID recipientUserId, String status, String title, Instant approvedAt, LocalDate dueDate) {
        String feeRef = "FEE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return jdbc.queryForObject(
                """
                INSERT INTO funding_requests (recipient_user_id, status, title, story, expense_type, institution_name,
                    institution_city, institution_country, course_name, fee_reference, fee_reference_normalized,
                    amount_minor, currency, due_date, submission_count, submitted_at, approved_at)
                VALUES (?, ?, ?, ?, 'TUITION', 'Fictional Institute of Technology', 'Pune', 'IN', 'B.Tech Computer Science',
                    ?, ?, 4500000, 'INR', ?, ?, ?, ?) RETURNING id
                """,
                UUID.class,
                recipientUserId,
                status,
                title,
                "A fictional story used in tests. ".repeat(4),
                feeRef,
                feeRef.replaceAll("[^A-Z0-9]", ""),
                dueDate == null ? null : java.sql.Date.valueOf(dueDate),
                "DRAFT".equals(status) ? 0 : 1,
                "DRAFT".equals(status) ? null : Timestamp.from(Instant.now()),
                approvedAt == null ? null : Timestamp.from(approvedAt));
    }

    public UUID insertDocument(UUID requestId, UUID uploadedBy, String status, String sha256) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO request_documents (id, request_id, uploaded_by, document_type, storage_key, original_file_name,
                    content_type, size_bytes, sha256, status, uploaded_at)
                VALUES (?, ?, ?, 'TUITION_INVOICE', ?, 'invoice.pdf', 'application/pdf', 1024, ?, ?, ?)
                """,
                id,
                requestId,
                uploadedBy,
                "requests/" + requestId + "/documents/" + id,
                sha256,
                status,
                "UPLOADED".equals(status) ? Timestamp.from(Instant.now()) : null);
        return id;
    }

    public void insertStatusHistory(UUID requestId, String fromStatus, String toStatus, UUID changedBy, String note) {
        jdbc.update(
                "INSERT INTO funding_request_status_history (request_id, from_status, to_status, changed_by, note) VALUES (?, ?, ?, ?, ?)",
                requestId,
                fromStatus,
                toStatus,
                changedBy,
                note);
    }
}
