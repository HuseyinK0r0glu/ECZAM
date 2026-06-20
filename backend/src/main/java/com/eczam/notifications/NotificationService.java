package com.eczam.notifications;

import com.eczam.notifications.push.PushSubscription;
import com.eczam.notifications.push.PushSubscriptionRepository;
import com.eczam.notifications.push.WebPushSender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final PushSubscriptionRepository subs;
    private final WebPushSender push;
    private final ObjectMapper mapper = new ObjectMapper();

    public NotificationService(PushSubscriptionRepository subs, WebPushSender push) {
        this.subs = subs; this.push = push;
    }

    @Transactional
    public void notifyUser(UUID userId, NotificationType type, String title, String body, Map<String, Object> data) {
        List<PushSubscription> list = subs.findByUserId(userId);
        if (list.isEmpty()) return;
        String payload = toJson(type, title, body, data);
        for (PushSubscription sub : list) {
            boolean alive = push.send(sub, payload);
            if (!alive) subs.delete(sub); // prune expired endpoints (404/410)
        }
    }

    private String toJson(NotificationType type, String title, String body, Map<String, Object> data) {
        try {
            return mapper.writeValueAsString(Map.of(
                    "type", type.name(), "title", title, "body", body,
                    "data", data == null ? Map.of() : data));
        } catch (JsonProcessingException e) {
            return "{\"title\":\"" + title + "\"}";
        }
    }
}
