package com.kyro.platform.verification.api;

import com.kyro.platform.common.security.CurrentActor;
import com.kyro.platform.common.storage.PresignedUrl;
import com.kyro.platform.contract.api.VerificationApi;
import com.kyro.platform.contract.model.DecisionInput;
import com.kyro.platform.contract.model.DocumentViewUrl;
import com.kyro.platform.contract.model.VerificationCaseDetail;
import com.kyro.platform.contract.model.VerificationCasePage;
import com.kyro.platform.verification.application.Queue;
import com.kyro.platform.verification.application.VerificationCaseService;
import com.kyro.platform.verification.application.VerificationViews.CasePage;
import com.kyro.platform.verification.application.VerificationViews.DecisionCommand;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class VerificationController implements VerificationApi {

    private final VerificationCaseService service;
    private final CurrentActor actor;

    VerificationController(VerificationCaseService service, CurrentActor actor) {
        this.service = service;
        this.actor = actor;
    }

    @Override
    public ResponseEntity<VerificationCasePage> listVerificationCases(String queue, String cursor, Integer limit) {
        CasePage page = service.list(Queue.parse(queue), cursor, limit == null ? 20 : limit, actor.require());
        return ResponseEntity.ok(VerificationMapper.toPage(page));
    }

    @Override
    public ResponseEntity<VerificationCaseDetail> getVerificationCase(UUID caseId) {
        return ResponseEntity.ok(VerificationMapper.toDetail(service.get(caseId, actor.require())));
    }

    @Override
    public ResponseEntity<VerificationCaseDetail> assignVerificationCase(UUID caseId) {
        return ResponseEntity.ok(VerificationMapper.toDetail(service.assign(caseId, actor.require())));
    }

    @Override
    public ResponseEntity<VerificationCaseDetail> decideVerificationCase(UUID caseId, DecisionInput input) {
        DecisionCommand command =
                new DecisionCommand(input.getDecision().getValue(), input.getRationale(), input.getMessageToRecipient());
        return ResponseEntity.ok(VerificationMapper.toDetail(service.decide(caseId, command, actor.require())));
    }

    @Override
    public ResponseEntity<DocumentViewUrl> createDocumentViewUrl(UUID caseId, UUID documentId) {
        PresignedUrl url = service.issueDocumentViewUrl(caseId, documentId, actor.require());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new DocumentViewUrl(URI.create(url.url()), url.expiresAt().atOffset(ZoneOffset.UTC)));
    }
}
