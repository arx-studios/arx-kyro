package com.kyro.platform.education.application;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.errors.ConflictException;
import com.kyro.platform.common.errors.FieldError;
import com.kyro.platform.common.errors.UnprocessableException;
import com.kyro.platform.common.security.CurrentActor;
import com.kyro.platform.education.application.port.EducationRequestSubmitted;
import com.kyro.platform.education.application.port.EducationRequestWithdrawn;
import com.kyro.platform.education.domain.EducationStore;
import com.kyro.platform.education.domain.FundingRequest;
import com.kyro.platform.education.domain.RequestFields;
import com.kyro.platform.education.domain.RequestStatus;
import com.kyro.platform.education.domain.RequestSummaryRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recipient-facing use cases for education requests. */
@Service
public class EducationRequestService {

    static final String RESOURCE = "education_request";
    static final String CURRENCY = "INR";
    static final int MIN_STORY_LENGTH = 50;

    /** Null fields mean "not provided" (unchanged on update). */
    public record RequestChanges(
            String title,
            String story,
            String expenseType,
            String institutionName,
            String institutionCity,
            String institutionCountry,
            String courseName,
            String feeReference,
            Long amountMinor,
            String currency,
            LocalDate dueDate,
            java.util.Set<String> clearFields) {

        public RequestChanges(
                String title, String story, String expenseType, String institutionName, String institutionCity,
                String institutionCountry, String courseName, String feeReference, Long amountMinor, String currency,
                LocalDate dueDate) {
            this(title, story, expenseType, institutionName, institutionCity, institutionCountry, courseName,
                    feeReference, amountMinor, currency, dueDate, java.util.Set.of());
        }
    }

