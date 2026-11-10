package com.kyro.platform.education.application;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.config.KyroProperties;
import com.kyro.platform.common.errors.ConflictException;
import com.kyro.platform.common.errors.FieldError;
import com.kyro.platform.common.errors.NotFoundException;
import com.kyro.platform.common.errors.UnprocessableException;
import com.kyro.platform.common.security.CurrentActor;
import com.kyro.platform.common.storage.ObjectStorage;
import com.kyro.platform.common.storage.PresignedUrl;
import com.kyro.platform.common.storage.StoredObjectMetadata;
import com.kyro.platform.education.domain.EducationStore;
import com.kyro.platform.education.domain.FundingRequest;
import com.kyro.platform.education.domain.RequestDocument;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Document upload flow: authorize (presigned PUT), complete (verify and hash), list, delete. */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final String ENTITY = "REQUEST_DOCUMENT";

    public record UploadCommand(String documentType, String fileName, String contentType, long sizeBytes) {}

    public record UploadAuthorization(RequestDocument document, PresignedUrl upload) {}

    private final EducationStore store;
    private final RequestAccess access;
    private final ObjectStorage storage;
    private final KyroProperties props;
    private final AuditService audit;
    private final CurrentActor actor;
    private final Clock clock;
    private final TransactionTemplate tx;

    DocumentService(
            EducationStore store,
            RequestAccess access,
            ObjectStorage storage,
            KyroProperties props,
            AuditService audit,
            CurrentActor actor,
            Clock clock,
            PlatformTransactionManager txManager) {
        this.store = store;
        this.access = access;
        this.storage = storage;
        this.props = props;
        this.audit = audit;
        this.actor = actor;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    @Transactional
    public UploadAuthorization authorizeUpload(UUID requestId, UploadCommand cmd) {
        FundingRequest r = access.loadOwned(requestId, true, EducationRequestService.RESOURCE);
        RequestAccess.requireEditable(r);
        KyroProperties.Documents limits = props.documents();
        if (!limits.allowedContentTypes().contains(cmd.contentType())) {
            throw new UnprocessableException(
                    "DOCUMENT_INVALID", "Unsupported file type.", List.of(new FieldError("contentType", "must be PDF, JPEG or PNG")));
        }
        if (cmd.sizeBytes() < 1 || cmd.sizeBytes() > limits.maxSizeBytes()) {
            throw new UnprocessableException(
                    "DOCUMENT_INVALID",
                    "File size out of range.",
                    List.of(new FieldError("sizeBytes", "must be between 1 and " + limits.maxSizeBytes() + " bytes")));
        }
        if (store.countActiveDocuments(requestId) >= limits.maxPerRequest()) {
            throw new UnprocessableException(
                    "DOCUMENT_LIMIT_EXCEEDED", "A request can have at most " + limits.maxPerRequest() + " documents.");
        }
        UUID docId = UUID.randomUUID();
        String key = "requests/" + requestId + "/documents/" + docId;
        store.insertDocument(new RequestDocument(
                docId, requestId, actor.require().userId(), cmd.documentType(), key, cmd.fileName(), cmd.contentType(),
                cmd.sizeBytes(), null, RequestDocument.PENDING_UPLOAD, null, null));
        PresignedUrl url = storage.presignPut(key, cmd.contentType(), cmd.sizeBytes(), limits.uploadUrlTtl());
        audit.record(new AuditEntry(
                AuditActions.DOCUMENT_UPLOAD_AUTHORIZED,
                ENTITY,
                docId,
                Map.of("requestId", requestId.toString(), "contentType", cmd.contentType(), "sizeBytes", cmd.sizeBytes())));
        return new UploadAuthorization(store.findDocument(requestId, docId, false).orElseThrow(), url);
    }

    public RequestDocument completeUpload(UUID requestId, UUID documentId) {
        // 1. authorize and load (own transaction; denial audit is independent of it)
        RequestDocument doc = tx.execute(status -> {
            FundingRequest r = access.loadOwned(requestId, false, EducationRequestService.RESOURCE);
            RequestDocument d = store.findDocument(requestId, documentId, false).orElseThrow(NotFoundException::new);
            if (RequestDocument.UPLOADED.equals(d.status())) {
                return d;
            }
            if (!RequestDocument.PENDING_UPLOAD.equals(d.status())) {
                throw new ConflictException("INVALID_STATE_TRANSITION", "The document is " + d.status() + ".");
            }
            RequestAccess.requireEditable(r);
            return d;
        });
        if (RequestDocument.UPLOADED.equals(doc.status())) {
            return doc;
        }
        // 2. inspect the stored object outside any transaction (I/O)
        Inspection inspection = inspect(doc);
        // 3. record the outcome (committed even when we then answer 422)
        RequestDocument result = tx.execute(status -> {
            RequestDocument d = store.findDocument(requestId, documentId, true).orElseThrow(NotFoundException::new);
            if (!RequestDocument.PENDING_UPLOAD.equals(d.status())) {
                return d;
            }
            if (inspection.error() == null) {
                store.markUploaded(documentId, inspection.sha256(), Instant.now(clock));
                audit.record(new AuditEntry(
                        AuditActions.DOCUMENT_UPLOADED,
                        ENTITY,
                        documentId,
                        Map.of("requestId", requestId.toString(), "sha256", inspection.sha256(), "sizeBytes", d.sizeBytes())));
            } else {
                store.markRejected(documentId);
                audit.record(new AuditEntry(
                        AuditActions.DOCUMENT_REJECTED,
                        ENTITY,
                        documentId,
                        Map.of("requestId", requestId.toString(), "reason", inspection.error())));
            }
            return store.findDocument(requestId, documentId, false).orElseThrow();
        });
        if (inspection.error() != null) {
            deleteObjectQuietly(doc.storageKey());
            throw new UnprocessableException(
                    "DOCUMENT_INVALID", inspection.error(), List.of(new FieldError("document", inspection.error())));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<RequestDocument> list(UUID requestId) {
        access.loadOwned(requestId, false, EducationRequestService.RESOURCE);
        return store.documents(requestId);
    }

    @Transactional
    public void delete(UUID requestId, UUID documentId) {
        FundingRequest r = access.loadOwned(requestId, true, EducationRequestService.RESOURCE);
        RequestAccess.requireEditable(r);
        RequestDocument d = store.findDocument(requestId, documentId, true).orElseThrow(NotFoundException::new);
        store.markDeleted(documentId, Instant.now(clock));
        audit.record(new AuditEntry(AuditActions.DOCUMENT_DELETED, ENTITY, documentId, Map.of("requestId", requestId.toString())));
        String key = d.storageKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteObjectQuietly(key);
            }
        });
    }

    // ---- object inspection ----

    private record Inspection(String sha256, String error) {}

    private Inspection inspect(RequestDocument doc) {
        Optional<StoredObjectMetadata> head = storage.head(doc.storageKey());
        if (head.isEmpty()) {
            return new Inspection(null, "No file was uploaded for this document.");
        }
        if (head.get().sizeBytes() != doc.sizeBytes()) {
            return new Inspection(null, "The uploaded file size does not match the declared size.");
        }
        String stored = head.get().contentType();
        if (stored != null && !stored.toLowerCase(Locale.ROOT).startsWith(doc.contentType())) {
            return new Inspection(null, "The uploaded file type does not match the declared type.");
        }
        try (InputStream in = storage.open(doc.storageKey())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] header = new byte[8];
            int headerLen = 0;
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                if (headerLen < header.length) {
                    int copy = Math.min(n, header.length - headerLen);
                    System.arraycopy(buf, 0, header, headerLen, copy);
                    headerLen += copy;
                }
                digest.update(buf, 0, n);
                total += n;
            }
            if (total != doc.sizeBytes()) {
                return new Inspection(null, "The uploaded file size does not match the declared size.");
            }
            if (!magicMatches(doc.contentType(), header, headerLen)) {
                return new Inspection(null, "The file content does not match its declared type.");
            }
            return new Inspection(HexFormat.of().formatHex(digest.digest()), null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean magicMatches(String contentType, byte[] h, int len) {
        return switch (contentType) {
            case "application/pdf" -> len >= 4 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F';
            case "image/jpeg" -> len >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case "image/png" -> len >= 4 && (h[0] & 0xFF) == 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47;
            default -> false;
        };
    }

    private void deleteObjectQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete object {}: {}", key, e.toString());
        }
    }
}
