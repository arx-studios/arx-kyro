package com.kyro.platform.verification.application;

import com.kyro.platform.education.application.port.EducationRequestSubmitted;
import com.kyro.platform.education.application.port.EducationRequestWithdrawn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Synchronous listeners: they run inside the publisher's transaction, so a case is opened or closed atomically
 * with the submit or withdraw that caused it.
 */
@Component
class VerificationEventListener {

    private final VerificationCaseService service;

    VerificationEventListener(VerificationCaseService service) {
        this.service = service;
    }

    @EventListener
    void onSubmitted(EducationRequestSubmitted event) {
        service.openCase(event.requestId(), event.submissionNumber());
    }

    @EventListener
    void onWithdrawn(EducationRequestWithdrawn event) {
        service.closeForWithdrawal(event.requestId());
    }
}
