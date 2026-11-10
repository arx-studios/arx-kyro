package com.kyro.platform.operations.api;

import com.kyro.platform.common.security.CurrentActor;
import com.kyro.platform.contract.api.OperationsApi;
import com.kyro.platform.contract.model.AdminDashboard;
import com.kyro.platform.contract.model.ReportDetail;
import com.kyro.platform.contract.model.ReportPage;
import com.kyro.platform.contract.model.ReportQueue;
import com.kyro.platform.contract.model.ReportResolutionInput;
import com.kyro.platform.operations.application.OperationsViews.ResolveCommand;
import com.kyro.platform.operations.application.ReportService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OperationsController implements OperationsApi {

    private final ReportService service;
    private final CurrentActor actor;

    OperationsController(ReportService service, CurrentActor actor) {
        this.service = service;
        this.actor = actor;
    }

    @Override
    public ResponseEntity<ReportPage> listReports(ReportQueue queue, String cursor, Integer limit) {
        var page = service.list(
                com.kyro.platform.operations.application.ReportQueue.parse(queue == null ? null : queue.getValue()),
                cursor,
                limit == null ? 20 : limit,
                actor.require());
        return ResponseEntity.ok(ReportMapper.toPage(page));
    }

    @Override
    public ResponseEntity<ReportDetail> getReport(UUID reportId) {
        return ResponseEntity.ok(ReportMapper.toDetail(service.get(reportId, actor.require())));
    }

    @Override
    public ResponseEntity<ReportDetail> assignReport(UUID reportId) {
        return ResponseEntity.ok(ReportMapper.toDetail(service.assign(reportId, actor.require())));
    }

    @Override
    public ResponseEntity<ReportDetail> resolveReport(UUID reportId, ReportResolutionInput input) {
        ResolveCommand cmd = new ResolveCommand(
                input.getResolution().getValue(), input.getNote(), Boolean.TRUE.equals(input.getHoldRequest()));
        return ResponseEntity.ok(ReportMapper.toDetail(service.resolve(reportId, cmd, actor.require())));
    }

    @Override
    public ResponseEntity<Void> releaseRequestHold(UUID requestId) {
        service.releaseHold(requestId, actor.require());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AdminDashboard> getAdminDashboard() {
        return ResponseEntity.ok(ReportMapper.toDashboard(service.dashboard(actor.require())));
    }
}
