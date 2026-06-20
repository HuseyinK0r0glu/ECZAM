package com.eczam.notifications.push;

import com.eczam.notifications.push.dto.PushDtos.*;
import com.eczam.shared.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository repo;
    private final VapidProperties vapid;

    public PushSubscriptionService(PushSubscriptionRepository repo, VapidProperties vapid) {
        this.repo = repo; this.vapid = vapid;
    }

    public VapidKey publicKey() { return new VapidKey(vapid.publicKey()); }

    @Transactional
    public SubscriptionView subscribe(UUID userId, SubscribeRequest req) {
        PushSubscription sub = repo.findByEndpoint(req.endpoint()).orElseGet(PushSubscription::new);
        sub.setUserId(userId);
        sub.setEndpoint(req.endpoint());
        sub.setP256dh(req.keys().p256dh());
        sub.setAuth(req.keys().auth());
        sub.setUserAgent(req.userAgent());
        repo.save(sub);
        return new SubscriptionView(sub.getId().toString());
    }

    @Transactional
    public void unsubscribe(UUID userId, UUID id) {
        PushSubscription sub = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Subscription not found"));
        repo.delete(sub);
    }
}
