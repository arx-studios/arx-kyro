package com.kyro.platform.operations.api;

import com.kyro.platform.common.security.CurrentActor;
import com.kyro.platform.contract.api.ReportsApi;
import com.kyro.platform.contract.model.ReportCreate;
import com.kyro.platform.contract.model.ReportReceipt;
import com.kyro.platform.contract.model.ReportStatus;
import com.kyro.platform.operations.application.ReportRepository;
import com.kyro.platform.operations.application.ReportService;
import java.time.ZoneOffset;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReportsController implements ReportsApi {

    private final ReportService service;
    private final CurrentActor actor;

    ReportsController(ReportService service, CurrentActor actor) {
        this.service = service;
        this.actor = actor;
    }

    @Override
    public ResponseEntity<ReportReceipt> createReport(ReportCreate input) {
        ReportRepository.Inserted r =
                service.create(input.getRequestId(), input.getReason().getValue(), input.getDetails(), actor.require());
        return ResponseEntity.accepted()
                .body(new ReportReceipt(r.id(), ReportStatus.OPEN, r.createdAt().atOffset(ZoneOffset.UTC)));
    }
}
