package com.kyro.platform.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.errors.ApiException;
import com.kyro.platform.common.errors.ConflictException;
import com.kyro.platform.common.errors.ForbiddenException;
import com.kyro.platform.common.errors.NotFoundException;
import com.kyro.platform.common.errors.UnprocessableException;
import com.kyro.platform.common.security.KyroPrincipal;
import com.kyro.platform.common.security.Role;
import com.kyro.platform.education.application.port.EducationReviewFacade;
import com.kyro.platform.education.application.port.NoOpEducationReviewFacade;
import com.kyro.platform.education.application.port.ReviewSnapshot;
import com.kyro.platform.education.application.port.ReviewSummary;
import com.kyro.platform.education.application.port.VerificationOutcome;
import com.kyro.platform.identity.application.UserDirectory;
import com.kyro.platform.verification.application.VerificationViews.DecisionCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Guards of the verification workflow with every collaborator mocked (no Spring, no database). */
class VerificationCaseServiceTest {

    static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    CaseRepository cases = mock(CaseRepository.class);
    EducationReviewFacade education = mock(EducationReviewFacade.class);
    UserDirectory users = mock(UserDirectory.class);
    AuditService audit = mock(AuditService.class);
    VerificationCaseService service =
            new VerificationCaseService(cases, education, users, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    UUID caseId = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    UUID recipientId = UUID.randomUUID();
    KyroPrincipal verifier = principal();
    KyroPrincipal otherVerifier = principal();

    static KyroPrincipal principal() {
        return new KyroPrincipal(UUID.randomUUID(), "v@kyro.local", Set.of(Role.VERIFIER));
    }

    CaseRow row(String status, UUID assignedTo) {
        return new CaseRow(caseId, requestId, 1, status, null, assignedTo, null, NOW, null, 0);
    }

    @BeforeEach
    void setUp() {
        when(education.getReviewSummaries(any()))
                .thenReturn(Map.of(requestId, new ReviewSummary(requestId, recipientId, "T", "Name", 100, "INR")));
        when(cases.decisionsForRequest(any())).thenReturn(List.of());
        when(users.findSummaries(any())).thenReturn(Map.of());
        when(users.findRecipient(any())).thenReturn(Optional.empty());
        when(education.getReviewSnapshot(requestId)).thenReturn(snapshot());
    }

    ReviewSnapshot snapshot() {
        return new ReviewSnapshot(
                requestId, recipientId, "IN_REVIEW", "T", null, "TUITION", null, null, null, null, null, 100, "INR", null, 1,
                "Name", false, null, null, null, NOW, NOW, 0, List.of(), List.of(), List.of());
    }

    // ---- assign ----

    @Test
    void assignClaimsAndMarksRequestInReview() {
        when(cases.find(caseId)).thenReturn(Optional.of(row("OPEN", null)));
        when(cases.claim(caseId, verifier.userId(), NOW)).thenReturn(true);

        service.assign(caseId, verifier);

        verify(education).markInReview(requestId, verifier.userId());
        ArgumentCaptor<AuditEntry> audited = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(audited.capture());
        assertThat(audited.getValue().action()).isEqualTo(AuditActions.CASE_ASSIGNED);
    }

    @Test
    void assignByRequestOwnerIsForbiddenAndNothingIsWritten() {
        when(cases.find(caseId)).thenReturn(Optional.of(row("OPEN", null)));
        KyroPrincipal owner = new KyroPrincipal(recipientId, "o@kyro.local", Set.of(Role.VERIFIER, Role.RECIPIENT));

        assertThatThrownBy(() -> service.assign(caseId, owner))
                .isInstanceOfSatisfying(ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("SELF_REVIEW_FORBIDDEN"));

        verify(cases, never()).claim(any(), any(), any());
        verify(education, never()).markInReview(any(), any());
        ArgumentCaptor<AuditEntry> audited = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(audited.capture());
        assertThat(audited.getValue().action()).isEqualTo(AuditActions.ACCESS_DENIED);
    }

    @Test
    void assignOfCaseHeldByAnotherVerifierConflicts() {
        when(cases.find(caseId)).thenReturn(Optional.of(row("ASSIGNED", otherVerifier.userId())));

        assertThatThrownBy(() -> service.assign(caseId, verifier)).isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("CASE_ALREADY_ASSIGNED"));
        verify(cases, never()).claim(any(), any(), any());
    }

