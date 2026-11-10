package com.kyro.platform.education;

import static org.assertj.core.api.Assertions.assertThat;

import com.kyro.platform.education.domain.FeeReference;
import com.kyro.platform.education.domain.RequestStatus;
import org.junit.jupiter.api.Test;

class RequestStatusTest {

    @Test
    void lifecycleTransitionsFollowThePlan() {
        assertThat(RequestStatus.DRAFT.canTransitionTo(RequestStatus.SUBMITTED)).isTrue();
        assertThat(RequestStatus.DRAFT.canTransitionTo(RequestStatus.APPROVED)).isFalse();
        assertThat(RequestStatus.SUBMITTED.canTransitionTo(RequestStatus.IN_REVIEW)).isTrue();
        assertThat(RequestStatus.IN_REVIEW.canTransitionTo(RequestStatus.APPROVED)).isTrue();
        assertThat(RequestStatus.IN_REVIEW.canTransitionTo(RequestStatus.CHANGES_REQUESTED)).isTrue();
        assertThat(RequestStatus.CHANGES_REQUESTED.canTransitionTo(RequestStatus.SUBMITTED)).isTrue();
        assertThat(RequestStatus.APPROVED.canTransitionTo(RequestStatus.WITHDRAWN)).isTrue();
        assertThat(RequestStatus.APPROVED.canTransitionTo(RequestStatus.DRAFT)).isFalse();
        assertThat(RequestStatus.REJECTED.canTransitionTo(RequestStatus.SUBMITTED)).isFalse();
        assertThat(RequestStatus.WITHDRAWN.canTransitionTo(RequestStatus.DRAFT)).isFalse();
    }

    @Test
    void onlyDraftAndChangesRequestedAreEditable() {
        for (RequestStatus s : RequestStatus.values()) {
            assertThat(s.isEditable()).isEqualTo(s == RequestStatus.DRAFT || s == RequestStatus.CHANGES_REQUESTED);
        }
    }

    @Test
    void feeReferenceNormalization() {
        assertThat(FeeReference.normalize(" inv-2026/001 ")).isEqualTo("INV2026001");
        assertThat(FeeReference.normalize("---")).isNull();
        assertThat(FeeReference.normalize(null)).isNull();
    }
}
