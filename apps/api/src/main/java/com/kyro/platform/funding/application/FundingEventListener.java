package com.kyro.platform.funding.application;

import com.kyro.platform.education.application.port.EducationRequestWithdrawn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Synchronous listener: runs inside the education withdraw transaction. */
@Component
class FundingEventListener {

    private final PledgeService service;

    FundingEventListener(PledgeService service) {
        this.service = service;
    }

    @EventListener
    void onWithdrawn(EducationRequestWithdrawn event) {
        service.cancelAllForWithdrawnRequest(event.requestId());
    }
}
