package com.eczam.notifications;

import com.eczam.AbstractIntegrationTest;
import com.eczam.notifications.push.PushSubscription;
import com.eczam.notifications.push.PushSubscriptionRepository;
import com.eczam.notifications.push.WebPushSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class PushSubscriptionIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired PushSubscriptionRepository subs;
    @Autowired NotificationService notifications;
    @MockBean WebPushSender webPushSender;

    private record Ctx(UUID userId, String token) {}

    private Ctx registerUser(String email) {
        var reg = rest.postForEntity("/auth/register",
                Map.of("email", email, "password", "password1", "displayName", "Push"), Map.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) reg.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        return new Ctx(UUID.fromString((String) user.get("id")), (String) data.get("accessToken"));
    }

    private String subscribe(Ctx ctx, String endpoint) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(ctx.token());
        h.setContentType(MediaType.APPLICATION_JSON);
        var body = Map.of("endpoint", endpoint,
                "keys", Map.of("p256dh", "BPublicKeyValue", "auth", "AuthSecretValue"),
                "userAgent", "JUnit");
        var resp = rest.exchange("/push/subscriptions", HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        return (String) data.get("id");
    }

    @Test
    void subscribe_then_unsubscribe_via_endpoint() {
        Ctx ctx = registerUser("push-" + UUID.randomUUID() + "@b.com");
        String id = subscribe(ctx, "https://push.example.com/" + UUID.randomUUID());
        assertThat(subs.findByUserId(ctx.userId())).hasSize(1);

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(ctx.token());
        var del = rest.exchange("/push/subscriptions/" + id, HttpMethod.DELETE, new HttpEntity<>(h), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(subs.findByUserId(ctx.userId())).isEmpty();
    }

    @Test
    void notify_prunes_dead_endpoints() {
        Ctx ctx = registerUser("prune-" + UUID.randomUUID() + "@b.com");
        subscribe(ctx, "https://push.example.com/" + UUID.randomUUID());
        assertThat(subs.findByUserId(ctx.userId())).hasSize(1);

        // Simulate a 404/410 gone endpoint → sender returns false → service prunes it.
        when(webPushSender.send(any(PushSubscription.class), anyString())).thenReturn(false);
        notifications.notifyUser(ctx.userId(), NotificationType.DOSE_REMINDER, "t", "b", Map.of());

        assertThat(subs.findByUserId(ctx.userId())).isEmpty();
    }
}
