package com.kyro.platform.discovery.api;

import com.kyro.platform.contract.api.DiscoveryApi;
import com.kyro.platform.contract.model.FeedPage;
import com.kyro.platform.contract.model.PublicRequestDetail;
import com.kyro.platform.contract.model.SavedPage;
import com.kyro.platform.contract.model.SwipeInput;
import com.kyro.platform.contract.model.SwipeResult;
import com.kyro.platform.discovery.application.DiscoveryService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DiscoveryController implements DiscoveryApi {

    private final DiscoveryService service;

    DiscoveryController(DiscoveryService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<FeedPage> getDiscoveryFeed(String cursor, Integer limit) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.feed(cursor, limit));
    }

    @Override
    public ResponseEntity<PublicRequestDetail> getPublicRequest(UUID requestId) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.detail(requestId));
    }

    @Override
    public ResponseEntity<SwipeResult> recordSwipe(UUID requestId, SwipeInput swipeInput) {
        return ResponseEntity.ok(service.swipe(requestId, swipeInput.getAction()));
    }

    @Override
    public ResponseEntity<SavedPage> listSaved(String cursor, Integer limit) {
        return ResponseEntity.ok(service.saved(cursor, limit));
    }

    @Override
    public ResponseEntity<Void> removeSaved(UUID requestId) {
        service.removeSaved(requestId);
        return ResponseEntity.noContent().build();
    }
}
