package com.kyro.platform.discovery.application;

import com.kyro.platform.common.config.KyroProperties;
import com.kyro.platform.common.errors.NotFoundException;
import com.kyro.platform.common.errors.UnprocessableException;
import com.kyro.platform.common.security.CurrentActor;
import com.kyro.platform.common.security.KyroPrincipal;
import com.kyro.platform.contract.model.ExpenseType;
import com.kyro.platform.contract.model.FeedPage;
import com.kyro.platform.contract.model.FundingProgress;
import com.kyro.platform.contract.model.Money;
import com.kyro.platform.contract.model.PublicRequestDetail;
import com.kyro.platform.contract.model.RequestCard;
import com.kyro.platform.contract.model.SavedItem;
import com.kyro.platform.contract.model.SavedPage;
import com.kyro.platform.contract.model.SwipeAction;
import com.kyro.platform.contract.model.SwipeResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Discovery reads only the {@code discovery_public_requests} view and the {@code swipes} table. */
@Service
public class DiscoveryService {

    private static final String CARD_COLUMNS =
            "v.id, v.title, v.story, v.expense_type, v.institution_name, v.institution_city, v.institution_country,"
                    + " v.course_name, v.amount_minor, v.currency, v.due_date, v.approved_at, v.recipient_public_name,"
                    + " COALESCE(fp.pledged_minor, 0) AS pledged_minor";

    /** funding_request_progress has a row only when a pledge exists, so it is always LEFT JOINed. */
    private static final String PROGRESS_JOIN =
            " LEFT JOIN funding_request_progress fp ON fp.request_id = v.id AND fp.currency = v.currency";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CurrentActor actor;
    private final KyroProperties props;

