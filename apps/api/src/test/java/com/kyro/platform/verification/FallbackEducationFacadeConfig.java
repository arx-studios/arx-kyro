package com.kyro.platform.verification;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.config.KyroProperties;
import com.kyro.platform.common.errors.ConflictException;
import com.kyro.platform.common.errors.NotFoundException;
import com.kyro.platform.common.storage.ObjectStorage;
import com.kyro.platform.common.storage.PresignedUrl;
import com.kyro.platform.education.application.port.DuplicateIndicatorView;
import com.kyro.platform.education.application.port.EducationReviewFacade;
import com.kyro.platform.education.application.port.ReviewDocument;
import com.kyro.platform.education.application.port.ReviewSnapshot;
import com.kyro.platform.education.application.port.ReviewStatusHistoryEntry;
import com.kyro.platform.education.application.port.ReviewSummary;
import com.kyro.platform.education.application.port.VerificationOutcome;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test-only stand-in for the education module's {@link EducationReviewFacade} until API-2 is merged: a thin JDBC
 * implementation over the V2 tables. It backs off ({@code @ConditionalOnMissingBean}) as soon as the real facade bean
 * exists, so after API-2 merges the same integration tests run against the real implementation.
 */
@Configuration
class FallbackEducationFacadeConfig {

    @Bean
    @ConditionalOnMissingBean(EducationReviewFacade.class)
    EducationReviewFacade fallbackEducationReviewFacade(
            JdbcTemplate jdbc, AuditService audit, ObjectStorage storage, KyroProperties props) {
        return new JdbcFacade(jdbc, audit, storage, props);
    }

    static final class JdbcFacade implements EducationReviewFacade {
        private final JdbcTemplate jdbc;
        private final AuditService audit;
        private final ObjectStorage storage;
        private final KyroProperties props;

        JdbcFacade(JdbcTemplate jdbc, AuditService audit, ObjectStorage storage, KyroProperties props) {
            this.jdbc = jdbc;
            this.audit = audit;
            this.storage = storage;
            this.props = props;
        }

        @Override
        public Map<UUID, ReviewSummary> getReviewSummaries(Collection<UUID> requestIds) {
            return requestIds.stream()
                    .map(id -> jdbc.query(
                            """
                            SELECT r.id, r.recipient_user_id, r.title, r.amount_minor, r.currency, p.public_display_name
                              FROM funding_requests r JOIN recipient_profiles p ON p.user_id = r.recipient_user_id
                             WHERE r.id = ?
                            """,
                            (rs, i) -> new ReviewSummary(
                                    rs.getObject("id", UUID.class),
                                    rs.getObject("recipient_user_id", UUID.class),
                                    rs.getString("title"),
                                    rs.getString("public_display_name"),
                                    rs.getLong("amount_minor"),
                                    rs.getString("currency")),
                            id))
                    .flatMap(List::stream)
                    .collect(Collectors.toMap(ReviewSummary::requestId, s -> s));
        }

        @Override
        public ReviewSnapshot getReviewSnapshot(UUID requestId) {
            List<ReviewDocument> docs = jdbc.query(
                    "SELECT * FROM request_documents WHERE request_id = ? AND status <> 'DELETED' ORDER BY created_at",
                    (rs, i) -> new ReviewDocument(
                            rs.getObject("id", UUID.class),
                            rs.getString("document_type"),
                            rs.getString("original_file_name"),
                            rs.getString("content_type"),
                            rs.getLong("size_bytes"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("uploaded_at") == null ? null : rs.getTimestamp("uploaded_at").toInstant()),
                    requestId);
            List<ReviewStatusHistoryEntry> history = jdbc.query(
                    "SELECT * FROM funding_request_status_history WHERE request_id = ? ORDER BY changed_at, id",
                    (rs, i) -> new ReviewStatusHistoryEntry(
                            rs.getString("from_status"),
                            rs.getString("to_status"),
                            rs.getTimestamp("changed_at").toInstant(),
                            rs.getString("note")),
                    requestId);
            List<ReviewSnapshot> found = jdbc.query(
                    """
                    SELECT r.*, p.public_display_name
                      FROM funding_requests r JOIN recipient_profiles p ON p.user_id = r.recipient_user_id
                     WHERE r.id = ?
                    """,
                    (rs, i) -> new ReviewSnapshot(
                            rs.getObject("id", UUID.class),
                            rs.getObject("recipient_user_id", UUID.class),
                            rs.getString("status"),
                            rs.getString("title"),
                            rs.getString("story"),
                            rs.getString("expense_type"),
                            rs.getString("institution_name"),
                            rs.getString("institution_city"),
                            rs.getString("institution_country"),
                            rs.getString("course_name"),
                            rs.getString("fee_reference"),
                            rs.getLong("amount_minor"),
                            rs.getString("currency"),
                            rs.getDate("due_date") == null ? null : rs.getDate("due_date").toLocalDate(),
                            rs.getInt("submission_count"),
                            rs.getString("public_display_name"),
                            false,
                            null,
                            rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant(),
                            rs.getTimestamp("approved_at") == null ? null : rs.getTimestamp("approved_at").toInstant(),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant(),
                            rs.getLong("version"),
                            docs,
                            history,
                            List.<DuplicateIndicatorView>of()),
                    requestId);
            if (found.isEmpty()) {
                throw new NotFoundException();
            }
            return found.get(0);
        }

        @Override
        public void markInReview(UUID requestId, UUID verifierId) {
            transition(requestId, "SUBMITTED", "IN_REVIEW", verifierId, null);
        }

        @Override
        public void applyDecision(UUID requestId, VerificationOutcome outcome, UUID verifierId, String message) {
            transition(requestId, "IN_REVIEW", outcome.name(), verifierId, message);
        }

        private void transition(UUID requestId, String from, String to, UUID by, String note) {
            int n = jdbc.update(
                    "UPDATE funding_requests SET status = ?, approved_at = CASE WHEN ? = 'APPROVED' THEN now() ELSE approved_at END,"
                            + " version = version + 1, updated_at = now() WHERE id = ? AND status = ?",
                    to,
                    to,
                    requestId,
                    from);
            if (n != 1) {
                throw new ConflictException("INVALID_STATE_TRANSITION", "Request is not " + from);
            }
            jdbc.update(
                    "INSERT INTO funding_request_status_history (request_id, from_status, to_status, changed_by, note) VALUES (?, ?, ?, ?, ?)",
                    requestId,
                    from,
                    to,
                    by,
                    note);
        }

        @Override
        public PresignedUrl issueDocumentViewUrl(UUID requestId, UUID documentId, UUID actorId) {
            List<String> keys = jdbc.queryForList(
                    "SELECT storage_key FROM request_documents WHERE id = ? AND request_id = ? AND status = 'UPLOADED'",
                    String.class,
                    documentId,
                    requestId);
            if (keys.isEmpty()) {
                throw new NotFoundException();
            }
            audit.record(new AuditEntry(
                    AuditActions.DOCUMENT_VIEW_URL_ISSUED,
                    "RequestDocument",
                    documentId,
                    Map.of("requestId", requestId.toString())));
            return storage.presignGet(keys.get(0), props.documents().viewUrlTtl());
        }
    }
}