    @Test
    void assignLosingTheRaceConflicts() {
        when(cases.find(caseId)).thenReturn(Optional.of(row("OPEN", null)));
        when(cases.claim(caseId, verifier.userId(), NOW)).thenReturn(false);

        assertThatThrownBy(() -> service.assign(caseId, verifier)).isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("CASE_ALREADY_ASSIGNED"));
        verify(education, never()).markInReview(any(), any());
    }

    @Test
    void assignOfClosedCaseIsInvalidTransition() {
        when(cases.find(caseId)).thenReturn(Optional.of(row("CLOSED", verifier.userId())));

        assertThatThrownBy(() -> service.assign(caseId, verifier)).isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_STATE_TRANSITION"));
    }

    @Test
    void unknownCaseIsNotFound() {
        when(cases.find(caseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(caseId, verifier)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(caseId, verifier)).isInstanceOf(NotFoundException.class);
    }

    // ---- decide ----

    DecisionCommand approve() {
        return new DecisionCommand("APPROVE", "Everything checks out fine.", null);
    }

    @Test
    void decideRecordsClosesAppliesAndAudits() {
        when(cases.find(caseId)).thenReturn(Optional.of(row("ASSIGNED", verifier.userId())));
        when(cases.closeAssigned(caseId, verifier.userId(), "APPROVED", NOW)).thenReturn(true);

        service.decide(caseId, approve(), verifier);

        verify(cases).insertDecision(caseId, verifier.userId(), "APPROVE", "Everything checks out fine.", null, NOW);
        verify(education).applyDecision(requestId, VerificationOutcome.APPROVED, verifier.userId(), null);
        ArgumentCaptor<AuditEntry> audited = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit, org.mockito.Mockito.times(2)).record(audited.capture());
        assertThat(audited.getAllValues()).extracting(AuditEntry::action)
                .containsExactly(AuditActions.VERIFICATION_DECIDED, AuditActions.CASE_CLOSED);
        assertThat(audited.getAllValues().get(0).details()).containsEntry("decision", "APPROVE").containsEntry("caseId", caseId.toString());
    }

    @Test
    void decideMapsEachDecisionToItsOutcome() {
        when(cases.find(caseId)).thenReturn(Optional.of(row("ASSIGNED", verifier.userId())));
        when(cases.closeAssigned(any(), any(), anyString(), any())).thenReturn(true);

        service.decide(caseId, new DecisionCommand("REJECT", "Not a genuine fee demand.", "Sorry."), verifier);
        verify(education).applyDecision(requestId, VerificationOutcome.REJECTED, verifier.userId(), "Sorry.");
        service.decide(caseId, new DecisionCommand("REQUEST_CHANGES", "Invoice unreadable here.", "Re-upload."), verifier);
        verify(education).applyDecision(requestId, VerificationOutcome.CHANGES_REQUESTED, verifier.userId(), "Re-upload.");
    }

    @Test
    void decideByNonAssigneeConflicts() {
        when(cases.find(caseId)).thenReturn(Optional.of(row("ASSIGNED", otherVerifier.userId())));
        assertThatThrownBy(() -> service.decide(caseId, approve(), verifier)).isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("CASE_NOT_ASSIGNED_TO_YOU"));

        when(cases.find(caseId)).thenReturn(Optional.of(row("OPEN", null)));
        assertThatThrownBy(() -> service.decide(caseId, approve(), verifier)).isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("CASE_NOT_ASSIGNED_TO_YOU"));

        verify(cases, never()).insertDecision(any(), any(), any(), any(), any(), any());
        verify(education, never()).applyDecision(any(), any(), any(), any());
    }

    @Test
    void decideOnClosedCaseIsInvalidTransition() {
        when(cases.find(caseId)).thenReturn(Optional.of(row("CLOSED", verifier.userId())));
        assertThatThrownBy(() -> service.decide(caseId, approve(), verifier)).isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_STATE_TRANSITION"));
    }

    @Test
    void decideByRequestOwnerIsForbiddenEvenWhenAssigned() {
        KyroPrincipal owner = new KyroPrincipal(recipientId, "o@kyro.local", Set.of(Role.VERIFIER, Role.RECIPIENT));
        when(cases.find(caseId)).thenReturn(Optional.of(row("ASSIGNED", recipientId)));

        assertThatThrownBy(() -> service.decide(caseId, approve(), owner))
                .isInstanceOfSatisfying(ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("SELF_REVIEW_FORBIDDEN"));
        verify(cases, never()).closeAssigned(any(), any(), any(), any());
        verify(education, never()).applyDecision(any(), any(), any(), any());
    }

    @Test
    void rejectAndRequestChangesNeedAMessage() {
        when(cases.find(caseId)).thenReturn(Optional.of(row("ASSIGNED", verifier.userId())));
        for (String decision : List.of("REJECT", "REQUEST_CHANGES")) {
            for (String message : new String[] {null, "", "   "}) {
                assertThatThrownBy(() -> service.decide(
                                caseId, new DecisionCommand(decision, "A perfectly fine rationale.", message), verifier))
                        .isInstanceOfSatisfying(UnprocessableException.class, e -> {
                            assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
                            assertThat(e.getErrors()).anyMatch(f -> f.field().equals("messageToRecipient"));
                        });
            }
        }
        verify(cases, never()).closeAssigned(any(), any(), any(), any());
    }

    @Test
    void rationaleBoundsAreEnforced() {
        when(cases.find(caseId)).thenReturn(Optional.of(row("ASSIGNED", verifier.userId())));
        for (String rationale : new String[] {null, "", "too short", "          ", "x".repeat(2001)}) {
            assertThatThrownBy(() -> service.decide(caseId, new DecisionCommand("APPROVE", rationale, null), verifier))
                    .isInstanceOfSatisfying(UnprocessableException.class, e -> assertThat(e.getErrors()).anyMatch(f -> f.field().equals("rationale")));
        }
    }

    @Test
    void failureOfTheEducationSideBubblesUpSoTheTransactionRollsBack() {
        when(cases.find(caseId)).thenReturn(Optional.of(row("ASSIGNED", verifier.userId())));
        when(cases.closeAssigned(any(), any(), anyString(), any())).thenReturn(true);
        org.mockito.Mockito.doThrow(new ConflictException("INVALID_STATE_TRANSITION", "no"))
                .when(education).applyDecision(any(), any(), any(), any());

        assertThatThrownBy(() -> service.decide(caseId, approve(), verifier)).isInstanceOf(ConflictException.class);
        verify(audit, never()).record(any());
    }

    // ---- view-url ----

    @Test
    void viewUrlDelegatesToFacadeWithTheCasesRequest() {
        UUID docId = UUID.randomUUID();
        var url = new com.kyro.platform.common.storage.PresignedUrl("http://x/y", Map.of(), NOW.plusSeconds(300));
        when(cases.find(caseId)).thenReturn(Optional.of(row("ASSIGNED", otherVerifier.userId())));
        when(education.issueDocumentViewUrl(requestId, docId, verifier.userId())).thenReturn(url);

        assertThat(service.issueDocumentViewUrl(caseId, docId, verifier)).isSameAs(url);
    }

    @Test
    void viewUrlOfDocumentNotOnTheRequestIsNotFound() {
        when(cases.find(caseId)).thenReturn(Optional.of(row("OPEN", null)));
        VerificationCaseService withNoOp = new VerificationCaseService(
                cases, new NoOpEducationReviewFacade(), users, audit, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> withNoOp.issueDocumentViewUrl(caseId, UUID.randomUUID(), verifier))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- events ----

    @Test
    void openCaseAuditsOnlyWhenACaseWasCreated() {
        when(cases.insertOpen(requestId, 1, NOW)).thenReturn(Optional.of(caseId));
        service.openCase(requestId, 1);
        verify(audit).record(any());

        when(cases.insertOpen(requestId, 1, NOW)).thenReturn(Optional.empty());
        service.openCase(requestId, 1);
        verify(audit, org.mockito.Mockito.times(1)).record(any());
    }

    @Test
    void withdrawalClosesCasesWithOutcomeWithdrawn() {
        when(cases.closeUnclosedForRequest(requestId, "WITHDRAWN", NOW)).thenReturn(List.of(caseId));
        service.closeForWithdrawal(requestId);

        ArgumentCaptor<AuditEntry> audited = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(audited.capture());
        assertThat(audited.getValue().action()).isEqualTo(AuditActions.CASE_CLOSED);
        assertThat(audited.getValue().details()).containsEntry("outcome", "WITHDRAWN");
    }

    // ---- queue / canDecide ----

    @Test
    void canDecideIsTrueOnlyForTheAssigneeOfAnAssignedCase() {
        when(cases.find(caseId)).thenReturn(Optional.of(row("ASSIGNED", verifier.userId())));
        assertThat(service.get(caseId, verifier).canDecide()).isTrue();
        assertThat(service.get(caseId, otherVerifier).canDecide()).isFalse();

        when(cases.find(caseId)).thenReturn(Optional.of(row("OPEN", null)));
        assertThat(service.get(caseId, verifier).canDecide()).isFalse();
    }

    @Test
    void invalidQueueAndCursorAreBadRequests() {
        assertThatThrownBy(() -> Queue.parse("BOGUS")).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(400));
        assertThatThrownBy(() -> service.list(Queue.ALL, "%%%", 10, verifier)).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(400));
        assertThat(Queue.parse(null)).isEqualTo(Queue.UNASSIGNED);
    }

    @Test
    void listFetchesOneExtraRowToProduceNextCursor() {
        CaseRow a = new CaseRow(UUID.randomUUID(), requestId, 1, "OPEN", null, null, null, NOW, null, 0);
        CaseRow b = new CaseRow(UUID.randomUUID(), requestId, 2, "OPEN", null, null, null, NOW.plusSeconds(1), null, 0);
        when(cases.page(eq(Queue.UNASSIGNED), any(), any(), any(), eq(2))).thenReturn(List.of(a, b));

        var page = service.list(Queue.UNASSIGNED, null, 1, verifier);

        assertThat(page.items()).hasSize(1);
        assertThat(page.nextCursor()).isNotBlank();
    }
}