    public DiscoveryService(JdbcTemplate jdbc, Clock clock, CurrentActor actor, KyroProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.actor = actor;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public FeedPage feed(String cursor, Integer limit) {
        int size = pageSize(limit);
        Optional<KyroPrincipal> me = actor.current();
        StringBuilder sql = new StringBuilder(
                "SELECT " + CARD_COLUMNS + " FROM discovery_public_requests v" + PROGRESS_JOIN + " WHERE (v.due_date IS NULL OR v.due_date >= ?)");
        List<Object> args = new ArrayList<>();
        args.add(java.sql.Date.valueOf(LocalDate.now(clock)));
        if (me.isPresent()) {
            sql.append(" AND v.recipient_user_id <> ?"
                    + " AND NOT EXISTS (SELECT 1 FROM swipes s WHERE s.request_id = v.id AND s.donor_user_id = ?)");
            args.add(me.get().userId());
            args.add(me.get().userId());
        }
        if (cursor != null && !cursor.isEmpty()) {
            PageCursor c = PageCursor.decode(cursor);
            sql.append(" AND (v.approved_at, v.id) < (?, ?)");
            args.add(Timestamp.from(c.at()));
            args.add(c.id());
        }
        sql.append(" ORDER BY v.approved_at DESC, v.id DESC LIMIT ?");
        args.add(size + 1);
        List<Row> rows = jdbc.query(sql.toString(), (rs, i) -> row(rs), args.toArray());
        boolean more = rows.size() > size;
        List<Row> page = more ? rows.subList(0, size) : rows;
        String next = more ? new PageCursor(page.get(size - 1).approvedAt, page.get(size - 1).id).encode() : null;
        return new FeedPage(page.stream().map(this::card).toList(), next);
    }

    @Transactional(readOnly = true)
    public PublicRequestDetail detail(UUID requestId) {
        Row r = jdbc.query("SELECT " + CARD_COLUMNS + " FROM discovery_public_requests v" + PROGRESS_JOIN + " WHERE v.id = ?", (rs, i) -> row(rs), requestId)
                .stream()
                .findFirst()
                .orElseThrow(NotFoundException::new);
        RequestCard c = card(r);
        PublicRequestDetail d = new PublicRequestDetail();
        d.id(c.getId())
                .title(c.getTitle())
                .storyExcerpt(c.getStoryExcerpt())
                .recipientPublicName(c.getRecipientPublicName())
                .expenseType(c.getExpenseType())
                .institutionName(c.getInstitutionName())
                .institutionCity(c.getInstitutionCity())
                .institutionCountry(c.getInstitutionCountry())
                .courseName(c.getCourseName())
                .amount(c.getAmount())
                .dueDate(c.getDueDate())
                .verifiedAt(c.getVerifiedAt())
                .funding(c.getFunding())
                .story(r.story);
        actor.current().ifPresent(me -> jdbc.query(
                        "SELECT action FROM swipes WHERE donor_user_id = ? AND request_id = ?",
                        (rs, i) -> SwipeAction.fromValue(rs.getString(1)),
                        me.userId(),
                        requestId)
                .stream()
                .findFirst()
                .ifPresent(d::viewerAction));
        return d;
    }

    @Transactional
    public SwipeResult swipe(UUID requestId, SwipeAction action) {
        KyroPrincipal me = actor.require();
        UUID owner = jdbc.query(
                        "SELECT v.recipient_user_id FROM discovery_public_requests v WHERE v.id = ?",
                        (rs, i) -> rs.getObject(1, UUID.class),
                        requestId)
                .stream()
                .findFirst()
                .orElseThrow(NotFoundException::new);
        if (owner.equals(me.userId())) {
            throw new UnprocessableException("VALIDATION_FAILED", "You cannot swipe on your own request.");
        }
        Timestamp now = Timestamp.from(clock.instant());
        Timestamp updated = jdbc.queryForObject(
                """
                INSERT INTO swipes (donor_user_id, request_id, action, created_at, updated_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (donor_user_id, request_id) DO UPDATE SET action = EXCLUDED.action, updated_at = EXCLUDED.updated_at
                RETURNING updated_at
                """,
                Timestamp.class,
                me.userId(),
                requestId,
                action.getValue(),
                now,
                now);
        return new SwipeResult(requestId, action, updated.toInstant().atOffset(ZoneOffset.UTC));
    }

    private record Saved(UUID swipeId, String action, Instant updatedAt, Row row) {}

    @Transactional(readOnly = true)
    public SavedPage saved(String cursor, Integer limit) {
        KyroPrincipal me = actor.require();
        int size = pageSize(limit);
        StringBuilder sql = new StringBuilder("SELECT s.id AS swipe_id, s.action, s.updated_at, " + CARD_COLUMNS
                + " FROM swipes s JOIN discovery_public_requests v ON v.id = s.request_id" + PROGRESS_JOIN
                + " WHERE s.donor_user_id = ? AND s.action IN ('SAVED', 'INTERESTED')");
        List<Object> args = new ArrayList<>();
        args.add(me.userId());
        if (cursor != null && !cursor.isEmpty()) {
            PageCursor c = PageCursor.decode(cursor);
            sql.append(" AND (s.updated_at, s.id) < (?, ?)");
            args.add(Timestamp.from(c.at()));
            args.add(c.id());
        }
        sql.append(" ORDER BY s.updated_at DESC, s.id DESC LIMIT ?");
        args.add(size + 1);
        List<Saved> rows = jdbc.query(
                sql.toString(),
                (rs, i) -> new Saved(
                        rs.getObject("swipe_id", UUID.class),
                        rs.getString("action"),
                        rs.getTimestamp("updated_at").toInstant(),
                        row(rs)),
                args.toArray());
        boolean more = rows.size() > size;
        List<Saved> page = more ? rows.subList(0, size) : rows;
        String next = more ? new PageCursor(page.get(size - 1).updatedAt, page.get(size - 1).swipeId).encode() : null;
        List<SavedItem> items = page.stream()
                .map(s -> new SavedItem(card(s.row), SavedItem.ActionEnum.fromValue(s.action), s.updatedAt.atOffset(ZoneOffset.UTC)))
                .toList();
        return new SavedPage(items, next);
    }

    /** Removes the donor's SAVED/INTERESTED swipe; idempotent. */
    @Transactional
    public void removeSaved(UUID requestId) {
        KyroPrincipal me = actor.require();
        jdbc.update(
                "DELETE FROM swipes WHERE donor_user_id = ? AND request_id = ? AND action IN ('SAVED', 'INTERESTED')",
                me.userId(),
                requestId);
    }

    private int pageSize(Integer limit) {
        KyroProperties.Discovery cfg = props.discovery();
        int l = limit == null ? cfg.defaultPageSize() : limit;
        return Math.max(1, Math.min(l, cfg.maxPageSize()));
    }

    private record Row(
            UUID id,
            String title,
            String story,
            String expenseType,
            String institutionName,
            String institutionCity,
            String institutionCountry,
            String courseName,
            long amountMinor,
            String currency,
            LocalDate dueDate,
            Instant approvedAt,
            String recipientName,
            long pledgedMinor) {}

    private static Row row(ResultSet rs) throws SQLException {
        java.sql.Date due = rs.getDate("due_date");
        return new Row(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("story"),
                rs.getString("expense_type"),
                rs.getString("institution_name"),
                rs.getString("institution_city"),
                rs.getString("institution_country"),
                rs.getString("course_name"),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                due == null ? null : due.toLocalDate(),
                rs.getTimestamp("approved_at").toInstant(),
                rs.getString("recipient_public_name"),
                rs.getLong("pledged_minor"));
    }

    private RequestCard card(Row r) {
        int max = props.discovery().storyExcerptLength();
        String story = r.story == null ? "" : r.story;
        String excerpt = story.length() <= max ? story : story.substring(0, max);
        Money amount = new Money(r.amountMinor, r.currency);
        return new RequestCard(
                        r.id,
                        r.title,
                        excerpt,
                        r.recipientName,
                        ExpenseType.fromValue(r.expenseType),
                        amount,
                        r.approvedAt.atOffset(ZoneOffset.UTC),
                        new FundingProgress(amount, r.pledgedMinor, 0L))
                .institutionName(r.institutionName)
                .institutionCity(r.institutionCity)
                .institutionCountry(r.institutionCountry)
                .courseName(r.courseName)
                .dueDate(r.dueDate);
    }
}