    private final EducationStore store;
    private final RequestAccess access;
    private final RequestTransitions transitions;
    private final CurrentActor actor;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    EducationRequestService(
            EducationStore store,
            RequestAccess access,
            RequestTransitions transitions,
            CurrentActor actor,
            AuditService audit,
            ApplicationEventPublisher events,
            Clock clock) {
        this.store = store;
        this.access = access;
        this.transitions = transitions;
        this.actor = actor;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public RequestDetail create(RequestChanges c) {
        UUID userId = actor.require().userId();
        List<FieldError> errors = new ArrayList<>();
        if (c.expenseType() == null) {
            errors.add(new FieldError("expenseType", "is required"));
        }
        validateMoney(c.amountMinor(), c.currency(), errors);
        throwIfAny(errors);
        RequestFields f = new RequestFields(
                clean(c.title()),
                clean(c.story()),
                c.expenseType(),
                clean(c.institutionName()),
                clean(c.institutionCity()),
                c.institutionCountry() == null ? "IN" : c.institutionCountry(),
                clean(c.courseName()),
                clean(c.feeReference()),
                c.amountMinor(),
                c.currency(),
                c.dueDate());
        UUID id = store.insertRequest(userId, f);
        store.addHistory(id, null, RequestStatus.DRAFT, userId, null);
        audit.record(new AuditEntry(AuditActions.REQUEST_CREATED, "FUNDING_REQUEST", id, Map.of("expenseType", f.expenseType())));
        return detail(id);
    }

    @Transactional(readOnly = true)
    public RequestDetail get(UUID requestId) {
        access.loadOwned(requestId, false, RESOURCE);
        return detail(requestId);
    }

    @Transactional(readOnly = true)
    public List<RequestSummaryRow> listMine() {
        return store.listByRecipient(actor.require().userId());
    }

    @Transactional
    public RequestDetail update(UUID requestId, long expectedVersion, RequestChanges c) {
        FundingRequest r = access.loadOwned(requestId, true, RESOURCE);
        RequestAccess.requireEditable(r);
        if (r.version() != expectedVersion) {
            throw new ConflictException(
                    "STALE_VERSION", "The request was changed since you loaded it (current version " + r.version() + ").");
        }
        List<FieldError> errors = new ArrayList<>();
        long amount = c.amountMinor() != null ? c.amountMinor() : r.amountMinor();
        String currency = c.currency() != null ? c.currency() : r.currency();
        if (c.amountMinor() != null || c.currency() != null) {
            validateMoney(amount, currency, errors);
        }
        java.util.Set<String> clear = c.clearFields() == null ? java.util.Set.of() : c.clearFields();
        Map<String, Object> given = new LinkedHashMap<>();
        given.put("story", c.story());
        given.put("institutionName", c.institutionName());
        given.put("institutionCity", c.institutionCity());
        given.put("courseName", c.courseName());
        given.put("feeReference", c.feeReference());
        given.put("dueDate", c.dueDate());
        for (String field : clear) {
            if (given.get(field) != null) {
                errors.add(new FieldError(field, "cannot be both cleared and given a value"));
            }
        }
        throwIfAny(errors);
        RequestFields f = new RequestFields(
                c.title() != null ? clean(c.title()) : r.title(),
                clear.contains("story") ? null : c.story() != null ? clean(c.story()) : r.story(),
                c.expenseType() != null ? c.expenseType() : r.expenseType(),
                clear.contains("institutionName") ? null : c.institutionName() != null ? clean(c.institutionName()) : r.institutionName(),
                clear.contains("institutionCity") ? null : c.institutionCity() != null ? clean(c.institutionCity()) : r.institutionCity(),
                c.institutionCountry() != null ? c.institutionCountry() : r.institutionCountry(),
                clear.contains("courseName") ? null : c.courseName() != null ? clean(c.courseName()) : r.courseName(),
                clear.contains("feeReference") ? null : c.feeReference() != null ? clean(c.feeReference()) : r.feeReference(),
                amount,
                currency,
                clear.contains("dueDate") ? null : c.dueDate() != null ? c.dueDate() : r.dueDate());
        if (!store.updateFields(requestId, expectedVersion, f)) {
            throw new ConflictException("STALE_VERSION", "The request was changed concurrently; reload and retry.");
        }
        audit.record(new AuditEntry(
                AuditActions.REQUEST_UPDATED, "FUNDING_REQUEST", requestId, Map.of(
                        "changedFields", changedFields(r.fields(), f),
                        "clearedFields", clear.stream().sorted().toList())));
        return detail(requestId);
    }

    @Transactional
    public RequestDetail submit(UUID requestId) {
        FundingRequest r = access.loadOwned(requestId, true, RESOURCE);
        RequestAccess.requireTransition(r, RequestStatus.SUBMITTED);
        List<FieldError> errors = completenessErrors(r);
        if (!errors.isEmpty()) {
            throw new UnprocessableException("REQUEST_INCOMPLETE", "The request is incomplete and cannot be submitted.", errors);
        }
        Instant now = transitions.apply(r, RequestStatus.SUBMITTED, actor.require().userId(), null);
        events.publishEvent(new EducationRequestSubmitted(requestId, r.recipientUserId(), r.submissionCount() + 1, now));
        return detail(requestId);
    }

    @Transactional
    public RequestDetail withdraw(UUID requestId) {
        FundingRequest r = access.loadOwned(requestId, true, RESOURCE);
        Instant now = transitions.apply(r, RequestStatus.WITHDRAWN, actor.require().userId(), null);
        events.publishEvent(new EducationRequestWithdrawn(requestId, r.recipientUserId(), now));
        return detail(requestId);
    }

    RequestDetail detail(UUID requestId) {
        FundingRequest r = store.findRequest(requestId, false).orElseThrow();
        return new RequestDetail(r, store.documents(requestId), store.history(requestId));
    }

    private List<FieldError> completenessErrors(FundingRequest r) {
        List<FieldError> e = new ArrayList<>();
        if (blank(r.title())) {
            e.add(new FieldError("title", "is required"));
        }
        if (r.story() == null || r.story().trim().length() < MIN_STORY_LENGTH) {
            e.add(new FieldError("story", "must be at least " + MIN_STORY_LENGTH + " characters"));
        }
        if (blank(r.expenseType())) {
            e.add(new FieldError("expenseType", "is required"));
        }
        if (blank(r.institutionName())) {
            e.add(new FieldError("institutionName", "is required"));
        }
        if (blank(r.institutionCity())) {
            e.add(new FieldError("institutionCity", "is required"));
        }
        if (blank(r.courseName())) {
            e.add(new FieldError("courseName", "is required"));
        }
        if (blank(r.feeReference())) {
            e.add(new FieldError("feeReference", "is required"));
        }
        if (r.amountMinor() <= 0 || !CURRENCY.equals(r.currency())) {
            e.add(new FieldError("amount", "must be a positive amount in " + CURRENCY));
        }
        if (r.dueDate() == null) {
            e.add(new FieldError("dueDate", "is required"));
        } else if (r.dueDate().isBefore(LocalDate.now(clock))) {
            e.add(new FieldError("dueDate", "must not be in the past"));
        }
        if (store.countUploadedDocuments(r.id()) < 1) {
            e.add(new FieldError("documents", "at least one uploaded document is required"));
        }
        return e;
    }

    private static void validateMoney(Long amountMinor, String currency, List<FieldError> errors) {
        if (amountMinor == null || amountMinor <= 0) {
            errors.add(new FieldError("amount.amountMinor", "must be greater than 0"));
        }
        if (!CURRENCY.equals(currency)) {
            errors.add(new FieldError("amount.currency", "only " + CURRENCY + " is supported"));
        }
    }

    private static void throwIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new UnprocessableException("VALIDATION_FAILED", "One or more fields are invalid.", errors);
        }
    }

    private static List<String> changedFields(RequestFields a, RequestFields b) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put("title", !java.util.Objects.equals(a.title(), b.title()));
        m.put("story", !java.util.Objects.equals(a.story(), b.story()));
        m.put("expenseType", !java.util.Objects.equals(a.expenseType(), b.expenseType()));
        m.put("institutionName", !java.util.Objects.equals(a.institutionName(), b.institutionName()));
        m.put("institutionCity", !java.util.Objects.equals(a.institutionCity(), b.institutionCity()));
        m.put("institutionCountry", !java.util.Objects.equals(a.institutionCountry(), b.institutionCountry()));
        m.put("courseName", !java.util.Objects.equals(a.courseName(), b.courseName()));
        m.put("feeReference", !java.util.Objects.equals(a.feeReference(), b.feeReference()));
        m.put("amount", a.amountMinor() != b.amountMinor() || !a.currency().equals(b.currency()));
        m.put("dueDate", !java.util.Objects.equals(a.dueDate(), b.dueDate()));
        return m.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String clean(String s) {
        return s == null ? null : s.trim();
    }
}
