package com.kyro.platform.identity.api;

import com.kyro.platform.common.config.KyroProperties;
import com.kyro.platform.contract.api.PoliciesApi;
import com.kyro.platform.contract.model.PolicyVersions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PoliciesController implements PoliciesApi {

    private final KyroProperties props;

    PoliciesController(KyroProperties props) {
        this.props = props;
    }

    @Override
    public ResponseEntity<PolicyVersions> getCurrentPolicies() {
        return ResponseEntity.ok(new PolicyVersions(props.policy().termsVersion(), props.policy().privacyVersion()));
    }
}
