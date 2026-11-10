package com.kyro.platform.funding.api;

import com.kyro.platform.common.errors.Problems;
import com.kyro.platform.contract.api.FundingApi;
import com.kyro.platform.contract.model.FundingSummary;
import com.kyro.platform.contract.model.Pledge;
import com.kyro.platform.contract.model.PledgeCreate;
import com.kyro.platform.contract.model.PledgePage;
import com.kyro.platform.funding.application.PledgeService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FundingController implements FundingApi {

    private static final String NO_STORE = "no-store";

    private final PledgeService service;

    FundingController(PledgeService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<Pledge> createPledge(UUID requestId, String idempotencyKey, PledgeCreate pledgeCreate) {
        PledgeService.CreateResult r = service.create(requestId, idempotencyKey, pledgeCreate);
        if (r.created()) {
            return ResponseEntity.created(URI.create("/api/v1/funding/pledges/" + r.pledge().getId()))
                    .header("Cache-Control", NO_STORE)
                    .body(r.pledge());
        }
        return ResponseEntity.ok().header("Cache-Control", NO_STORE).body(r.pledge());
    }

    @Override
    public ResponseEntity<Pledge> getPledge(UUID pledgeId) {
        return ResponseEntity.ok().header("Cache-Control", NO_STORE).body(service.get(pledgeId));
    }

    @Override
    public ResponseEntity<Pledge> cancelPledge(UUID pledgeId) {
        return ResponseEntity.ok().header("Cache-Control", NO_STORE).body(service.cancel(pledgeId));
    }

    @Override
    public ResponseEntity<PledgePage> listMyPledges(String cursor, Integer limit) {
        return ResponseEntity.ok().header("Cache-Control", NO_STORE).body(service.listMine(cursor, limit));
    }

    @Override
    public ResponseEntity<FundingSummary> getFundingSummary(UUID requestId) {
        return ResponseEntity.ok().header("Cache-Control", NO_STORE).body(service.summary(requestId));
    }

    /** A missing Idempotency-Key is 400 MALFORMED_REQUEST (the shared handler would otherwise answer 500). */
    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<Map<String, Object>> missingHeader(MissingRequestHeaderException ex, HttpServletRequest req) {
        Map<String, Object> body = Problems.body(
                400, "MALFORMED_REQUEST", "Required header " + ex.getHeaderName() + " is missing.", req.getRequestURI(), List.of());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
