package com.kyro.platform.education.api;

import com.kyro.platform.contract.api.EducationRequestsApi;
import com.kyro.platform.contract.model.EducationRequest;
import com.kyro.platform.contract.model.EducationRequestCreate;
import com.kyro.platform.contract.model.EducationRequestSummaryList;
import com.kyro.platform.contract.model.EducationRequestUpdate;
import com.kyro.platform.education.application.EducationRequestService;
import com.kyro.platform.education.application.EducationRequestService.RequestChanges;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class EducationRequestsController implements EducationRequestsApi {

    private final EducationRequestService service;

    EducationRequestsController(EducationRequestService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<EducationRequest> createEducationRequest(EducationRequestCreate b) {
        EducationRequest created = EducationMapper.toContract(service.create(new RequestChanges(
                b.getTitle(),
                b.getStory(),
                b.getExpenseType() == null ? null : b.getExpenseType().getValue(),
                b.getInstitutionName(),
                b.getInstitutionCity(),
                b.getInstitutionCountry(),
                b.getCourseName(),
                b.getFeeReference(),
                b.getAmount().getAmountMinor(),
                b.getAmount().getCurrency(),
                b.getDueDate())));
        return ResponseEntity.created(URI.create("/api/v1/education/requests/" + created.getId())).body(created);
    }

    @Override
    public ResponseEntity<EducationRequestSummaryList> listMyEducationRequests() {
        EducationRequestSummaryList list = new EducationRequestSummaryList();
        service.listMine().forEach(s -> list.addItemsItem(EducationMapper.toContract(s)));
        return ResponseEntity.ok(list);
    }

    @Override
    public ResponseEntity<EducationRequest> getEducationRequest(UUID requestId) {
        return ResponseEntity.ok(EducationMapper.toContract(service.get(requestId)));
    }

    @Override
    public ResponseEntity<EducationRequest> updateEducationRequest(UUID requestId, EducationRequestUpdate b) {
        return ResponseEntity.ok(EducationMapper.toContract(service.update(
                requestId,
                b.getVersion(),
                new RequestChanges(
                        b.getTitle(),
                        b.getStory(),
                        b.getExpenseType() == null ? null : b.getExpenseType().getValue(),
                        b.getInstitutionName(),
                        b.getInstitutionCity(),
                        b.getInstitutionCountry(),
                        b.getCourseName(),
                        b.getFeeReference(),
                        b.getAmount() == null ? null : b.getAmount().getAmountMinor(),
                        b.getAmount() == null ? null : b.getAmount().getCurrency(),
                        b.getDueDate()))));
    }

    @Override
    public ResponseEntity<EducationRequest> submitEducationRequest(UUID requestId) {
        return ResponseEntity.ok(EducationMapper.toContract(service.submit(requestId)));
    }

    @Override
    public ResponseEntity<EducationRequest> withdrawEducationRequest(UUID requestId) {
        return ResponseEntity.ok(EducationMapper.toContract(service.withdraw(requestId)));
    }
}
