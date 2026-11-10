package com.kyro.platform.verification.infrastructure;

import com.kyro.platform.verification.application.CaseRepository;
import com.kyro.platform.verification.application.CaseRow;
import com.kyro.platform.verification.application.DecisionRow;
import com.kyro.platform.verification.application.Queue;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCaseRepository implements CaseRepository {

    private static final String CASE_COLUMNS =
            "id, request_id, submission_number, status, outcome, assigned_to, assigned_at, opened_at, closed_at, version";

    private static final RowMapper<CaseRow> CASE_MAPPER = JdbcCaseRepository::mapCase;

    private final JdbcTemplate jdbc;

    JdbcCaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CaseRow> find(UUID caseId) {
        return jdbc.query("SELECT " + CASE_COLUMNS + " FROM verification_cases WHERE id = ?", CASE_MAPPER, caseId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<UUID> insertOpen(UUID requestId, int submissionNumber, Instant openedAt) {
        return jdbc
                .query(
                        """
                        INSERT INTO verification_cases (request_id, submission_number, status, opened_at)
                        VALUES (?, ?, 'OPEN', ?)
                        ON CONFLICT DO NOTHING
                        RETURNING id
                        """,
                        (rs, i) -> rs.getObject(1, UUID.class),
                        requestId,
                        submissionNumber,
                        Timestamp.from(openedAt))
                .stream()
                .findFirst();
    }

    @Override
    public boolean claim(UUID caseId, UUID userId, Instant at) {
        return jdbc.update(
                        """
                        UPDATE verification_cases
                           SET status = 'ASSIGNED', assigned_to = ?, assigned_at = ?, version = version + 1
                         WHERE id = ? AND status = 'OPEN'
                        """,
                        userId,
                        Timestamp.from(at),
                        caseId)
                == 1;
    }

    @Override
    public boolean closeAssigned(UUID caseId, UUID assignee, String outcome, Instant at) {
        return jdbc.update(
                        """
                        UPDATE verification_cases
                           SET status = 'CLOSED', outcome = ?, closed_at = ?, version = version + 1
                         WHERE id = ? AND status = 'ASSIGNED' AND assigned_to = ?
                        """,
                        outcome,
                        Timestamp.from(at),
                        caseId,
                        assignee)
                == 1;
    }

    @Override
    public List<UUID> closeUnclosedForRequest(UUID requestId, String outcome, Instant at) {
        return jdbc.query(
                """
                UPDATE verification_cases
                   SET status = 'CLOSED', outcome = ?, closed_at = ?, version = version + 1
                 WHERE request_id = ? AND status <> 'CLOSED'
                RETURNING id
                """,
                (rs, i) -> rs.getObject(1, UUID.class),
                outcome,
                Timestamp.from(at),
                requestId);
    }

    @Override
    public void insertDecision(
            UUID caseId, UUID decidedBy, String decision, String rationale, String messageToRecipient, Instant at) {
        jdbc.update(
                """
                INSERT INTO verification_decisions (case_id, decided_by, decision, rationale, message_to_recipient, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                caseId,
                decidedBy,
                decision,
                rationale,
                messageToRecipient,
                Timestamp.from(at));
    }

    @Override
    public List<DecisionRow> decisionsForRequest(UUID requestId) {
        return jdbc.query(
                """
                SELECT d.id, d.case_id, d.decided_by, d.decision, d.rationale, d.message_to_recipient, d.created_at
                  FROM verification_decisions d
                  JOIN verification_cases c ON c.id = d.case_id
                 WHERE c.request_id = ?
                 ORDER BY d.created_at DESC, d.id DESC
                """,
                (rs, i) -> new DecisionRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("case_id", UUID.class),
                        rs.getObject("decided_by", UUID.class),
                        rs.getString("decision"),
                        rs.getString("rationale"),
                        rs.getString("message_to_recipient"),
                        rs.getTimestamp("created_at").toInstant()),
                requestId);
    }

    @Override
    public List<CaseRow> page(Queue queue, UUID userId, Instant afterOpenedAt, UUID afterId, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT " + CASE_COLUMNS + " FROM verification_cases WHERE ");
        switch (queue) {
            case UNASSIGNED -> sql.append("status = 'OPEN'");
            case MINE -> {
                sql.append("status = 'ASSIGNED' AND assigned_to = ?");
                args.add(userId);
            }
            case CLOSED -> sql.append("status = 'CLOSED'");
            case ALL -> sql.append("TRUE");
        }
        String op = queue.oldestFirst() ? ">" : "<";
        String dir = queue.oldestFirst() ? "ASC" : "DESC";
        if (afterOpenedAt != null && afterId != null) {
            sql.append(" AND (opened_at, id) ").append(op).append(" (?, ?)");
            args.add(Timestamp.from(afterOpenedAt));
            args.add(afterId);
        }
        sql.append(" ORDER BY opened_at ").append(dir).append(", id ").append(dir).append(" LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), CASE_MAPPER, args.toArray());
    }

    private static CaseRow mapCase(ResultSet rs, int i) throws SQLException {
        Timestamp assignedAt = rs.getTimestamp("assigned_at");
        Timestamp closedAt = rs.getTimestamp("closed_at");
        return new CaseRow(
                rs.getObject("id", UUID.class),
                rs.getObject("request_id", UUID.class),
                rs.getInt("submission_number"),
                rs.getString("status"),
                rs.getString("outcome"),
                rs.getObject("assigned_to", UUID.class),
                assignedAt == null ? null : assignedAt.toInstant(),
                rs.getTimestamp("opened_at").toInstant(),
                closedAt == null ? null : closedAt.toInstant(),
                rs.getLong("version"));
    }
}
