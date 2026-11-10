package com.kyro.platform.funding.application;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.errors.ConflictException;
import com.kyro.platform.common.errors.FieldError;
import com.kyro.platform.common.errors.NotFoundException;
import com.kyro.platform.common.errors.UnprocessableException;
import com.kyro.platform.common.security.CurrentActor;
import com.kyro.platform.common.security.KyroPrincipal;
import com.kyro.platform.common.security.Role;
import com.kyro.platform.contract.model.FundingSummary;
import com.kyro.platform.contract.model.Money;
import com.kyro.platform.contract.model.Pledge;
import com.kyro.platform.contract.model.PledgeCancelReason;
import com.kyro.platform.contract.model.PledgeCreate;
import com.kyro.platform.contract.model.PledgeListItem;
import com.kyro.platform.contract.model.PledgePage;
import com.kyro.platform.contract.model.PledgeStatus;
import com.kyro.platform.contract.model.PublicRequestRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Non-binding pledges. A pledge is a statement of intent: it is never money raised and never reduces the outstanding
 * need. The campaign row is the lock anchor that keeps concurrent pledges within the eligible amount. Reads the
 * {@code discovery_public_requests} view and never imports education code.
 */
@Service
public class PledgeService {

    public record CreateResult(Pledge pledge, boolean created) {}

    private record PublicRequest(UUID id, UUID recipientUserId, long amountMinor, String currency, LocalDate dueDate) {}

    private static final String PLEDGE_COLUMNS =
            "p.id, p.request_id, p.amount_minor, p.currency, p.status, p.cancel_reason, p.created_at, p.cancelled_at";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CurrentActor actor;
    private final AuditService audit;
    private final AccessAuditor accessAuditor;
    private final FundingProperties props;

    public PledgeService(
            JdbcTemplate jdbc,
            Clock clock,
            CurrentActor actor,
            AuditService audit,
            AccessAuditor accessAuditor,
            FundingProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.actor = actor;
        this.audit = audit;
        this.accessAuditor = accessAuditor;
        this.props = props;
    }

    /** Confirmed contributions. Always 0 until payments exist (a later phase); pledges are never counted here. */
    long confirmedMinor(UUID requestId) {
        // TODO(payments): sum confirmed campaign_contributions once payments exist.
        return 0L;
    }

    // ---- create ----

