package com.kyro.platform.identity.api;

import com.kyro.platform.contract.api.ConsentsApi;
import com.kyro.platform.contract.model.ConsentRecord;
import com.kyro.platform.contract.model.ConsentStatus;
import com.kyro.platform.contract.model.Consents;
import com.kyro.platform.contract.model.PolicyType;
import com.kyro.platform.contract.model.PolicyVersions;
import com.kyro.platform.identity.application.ConsentService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ConsentsController implements ConsentsApi {

    private final ConsentService consents;
    private final HttpServletRequest request;

    ConsentsController(ConsentService consents, HttpServletRequest request) {
        this.consents = consents;
        this.request = request;
    }

    @Override
    public ResponseEntity<ConsentStatus> getMyConsents() {
        return ResponseEntity.ok(toContract(consents.status()));
    }

    @Override
    public ResponseEntity<ConsentStatus> acceptCurrentPolicies(Consents body) {
        return ResponseEntity.ok(toContract(consents.accept(
                body.getTermsVersion(), body.getPrivacyVersion(), request.getRemoteAddr(), request.getHeader("User-Agent"))));
    }

    private static ConsentStatus toContract(ConsentService.Status s) {
        List<ConsentRecord> history = s.history().stream()
                .map(r -> new ConsentRecord(
                        PolicyType.fromValue(r.policyType()),
                        r.policyVersion(),
                        OffsetDateTime.ofInstant(r.acceptedAt(), ZoneOffset.UTC)))
                .toList();
        ConsentStatus out = new ConsentStatus(new PolicyVersions(s.currentTerms(), s.currentPrivacy()), s.reacceptRequired(), history);
        out.setAcceptedTermsVersion(s.acceptedTerms());
        out.setAcceptedPrivacyVersion(s.acceptedPrivacy());
        return out;
    }
}
