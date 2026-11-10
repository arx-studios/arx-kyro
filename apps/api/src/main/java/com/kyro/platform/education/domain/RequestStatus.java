package com.kyro.platform.education.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Request lifecycle (PLAN section 8). Names equal the contract RequestStatus values. */
public enum RequestStatus {
    DRAFT,
    SUBMITTED,
    IN_REVIEW,
    CHANGES_REQUESTED,
    APPROVED,
    REJECTED,
    WITHDRAWN,
    FUNDED,
    CLOSED;

    private static final Map<RequestStatus, Set<RequestStatus>> ALLOWED = new EnumMap<>(RequestStatus.class);

    static {
        ALLOWED.put(DRAFT, EnumSet.of(SUBMITTED, WITHDRAWN));
        ALLOWED.put(SUBMITTED, EnumSet.of(IN_REVIEW, WITHDRAWN));
        ALLOWED.put(IN_REVIEW, EnumSet.of(APPROVED, REJECTED, CHANGES_REQUESTED, WITHDRAWN));
        ALLOWED.put(CHANGES_REQUESTED, EnumSet.of(SUBMITTED, WITHDRAWN));
        ALLOWED.put(APPROVED, EnumSet.of(WITHDRAWN, FUNDED));
        ALLOWED.put(FUNDED, EnumSet.of(CLOSED));
        ALLOWED.put(REJECTED, EnumSet.noneOf(RequestStatus.class));
        ALLOWED.put(WITHDRAWN, EnumSet.noneOf(RequestStatus.class));
        ALLOWED.put(CLOSED, EnumSet.noneOf(RequestStatus.class));
    }

    public boolean canTransitionTo(RequestStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** Only DRAFT and CHANGES_REQUESTED accept edits and document changes; an APPROVED request can never be edited. */
    public boolean isEditable() {
        return this == DRAFT || this == CHANGES_REQUESTED;
    }
}
