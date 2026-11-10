package com.kyro.platform.identity.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// FROZEN CONTRACT: change signatures only with a DECISIONS.md entry.
/** Read-only lookup of user display data for other modules (verification, audit). */
public interface UserDirectory {

    /** Summaries keyed by user id; unknown ids are absent from the map. */
    Map<UUID, UserSummaryView> findSummaries(Collection<UUID> ids);

    /** Recipient details for the (staff-only) case review view. */
    Optional<ReviewRecipientView> findRecipient(UUID userId);
}
