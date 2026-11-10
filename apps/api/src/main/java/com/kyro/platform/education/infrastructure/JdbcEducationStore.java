package com.kyro.platform.education.infrastructure;

import com.kyro.platform.education.domain.DuplicateMatch;
import com.kyro.platform.education.domain.EducationStore;
import com.kyro.platform.education.domain.FeeReference;
import com.kyro.platform.education.domain.FundingRequest;
import com.kyro.platform.education.domain.RequestDocument;
import com.kyro.platform.education.domain.RequestFields;
import com.kyro.platform.education.domain.RequestStatus;
import com.kyro.platform.education.domain.RequestSummaryRow;
import com.kyro.platform.education.domain.StatusHistoryItem;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEducationStore implements EducationStore {

    private static final String REQUEST_SELECT =
            """
            SELECT r.*, coalesce(p.public_display_name, 'Recipient') AS public_display_name
            FROM funding_requests r LEFT JOIN recipient_profiles p ON p.user_id = r.recipient_user_id
            """;

    private static final String DOC_COLUMNS =
            "id, request_id, uploaded_by, document_type, storage_key, original_file_name, content_type, size_bytes, sha256, status, created_at, uploaded_at";

    private static final RowMapper<FundingRequest> REQUEST_MAPPER = (rs, i) -> new FundingRequest(
            rs.getObject("id", UUID.class),
            rs.getObject("recipient_user_id", UUID.class),
            RequestStatus.valueOf(rs.getString("status")),
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
            rs.getObject("due_date", LocalDate.class),
            rs.getInt("submission_count"),
            instant(rs, "submitted_at"),
            instant(rs, "approved_at"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getLong("version"),
            rs.getString("public_display_name"));

    private static final RowMapper<RequestDocument> DOC_MAPPER = (rs, i) -> new RequestDocument(
            rs.getObject("id", UUID.class),
            rs.getObject("request_id", UUID.class),
            rs.getObject("uploaded_by", UUID.class),
            rs.getString("document_type"),
            rs.getString("storage_key"),
            rs.getString("original_file_name"),
            rs.getString("content_type"),
            rs.getLong("size_bytes"),
            rs.getString("sha256"),
            rs.getString("status"),
            instant(rs, "created_at"),
            instant(rs, "uploaded_at"));

    private final JdbcTemplate jdbc;

    JdbcEducationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    @Override
    public UUID insertRequest(UUID recipientUserId, RequestFields f) {
        return jdbc.queryForObject(
                """
                INSERT INTO funding_requests (recipient_user_id, status, title, story, expense_type, institution_name,
                    institution_city, institution_country, course_name, fee_reference, fee_reference_normalized,
                    amount_minor, currency, due_date)
                VALUES (?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """,
                UUID.class,
                recipientUserId,
                f.title(),
                f.story(),
                f.expenseType(),
                f.institutionName(),
                f.institutionCity(),
                f.institutionCountry(),
                f.courseName(),
                f.feeReference(),
                FeeReference.normalize(f.feeReference()),
                f.amountMinor(),
                f.currency(),
                f.dueDate() == null ? null : java.sql.Date.valueOf(f.dueDate()));
    }

    @Override
    public Optional<FundingRequest> findRequest(UUID id, boolean forUpdate) {
        List<FundingRequest> rows = jdbc.query(
                REQUEST_SELECT + " WHERE r.id = ?" + (forUpdate ? " FOR UPDATE OF r" : ""), REQUEST_MAPPER, id);
        return rows.stream().findFirst();
    }

    @Override
    public Map<UUID, FundingRequest> findRequests(Collection<UUID> ids) {
        Map<UUID, FundingRequest> out = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        String placeholders = String.join(",", ids.stream().map(x -> "?").toList());
        jdbc.query(REQUEST_SELECT + " WHERE r.id IN (" + placeholders + ")", REQUEST_MAPPER, ids.toArray())
                .forEach(r -> out.put(r.id(), r));
        return out;
    }

    @Override
    public boolean updateFields(UUID id, long expectedVersion, RequestFields f) {
        return jdbc.update(
                        """
                        UPDATE funding_requests SET title = ?, story = ?, expense_type = ?, institution_name = ?,
                            institution_city = ?, institution_country = ?, course_name = ?, fee_reference = ?,
                            fee_reference_normalized = ?, amount_minor = ?, currency = ?, due_date = ?,
                            version = version + 1, updated_at = now()
                        WHERE id = ? AND version = ? AND status IN ('DRAFT', 'CHANGES_REQUESTED')
                        """,
                        f.title(),
                        f.story(),
                        f.expenseType(),
                        f.institutionName(),
                        f.institutionCity(),
                        f.institutionCountry(),
                        f.courseName(),
                        f.feeReference(),
                        FeeReference.normalize(f.feeReference()),
                        f.amountMinor(),
                        f.currency(),
                        f.dueDate() == null ? null : java.sql.Date.valueOf(f.dueDate()),
                        id,
                        expectedVersion)
                == 1;
    }

    @Override
    public boolean transition(UUID id, RequestStatus from, RequestStatus to, long expectedVersion, Instant now) {
        String extra =
                switch (to) {
                    case SUBMITTED -> ", submission_count = submission_count + 1, submitted_at = ?";
                    case APPROVED -> ", approved_at = ?";
                    default -> "";
                };
        String sql = "UPDATE funding_requests SET status = ?, version = version + 1, updated_at = now()" + extra
                + " WHERE id = ? AND status = ? AND version = ?";
        boolean withTime = !extra.isEmpty();
        Object[] args = withTime
                ? new Object[] {to.name(), ts(now), id, from.name(), expectedVersion}
                : new Object[] {to.name(), id, from.name(), expectedVersion};
        return jdbc.update(sql, args) == 1;
    }

    @Override
    public void addHistory(UUID requestId, RequestStatus from, RequestStatus to, UUID changedBy, String note) {
        jdbc.update(
                "INSERT INTO funding_request_status_history (request_id, from_status, to_status, changed_by, note) VALUES (?, ?, ?, ?, ?)",
                requestId,
                from == null ? null : from.name(),
                to.name(),
                changedBy,
                note);
    }

    @Override
    public List<StatusHistoryItem> history(UUID requestId) {
        return jdbc.query(
                "SELECT from_status, to_status, changed_at, note FROM funding_request_status_history WHERE request_id = ? ORDER BY changed_at, id",
                (rs, i) -> new StatusHistoryItem(
                        rs.getString("from_status") == null ? null : RequestStatus.valueOf(rs.getString("from_status")),
                        RequestStatus.valueOf(rs.getString("to_status")),
                        instant(rs, "changed_at"),
                        rs.getString("note")),
                requestId);
    }

    @Override
    public List<RequestSummaryRow> listByRecipient(UUID recipientUserId) {
        return jdbc.query(
                """
                SELECT r.id, r.title, r.status, r.amount_minor, r.currency, r.due_date, r.updated_at,
                    (SELECT count(*) FROM request_documents d WHERE d.request_id = r.id AND d.status = 'UPLOADED') AS doc_count
                FROM funding_requests r WHERE r.recipient_user_id = ? ORDER BY r.created_at DESC, r.id DESC
                """,
                (rs, i) -> new RequestSummaryRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        RequestStatus.valueOf(rs.getString("status")),
                        rs.getLong("amount_minor"),
                        rs.getString("currency"),
                        rs.getObject("due_date", LocalDate.class),
                        rs.getInt("doc_count"),
                        instant(rs, "updated_at")),
                recipientUserId);
    }

    @Override
    public void insertDocument(RequestDocument d) {
        jdbc.update(
                """
                INSERT INTO request_documents (id, request_id, uploaded_by, document_type, storage_key, original_file_name,
                    content_type, size_bytes, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_UPLOAD')
                """,
                d.id(),
                d.requestId(),
                d.uploadedBy(),
                d.documentType(),
                d.storageKey(),
                d.originalFileName(),
                d.contentType(),
                d.sizeBytes());
    }

    @Override
    public Optional<RequestDocument> findDocument(UUID requestId, UUID documentId, boolean forUpdate) {
        return jdbc
                .query(
                        "SELECT " + DOC_COLUMNS + " FROM request_documents WHERE id = ? AND request_id = ? AND status <> 'DELETED'"
                                + (forUpdate ? " FOR UPDATE" : ""),
                        DOC_MAPPER,
                        documentId,
                        requestId)
                .stream()
                .findFirst();
    }

    @Override
    public List<RequestDocument> documents(UUID requestId) {
        return jdbc.query(
                "SELECT " + DOC_COLUMNS + " FROM request_documents WHERE request_id = ? AND status <> 'DELETED' ORDER BY created_at, id",
                DOC_MAPPER,
                requestId);
    }

    @Override
    public int countActiveDocuments(UUID requestId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM request_documents WHERE request_id = ? AND status IN ('PENDING_UPLOAD', 'UPLOADED')",
                Integer.class,
                requestId);
        return n == null ? 0 : n;
    }

    @Override
    public int countUploadedDocuments(UUID requestId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM request_documents WHERE request_id = ? AND status = 'UPLOADED'", Integer.class, requestId);
        return n == null ? 0 : n;
    }

    @Override
    public void markUploaded(UUID documentId, String sha256, Instant at) {
        jdbc.update("UPDATE request_documents SET status = 'UPLOADED', sha256 = ?, uploaded_at = ? WHERE id = ?", sha256, ts(at), documentId);
    }

    @Override
    public void markRejected(UUID documentId) {
        jdbc.update("UPDATE request_documents SET status = 'REJECTED' WHERE id = ?", documentId);
    }

    @Override
    public void markDeleted(UUID documentId, Instant at) {
        jdbc.update("UPDATE request_documents SET status = 'DELETED', deleted_at = ? WHERE id = ?", ts(at), documentId);
    }

    @Override
    public List<DuplicateMatch> findDuplicates(FundingRequest r) {
        List<DuplicateMatch> out = new java.util.ArrayList<>();
        out.addAll(jdbc.query(
                """
                SELECT DISTINCT d2.request_id AS other_id, r2.status AS other_status
                FROM request_documents d1
                JOIN request_documents d2 ON d2.sha256 = d1.sha256 AND d2.request_id <> d1.request_id AND d2.status = 'UPLOADED'
                JOIN funding_requests r2 ON r2.id = d2.request_id
                WHERE d1.request_id = ? AND d1.status = 'UPLOADED' AND d1.sha256 IS NOT NULL
                """,
                (rs, i) -> new DuplicateMatch(
                        "DOCUMENT_HASH_MATCH", rs.getObject("other_id", UUID.class), RequestStatus.valueOf(rs.getString("other_status"))),
                r.id()));
        String normalized = FeeReference.normalize(r.feeReference());
        if (normalized != null && r.institutionName() != null && !r.institutionName().isBlank()) {
            out.addAll(jdbc.query(
                    """
                    SELECT id, status FROM funding_requests
                    WHERE id <> ? AND fee_reference_normalized = ? AND lower(institution_name) = lower(?) AND status <> 'WITHDRAWN'
                    """,
                    (rs, i) -> new DuplicateMatch(
                            "FEE_REFERENCE_MATCH", rs.getObject("id", UUID.class), RequestStatus.valueOf(rs.getString("status"))),
                    r.id(),
                    normalized,
                    r.institutionName()));
        }
        return out;
    }
}
