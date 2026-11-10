package com.kyro.platform.identity.api;

import com.kyro.platform.contract.api.MeApi;
import com.kyro.platform.contract.model.CurrentUser;
import com.kyro.platform.contract.model.UpdateProfileRequest;
import com.kyro.platform.identity.application.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController implements MeApi {

    private final AccountService accounts;

    MeController(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public ResponseEntity<CurrentUser> getCurrentUser() {
        return ResponseEntity.ok(UserMapper.toCurrentUser(accounts.current()));
    }

    @Override
    public ResponseEntity<CurrentUser> updateMyProfile(UpdateProfileRequest body) {
        return ResponseEntity.ok(UserMapper.toCurrentUser(accounts.updateProfile(
                body.getDisplayName(), body.getRecipientPublicDisplayName(), body.getDonorPublicDisplayName())));
    }
}
