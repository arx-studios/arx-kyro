package com.kyro.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyro.platform.common.security.Role;
import com.kyro.platform.education.application.port.EducationRequestStatusChanged;
import com.kyro.platform.education.application.port.EducationRequestSubmitted;
import com.kyro.platform.identity.account.CapturingMailSenderConfig;
import com.kyro.platform.identity.account.CapturingMailSenderConfig.CapturingMailSender;
import com.kyro.platform.support.Csrf;
import com.kyro.platform.support.DbFixtures;
import com.kyro.platform.support.IntegrationTestBase;
import com.kyro.platform.support.TestUsers;
import com.kyro.platform.support.TestUsers.TestUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.support.TransactionTemplate;

@Import(CapturingMailSenderConfig.class)
class StatusEmailIT extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/verification/cases";
    private static final String RATIONALE = "INTERNAL-RATIONALE-xyz Fee reference does not match the invoice.";
    private static final String MESSAGE = "RECIPIENT-MESSAGE-abc please upload a clearer invoice.";

    @Autowired TestUsers users;
    @Autowired DbFixtures fixtures;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEventPublisher events;
    @Autowired CapturingMailSender mail;
    @Autowired TransactionTemplate tx;

    private UUID openCase(UUID recipientId, UUID requestId) {
        events.publishEvent(new EducationRequestSubmitted(requestId, recipientId, 1, Instant.now()));
        return jdbc.queryForObject("SELECT id FROM verification_cases WHERE request_id = ?", UUID.class, requestId);
    }

    private void decide(TestUser v, UUID caseId, String decision) throws Exception {
        mockMvc.perform(post(BASE + "/" + caseId + "/assign").session(v.session()).with(Csrf.token()))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/" + caseId + "/decision")
                        .session(v.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"" + decision + "\",\"rationale\":\"" + RATIONALE + "\",\"messageToRecipient\":\""
                                + MESSAGE + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void requestChangesSendsExactlyOneMinimalEmailAfterCommit() throws Exception {
        TestUser v = users.createActiveUser(Role.VERIFIER);
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        UUID requestId = fixtures.insertRequest(recipient.id(), "SUBMITTED");
        UUID caseId = openCase(recipient.id(), requestId);
        assertThat(mail.to(recipient.email())).isEmpty(); // submit sends nothing

        decide(v, caseId, "REQUEST_CHANGES");

        List<SimpleMailMessage> mails = mail.to(recipient.email());
        assertThat(mails).hasSize(1);
        String text = mails.get(0).getText();
        assertThat(text).contains("/recipient/requests/" + requestId).contains("changes are needed");
        assertThat(text).doesNotContain("INTERNAL-RATIONALE").doesNotContain("RECIPIENT-MESSAGE").doesNotContain("token=");
        assertThat(mail.to(v.email())).isEmpty();
    }

    @Test
    void approvalAndRejectionAreMailedButWithdrawAndSubmitAreNot() {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        UUID rid = fixtures.insertRequest(recipient.id(), "IN_REVIEW", "Title one", null, null);
        tx.executeWithoutResult(s -> events.publishEvent(
                new EducationRequestStatusChanged(rid, recipient.id(), "Title one", "IN_REVIEW", "APPROVED", Instant.now())));
        tx.executeWithoutResult(s -> events.publishEvent(
                new EducationRequestStatusChanged(rid, recipient.id(), "Title one", "IN_REVIEW", "REJECTED", Instant.now())));
        assertThat(mail.to(recipient.email())).hasSize(2);
        tx.executeWithoutResult(s -> events.publishEvent(
                new EducationRequestStatusChanged(rid, recipient.id(), "Title one", "DRAFT", "SUBMITTED", Instant.now())));
        tx.executeWithoutResult(s -> events.publishEvent(
                new EducationRequestStatusChanged(rid, recipient.id(), "Title one", "DRAFT", "WITHDRAWN", Instant.now())));
        assertThat(mail.to(recipient.email())).hasSize(2);
    }

    @Test
    void rolledBackTransactionSendsNothing() {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        UUID rid = fixtures.insertRequest(recipient.id(), "IN_REVIEW");
        try {
            tx.executeWithoutResult(s -> {
                events.publishEvent(new EducationRequestStatusChanged(
                        rid, recipient.id(), "T", "IN_REVIEW", "APPROVED", Instant.now()));
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // rolled back
        }
        assertThat(mail.to(recipient.email())).isEmpty();
    }

    @Test
    void mailFailuresAreSwallowed() {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        UUID rid = fixtures.insertRequest(recipient.id(), "IN_REVIEW");
        mail.failing(true);
        try {
            tx.executeWithoutResult(s -> events.publishEvent(
                    new EducationRequestStatusChanged(rid, recipient.id(), "T", "IN_REVIEW", "APPROVED", Instant.now())));
        } finally {
            mail.failing(false);
        }
        assertThat(mail.to(recipient.email())).isEmpty();
    }
}
