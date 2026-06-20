package com.eczam.notifications.push;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.Security;

@Component
public class WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);
    private final PushService pushService;
    private final boolean enabled;

    public WebPushSender(VapidProperties vapid) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        this.enabled = vapid.publicKey() != null && !vapid.publicKey().isBlank();
        this.pushService = enabled
                ? new PushService(vapid.publicKey(), vapid.privateKey(), vapid.subject())
                : null;
    }

    /** Returns false if the subscription is gone (410/404) so the caller can prune it. */
    public boolean send(PushSubscription sub, String payloadJson) {
        if (!enabled) { log.warn("VAPID not configured; skipping push"); return true; }
        try {
            Notification notification = new Notification(sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payloadJson);
            var resp = pushService.send(notification);
            int code = resp.getStatusLine().getStatusCode();
            if (code == 404 || code == 410) return false;
            return true;
        } catch (Exception e) {
            log.error("Push send failed: {}", e.getMessage());
            return true; // transient; keep the subscription
        }
    }
}
