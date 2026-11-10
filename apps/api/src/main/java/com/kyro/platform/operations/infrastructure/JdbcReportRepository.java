package com.kyro.platform.operations.infrastructure;

import com.kyro.platform.operations.application.DashboardCounts;
import com.kyro.platform.operations.application.HoldRow;
import com.kyro.platform.operations.application.ReportQueue;
import com.kyro.platform.operations.application.ReportRepository;
import com.kyro.platform.operations.application.ReportRow;
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

/**
 * JDBC adapter. Reporting read exception (D-023): this module only READS {@code discovery_public_requests},
 * {@code funding_requests} and {@code verification_cases} with plain SQL, never importing those modules' Java code,
 * and writes only its own tables.
 */
@Repository
class JdbcReportRepository implements ReportRepository {

    private static final String SELECT =
            """
            SELECT r.id, r.reporter_user_id, r.request_id, fr.title, fr.recipient_user_id, fr.status AS request_status,
                   r.reason, r.details, r.status, r.assigned_to, r.created_at, r.resolution, r.resolution_note,
                   r.resolved_by, r.resolved_at,
                   EXISTS (SELECT 1 FROM request_moderation_holds h
                            WHERE h.request_id = r.request_id AND h.released_at IS NULL) AS on_hold
              FROM user_reports r
              JOIN funding_requests fr ON fr.id = r.request_id
            """;

    private static final RowMapper<ReportRow> MAPPER = JdbcReportRepository::map;

    private final JdbcTemplate jdbc;

    JdbcReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> findPublicRequestOwner(UUID requestId) {
        return jdbc.query(
                        "SELECT recipient_user_id FROM discovery_public_requests WHERE id = ?",
                        (rs, i) -> rs.getObject(1, UUID.class),
                        requestId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Inserted> insertReport(UUID reporterId, UUID requestId, String reason, String details) {
        return jdbc
                .query(
                        """
                        INSERT INTO user_reports (reporter_user_id, request_id, reason, details)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        RETURNING id, created_at
                        """,
                        (rs, i) -> new Inserted(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant()),
                        reporterId,
                        requestId,
                        reason,
                        details)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ReportRow> find(UUID reportId) {
        return jdbc.query(SELECT + " WHERE r.id = ?", MAPPER, reportId).stream().findFirst();
    }

    @Override
    public List<ReportRow> page(ReportQueue queue, UUID actorId, Instant afterAt, UUID afterId, int limit) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE ");
        List<Object> args = new ArrayList<>();
        switch (queue) {
            case OPEN -> sql.append("r.status IN ('OPEN', 'IN_REVIEW')");
            case MINE -> {
                sql.append("r.status = 'IN_REVIEW' AND r.assigned_to = ?");
                args.add(actorId);
            }
            case RESOLVED -> sql.append("r.status = 'RESOLVED'");
            case ALL -> sql.append("TRUE");
        }
        String cmp = queue.oldestFirst() ? ">" : "<";
        if (afterAt != null && afterId != null) {
            sql.append(" AND (r.created_at, r.id) ").append(cmp).append(" (?, ?)");
            args.add(Timestamp.from(afterAt));
            args.add(afterId);
        }
        String dir = queue.oldestFirst() ? "ASC" : "DESC";
        sql.append(" ORDER BY r.created_at ").append(dir).append(", r.id ").append(dir).append(" LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    @Override
    public List<ReportRow> otherReports(UUID requestId, UUID excludeReportId) {
        return jdbc.query(
                SELECT + " WHERE r.request_id = ? AND r.id <> ? ORDER BY r.created_at DESC, r.id DESC",
                MAPPER,
                requestId,
                excludeReportId);
    }

    @Override
    public boolean claim(UUID reportId, UUID userId, Instant at) {
        return jdbc.update(
                        """
                        UPDATE user_reports
                           SET status = 'IN_REVIEW', assigned_to = ?, assigned_at = ?, version = version + 1
                         WHERE id = ? AND status = 'OPEN'
                        """,
                        userId,
                        Timestamp.from(at),
                        reportId)
                == 1;
    }

    @Override
    public boolean resolve(UUID reportId, UUID assignee, String resolution, String note, Instant at) {
        return jdbc.update(
                        """
                        UPDATE user_reports
                           SET status = 'RESOLVED', resolution = ?, resolution_note = ?, resolved_by = ?, resolved_at = ?,
                               version = version + 1
                         WHERE id = ? AND status = 'IN_REVIEW' AND assigned_to = ?
                        """,
                        resolution,
                        note,
                        assignee,
                        Timestamp.from(at),
                        reportId,
                        assignee)
                == 1;
    }

    @Override
    public boolean insertHold(UUID requestId, UUID reportId, String reason, UUID placedBy, Instant at) {
        return jdbc.update(
                        """
                        INSERT INTO request_moderation_holds (request_id, report_id, reason, placed_by, placed_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """,
                        requestId,
                        reportId,
                        reason,
                        placedBy,
                        Timestamp.from(at))
                == 1;
    }

    @Override
    public boolean releaseHold(UUID requestId, UUID releasedBy, Instant at) {
        return jdbc.update(
                        """
                        UPDATE request_moderation_holds SET released_by = ?, released_at = ?
                         WHERE request_id = ? AND released_at IS NULL
                        """,
                        releasedBy,
                        Timestamp.from(at),
                        requestId)
                == 1;
    }

    @Override
    public Optional<HoldRow> activeHold(UUID requestId) {
        return jdbc
                .query(
                        """
                        SELECT request_id, report_id, reason, placed_by, placed_at
                          FROM request_moderation_holds WHERE request_id = ? AND released_at IS NULL
                        """,
                        (rs, i) -> new HoldRow(
                                rs.getObject("request_id", UUID.class),
                                rs.getObject("report_id", UUID.class),
                                rs.getString("reason"),
                                rs.getObject("placed_by", UUID.class),
                                rs.getTimestamp("placed_at").toInstant()),
                        requestId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<UUID> latestCaseId(UUID requestId) {
        return jdbc.query(
                        "SELECT id FROM verification_cases WHERE request_id = ? ORDER BY submission_number DESC LIMIT 1",
                        (rs, i) -> rs.getObject(1, UUID.class),
                        requestId)
                .stream()
                .findFirst();
    }

    @Override
    public DashboardCounts counts(UUID actorId, Instant now) {
        return jdbc.queryForObject(
                """
                SELECT (SELECT count(*) FROM verification_cases WHERE status = 'OPEN'),
                       (SELECT count(*) FROM verification_cases WHERE status = 'ASSIGNED' AND assigned_to = ?),
                       (SELECT count(*) FROM user_reports WHERE status <> 'RESOLVED'),
                       (SELECT count(*) FROM user_reports WHERE status = 'OPEN'),
                       (SELECT count(*) FROM request_moderation_holds WHERE released_at IS NULL)
                """,
                (rs, i) -> new DashboardCounts(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), now),
                actorId);
    }

    private static ReportRow map(ResultSet rs, int i) throws SQLException {
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        return new ReportRow(
                rs.getObject("id", UUID.class),
                rs.getObject("reporter_user_id", UUID.class),
                rs.getObject("request_id", UUID.class),
                rs.getString("title"),
                rs.getObject("recipient_user_id", UUID.class),
                rs.getString("request_status"),
                rs.getString("reason"),
                rs.getString("details"),
                rs.getString("status"),
                rs.getObject("assigned_to", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("resolution"),
                rs.getString("resolution_note"),
                rs.getObject("resolved_by", UUID.class),
                resolvedAt == null ? null : resolvedAt.toInstant(),
                rs.getBoolean("on_hold"));
    }
}
