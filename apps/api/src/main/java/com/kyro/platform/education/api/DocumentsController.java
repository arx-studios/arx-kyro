package com.kyro.platform.education.api;

import com.kyro.platform.contract.api.DocumentsApi;
import com.kyro.platform.contract.model.DocumentUploadAuthorization;
import com.kyro.platform.contract.model.DocumentUploadRequest;
import com.kyro.platform.contract.model.RequestDocument;
import com.kyro.platform.contract.model.RequestDocumentList;
import com.kyro.platform.education.application.DocumentService;
import com.kyro.platform.education.application.DocumentService.UploadAuthorization;
import com.kyro.platform.education.application.DocumentService.UploadCommand;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DocumentsController implements DocumentsApi {

    private final DocumentService service;

    DocumentsController(DocumentService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<DocumentUploadAuthorization> createDocumentUploadUrl(UUID requestId, DocumentUploadRequest b) {
        UploadAuthorization a = service.authorizeUpload(
                requestId,
                new UploadCommand(b.getDocumentType().getValue(), b.getFileName(), b.getContentType().getValue(), b.getSizeBytes()));
        DocumentUploadAuthorization body = new DocumentUploadAuthorization(
                EducationMapper.toContract(a.document()),
                URI.create(a.upload().url()),
                DocumentUploadAuthorization.MethodEnum.PUT,
                a.upload().requiredHeaders(),
                EducationMapper.time(a.upload().expiresAt()));
        return ResponseEntity.created(URI.create("/api/v1/education/requests/" + requestId + "/documents/" + a.document().id()))
                .body(body);
    }

    @Override
    public ResponseEntity<RequestDocument> completeDocumentUpload(UUID requestId, UUID documentId) {
        return ResponseEntity.ok(EducationMapper.toContract(service.completeUpload(requestId, documentId)));
    }

    @Override
    public ResponseEntity<RequestDocumentList> listRequestDocuments(UUID requestId) {
        RequestDocumentList list = new RequestDocumentList();
        service.list(requestId).forEach(d -> list.addItemsItem(EducationMapper.toContract(d)));
        return ResponseEntity.ok(list);
    }

    @Override
    public ResponseEntity<Void> deleteRequestDocument(UUID requestId, UUID documentId) {
        service.delete(requestId, documentId);
        return ResponseEntity.noContent().build();
    }
}