    @Transactional
    public CreateResult create(UUID requestId, String idempotencyKey, PledgeCreate body) {
        KyroPrincipal me = actor.require();
        PublicRequest req = publicRequest(requestId).orElseThrow(NotFoundException::new);
        if (req.recipientUserId().equals(me.userId())) {
            throw new UnprocessableException("PLEDGE_NOT_ALLOWED", "You cannot pledge to your own request.");
        }
        if (req.dueDate() != null && req.dueDate().isBefore(LocalDate.now(clock))) {
            throw new UnprocessableException("PLEDGE_NOT_ALLOWED", "This request is past its due date.");
        }
        Money amount = body.getAmount();
        if (!req.currency().equals(amount.getCurrency()) || !props.currencies().contains(amount.getCurrency())) {
            throw new UnprocessableException(
                    "UNSUPPORTED_CURRENCY", "Pledges for this request must be in " + req.currency() + ".");
        }
        if (amount.getAmountMinor() < props.minPledgeMinor()) {
            throw new UnprocessableException(
                    "VALIDATION_FAILED",
                    "One or more fields are invalid.",
                    List.of(new FieldError("amount.amountMinor", "must be at least " + props.minPledgeMinor())));
        }
        if (!Boolean.TRUE.equals(body.getAcknowledgeNonBinding())) {
            throw new UnprocessableException(
                    "VALIDATION_FAILED",
                    "One or more fields are invalid.",
                    List.of(new FieldError("acknowledgeNonBinding", "must be true")));
        }

        jdbc.update(
                "INSERT INTO campaigns (request_id, target_minor, currency) VALUES (?, ?, ?) ON CONFLICT (request_id) DO NOTHING",
                requestId,
                req.amountMinor(),
                req.currency());
        UUID campaignId = jdbc.queryForObject("SELECT id FROM campaigns WHERE request_id = ? FOR UPDATE", UUID.class, requestId);
        // The request may have been withdrawn or held while we waited for the lock.
        if (publicRequest(requestId).isEmpty()) {
            throw new NotFoundException();
        }

        Optional<Pledge> existing = jdbc.query(
                        "SELECT " + PLEDGE_COLUMNS + " FROM pledges p WHERE p.donor_user_id = ? AND p.idempotency_key = ?",
                        (rs, i) -> pledge(rs),
                        me.userId(),
                        idempotencyKey)
                .stream()
                .findFirst();
        if (existing.isPresent()) {
            Pledge p = existing.get();
            if (p.getRequestId().equals(requestId)
                    && p.getAmount().getAmountMinor().equals(amount.getAmountMinor())
                    && p.getAmount().getCurrency().equals(amount.getCurrency())) {
                return new CreateResult(p, false);
            }
            throw new UnprocessableException(
                    "IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used with a different request.");
        }

        Integer active = jdbc.queryForObject(
                "SELECT count(*) FROM pledges WHERE donor_user_id = ? AND request_id = ? AND status = 'ACTIVE'",
                Integer.class,
                me.userId(),
                requestId);
        if (active != null && active > 0) {
            throw new ConflictException("ACTIVE_PLEDGE_EXISTS", "You already have an active pledge on this request.");
        }

        long pledgeable = Math.max(0L, req.amountMinor() - confirmedMinor(requestId) - activePledged(requestId));
        if (amount.getAmountMinor() > pledgeable) {
            throw new ConflictException(
                    "PLEDGE_EXCEEDS_REMAINING",
                    "The amount exceeds what can still be pledged for this request (" + pledgeable + " minor units).");
        }

        Timestamp now = Timestamp.from(clock.instant());
        UUID id;
        try {
            id = jdbc.queryForObject(
                    """
                    INSERT INTO pledges (campaign_id, request_id, donor_user_id, amount_minor, currency, idempotency_key,
                        created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """,
                    UUID.class,
                    campaignId,
                    requestId,
                    me.userId(),
                    amount.getAmountMinor(),
                    amount.getCurrency(),
                    idempotencyKey,
                    now,
                    now);
        } catch (DuplicateKeyException e) {
            // Backstop for races the campaign lock cannot serialise (the same key used on another request).
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("uq_pledges_active_donor_request")) {
                throw new ConflictException("ACTIVE_PLEDGE_EXISTS", "You already have an active pledge on this request.");
            }
            throw new UnprocessableException(
                    "IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used with a different request.");
        }
        audit.record(new AuditEntry(
                AuditActions.PLEDGE_CREATED,
                "PLEDGE",
                id,
                Map.of("requestId", requestId.toString(), "amountMinor", amount.getAmountMinor(), "currency", amount.getCurrency())));
        return new CreateResult(load(id), true);
    }

    // ---- read / cancel ----

    @Transactional(readOnly = true)
    public Pledge get(UUID pledgeId) {
        KyroPrincipal me = actor.require();
        return owned(pledgeId, me);
    }

    @Transactional
    public Pledge cancel(UUID pledgeId) {
        KyroPrincipal me = actor.require();
        owned(pledgeId, me);
        Timestamp now = Timestamp.from(clock.instant());
        int n = jdbc.update(
                """
                UPDATE pledges SET status = 'CANCELLED', cancel_reason = 'DONOR_CANCELLED', cancelled_at = ?, updated_at = ?,
                    version = version + 1
                WHERE id = ? AND status = 'ACTIVE'
                """,
                now,
                now,
                pledgeId);
        if (n == 0) {
            throw new ConflictException("INVALID_STATE_TRANSITION", "Only an active pledge can be cancelled.");
        }
        audit.record(new AuditEntry(
                AuditActions.PLEDGE_CANCELLED, "PLEDGE", pledgeId, Map.of("reason", PledgeCancelReason.DONOR_CANCELLED.getValue())));
        return load(pledgeId);
    }

    /** Called inside the education withdraw transaction: cancels every ACTIVE pledge on the request. */
    @Transactional
    public void cancelAllForWithdrawnRequest(UUID requestId) {
        // Serialise with concurrent pledge creation (it re-checks the public view after taking this lock).
        jdbc.query("SELECT id FROM campaigns WHERE request_id = ? FOR UPDATE", (rs, i) -> rs.getObject(1), requestId);
        Timestamp now = Timestamp.from(clock.instant());
        List<UUID> cancelled = jdbc.query(
                """
                UPDATE pledges SET status = 'CANCELLED', cancel_reason = 'REQUEST_WITHDRAWN', cancelled_at = ?, updated_at = ?,
                    version = version + 1
                WHERE request_id = ? AND status = 'ACTIVE' RETURNING id
                """,
                (rs, i) -> rs.getObject(1, UUID.class),
                now,
                now,
                requestId);
        for (UUID id : cancelled) {
            audit.record(new AuditEntry(
                    AuditActions.PLEDGE_CANCELLED,
                    "PLEDGE",
                    id,
                    Map.of("reason", PledgeCancelReason.REQUEST_WITHDRAWN.getValue(), "requestId", requestId.toString())));
        }
    }

    private record ListRow(Pledge pledge, Instant createdAt, PublicRequestRef ref) {}

    @Transactional(readOnly = true)
    public PledgePage listMine(String cursor, Integer limit) {
        KyroPrincipal me = actor.require();
        int size = limit == null ? 20 : Math.max(1, Math.min(limit, 50));
        StringBuilder sql = new StringBuilder("SELECT " + PLEDGE_COLUMNS
                + ", v.id AS pub_id, v.title AS pub_title, v.recipient_public_name AS pub_name"
                + " FROM pledges p LEFT JOIN discovery_public_requests v ON v.id = p.request_id WHERE p.donor_user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(me.userId());
        if (cursor != null && !cursor.isEmpty()) {
            PledgeCursor c = PledgeCursor.decode(cursor);
            sql.append(" AND (p.created_at, p.id) < (?, ?)");
            args.add(Timestamp.from(c.at()));
            args.add(c.id());
        }
        sql.append(" ORDER BY p.created_at DESC, p.id DESC LIMIT ?");
        args.add(size + 1);
        List<ListRow> rows = jdbc.query(
                sql.toString(),
                (rs, i) -> {
                    UUID pub = rs.getObject("pub_id", UUID.class);
                    PublicRequestRef ref =
                            pub == null ? null : new PublicRequestRef(pub, rs.getString("pub_title"), rs.getString("pub_name"));
                    return new ListRow(pledge(rs), rs.getTimestamp("created_at").toInstant(), ref);
                },
                args.toArray());
        boolean more = rows.size() > size;
        List<ListRow> page = more ? rows.subList(0, size) : rows;
        String next = more
                ? new PledgeCursor(page.get(size - 1).createdAt(), page.get(size - 1).pledge().getId()).encode()
                : null;
        List<PledgeListItem> items = page.stream()
                .map(r -> new PledgeListItem(r.pledge()).request(r.ref()))
                .toList();
        return new PledgePage(items, next);
    }

    // ---- summary (public) ----

    @Transactional(readOnly = true)
    public FundingSummary summary(UUID requestId) {
        PublicRequest req = publicRequest(requestId).orElseThrow(NotFoundException::new);
        long[] progress = jdbc.query(
                        "SELECT pledged_minor, active_pledge_count FROM funding_request_progress WHERE request_id = ? AND currency = ?",
                        (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)},
                        requestId,
                        req.currency())
                .stream()
                .findFirst()
                .orElse(new long[] {0L, 0L});
        long confirmed = confirmedMinor(requestId);
        long pledged = progress[0];
        FundingSummary s = new FundingSummary(
                requestId,
                new Money(req.amountMinor(), req.currency()),
                confirmed,
                pledged,
                Math.max(0L, req.amountMinor() - confirmed),
                Math.max(0L, req.amountMinor() - confirmed - pledged),
                progress[1],
                true);
        actor.current()
                .filter(p -> p.hasRole(Role.DONOR))
                .flatMap(v -> jdbc.query(
                                "SELECT " + PLEDGE_COLUMNS
                                        + " FROM pledges p WHERE p.donor_user_id = ? AND p.request_id = ? AND p.status = 'ACTIVE'",
                                (rs, i) -> pledge(rs),
                                v.userId(),
                                requestId)
                        .stream()
                        .findFirst())
                .ifPresent(s::viewerPledge);
        return s;
    }

    // ---- helpers ----

    private record Found(Pledge pledge, UUID donor) {}

    private Pledge owned(UUID pledgeId, KyroPrincipal me) {
        Found f = jdbc.query(
                        "SELECT " + PLEDGE_COLUMNS + ", p.donor_user_id FROM pledges p WHERE p.id = ?",
                        (rs, i) -> new Found(pledge(rs), rs.getObject("donor_user_id", UUID.class)),
                        pledgeId)
                .stream()
                .findFirst()
                .orElseThrow(NotFoundException::new);
        if (!f.donor().equals(me.userId())) {
            accessAuditor.denied(pledgeId, "pledge");
            throw new NotFoundException();
        }
        return f.pledge();
    }

    private Pledge load(UUID id) {
        return jdbc.queryForObject("SELECT " + PLEDGE_COLUMNS + " FROM pledges p WHERE p.id = ?", (rs, i) -> pledge(rs), id);
    }

    private long activePledged(UUID requestId) {
        Long sum = jdbc.queryForObject(
                "SELECT COALESCE(sum(amount_minor), 0) FROM pledges WHERE request_id = ? AND status = 'ACTIVE'",
                Long.class,
                requestId);
        return sum == null ? 0L : sum;
    }

    private Optional<PublicRequest> publicRequest(UUID requestId) {
        return jdbc.query(
                        "SELECT v.id, v.recipient_user_id, v.amount_minor, v.currency, v.due_date FROM discovery_public_requests v WHERE v.id = ?",
                        (rs, i) -> {
                            java.sql.Date due = rs.getDate("due_date");
                            return new PublicRequest(
                                    rs.getObject("id", UUID.class),
                                    rs.getObject("recipient_user_id", UUID.class),
                                    rs.getLong("amount_minor"),
                                    rs.getString("currency"),
                                    due == null ? null : due.toLocalDate());
                        },
                        requestId)
                .stream()
                .findFirst();
    }

    private static Pledge pledge(ResultSet rs) throws SQLException {
        Timestamp cancelledAt = rs.getTimestamp("cancelled_at");
        String reason = rs.getString("cancel_reason");
        return new Pledge(
                        rs.getObject("id", UUID.class),
                        rs.getObject("request_id", UUID.class),
                        new Money(rs.getLong("amount_minor"), rs.getString("currency")),
                        PledgeStatus.fromValue(rs.getString("status")),
                        true,
                        rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC))
                .cancelReason(reason == null ? null : PledgeCancelReason.fromValue(reason))
                .cancelledAt(cancelledAt == null ? null : cancelledAt.toInstant().atOffset(ZoneOffset.UTC));
    }
}
