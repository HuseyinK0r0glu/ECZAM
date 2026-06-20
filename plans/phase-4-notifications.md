# Phase 4 — Notifications

> **Goal:** Web Push subscriptions, a per-minute background scheduler that fires dose
> reminders, low-stock and expiry alerts, expiration monitoring endpoints/page, and the
> frontend service worker + subscription flow.
>
> **Realizes:** EP-05, EP-06 · FR-050…054, FR-090…102 · UC-006, UC-007.
> **Prerequisites:** [phase-3-scheduling-logging.md](phase-3-scheduling-logging.md).
> **Exit criteria:** receive dose/low-stock/expiry notifications; act on "Mark as taken";
> Expiration page lists expiring-soon and expired items.

---

## 1. Dependencies / setup

Add to `backend/pom.xml`:

```xml
<dependency><groupId>nl.martijndwars</groupId><artifactId>web-push</artifactId><version>5.1.1</version></dependency>
<dependency><groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk18on</artifactId><version>1.78.1</version></dependency>
<!-- optional email channel -->
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-mail</artifactId></dependency>
```

Generate VAPID keys once and put them in env (`VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`,
`VAPID_EMAIL`); mirror the public key to the frontend as `VITE_VAPID_PUBLIC_KEY`:

```bash
npx web-push generate-vapid-keys   # or use the library's KeyPairGenerator
```

Add to `application.yml`:

```yaml
eczam:
  vapid:
    public-key: ${VAPID_PUBLIC_KEY:}
    private-key: ${VAPID_PRIVATE_KEY:}
    subject: ${VAPID_EMAIL:mailto:admin@eczam.app}
spring:
  mail:
    host: ${SMTP_HOST:}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USER:}
    password: ${SMTP_PASS:}
```

No new tables (push_subscriptions exists from Phase 1).

---

## 2. Backend

### Enable scheduling

#### `backend/src/main/java/com/eczam/shared/config/SchedulingConfig.java`

```java
package com.eczam.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {}
```

### Push subscriptions

#### `backend/src/main/java/com/eczam/notifications/push/PushSubscription.java`

```java
package com.eczam.notifications.push;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "push_subscriptions")
@Getter @Setter @NoArgsConstructor
public class PushSubscription {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String endpoint;

    @Column(nullable = false, columnDefinition = "text")
    private String p256dh;

    @Column(nullable = false, columnDefinition = "text")
    private String auth;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
```

#### `backend/src/main/java/com/eczam/notifications/push/PushSubscriptionRepository.java`

```java
package com.eczam.notifications.push;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {
    List<PushSubscription> findByUserId(UUID userId);
    Optional<PushSubscription> findByEndpoint(String endpoint);
    Optional<PushSubscription> findByIdAndUserId(UUID id, UUID userId);
    void deleteByEndpoint(String endpoint);
}
```

#### `backend/src/main/java/com/eczam/notifications/push/dto/PushDtos.java`

```java
package com.eczam.notifications.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class PushDtos {

    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {}

    public record SubscribeRequest(
            @NotBlank String endpoint,
            @NotNull Keys keys,
            String userAgent) {}

    public record SubscriptionView(String id) {}
    public record VapidKey(String publicKey) {}

    private PushDtos() {}
}
```

#### `backend/src/main/java/com/eczam/notifications/push/VapidProperties.java`

```java
package com.eczam.notifications.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eczam.vapid")
public record VapidProperties(String publicKey, String privateKey, String subject) {}
```

#### `backend/src/main/java/com/eczam/notifications/push/WebPushSender.java`

```java
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
```

#### `backend/src/main/java/com/eczam/notifications/push/PushSubscriptionService.java`

```java
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
```

#### `backend/src/main/java/com/eczam/notifications/push/PushSubscriptionController.java`

```java
package com.eczam.notifications.push;

import com.eczam.notifications.push.dto.PushDtos.*;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/push")
public class PushSubscriptionController {

    private final PushSubscriptionService service;
    public PushSubscriptionController(PushSubscriptionService service) { this.service = service; }

    @GetMapping("/vapid-public-key")
    public ApiResponse<VapidKey> vapidKey() { return ApiResponse.ok(service.publicKey()); }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubscriptionView> subscribe(@CurrentUser UUID userId,
                                                   @Valid @RequestBody SubscribeRequest req) {
        return ApiResponse.ok(service.subscribe(userId, req));
    }

    @DeleteMapping("/subscriptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.unsubscribe(userId, id);
    }
}
```

### Notification building & sending

#### `backend/src/main/java/com/eczam/notifications/NotificationType.java`

```java
package com.eczam.notifications;

public enum NotificationType { DOSE_REMINDER, LOW_STOCK, EXPIRY_WARNING, EXPIRED }
```

#### `backend/src/main/java/com/eczam/notifications/NotificationService.java`

```java
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
```

> **Email (optional):** add an `EmailNotifier` using `JavaMailSender`, invoked from
> `NotificationService` only when `user.notificationPreferences().email()` is true
> (FR-094). Omitted here for brevity; gate every send on the preference.

### Scheduler (the per-minute tick)

#### `backend/src/main/java/com/eczam/scheduler/NotificationDedupe.java`

```java
package com.eczam.scheduler;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory guard against duplicate sends within a window (NFR-020).
 * For multi-instance deployments use a persistent store + ShedLock instead.
 */
@Component
public class NotificationDedupe {
    private final ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<>();
    private volatile LocalDate day = LocalDate.now();

    public boolean firstTimeToday(String key) {
        rollover();
        return seen.putIfAbsent(key, Boolean.TRUE) == null;
    }
    public boolean firstTimeForMinute(String key) {
        return seen.putIfAbsent(key, Boolean.TRUE) == null;
    }
    private void rollover() {
        LocalDate now = LocalDate.now();
        if (!now.equals(day)) { synchronized (this) { if (!now.equals(day)) { seen.clear(); day = now; } } }
    }
}
```

#### `backend/src/main/java/com/eczam/scheduler/ReminderScheduler.java`

```java
package com.eczam.scheduler;

import com.eczam.inventory.UserMedication;
import com.eczam.inventory.UserMedicationRepository;
import com.eczam.notifications.NotificationService;
import com.eczam.notifications.NotificationType;
import com.eczam.reminders.MedicationSchedule;
import com.eczam.reminders.MedicationScheduleRepository;
import com.eczam.reminders.ScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Runs every minute (brief §7.1). For clustering, wrap with ShedLock so one node runs it. */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final MedicationScheduleRepository schedules;
    private final UserMedicationRepository inventory;
    private final NotificationService notifications;
    private final NotificationDedupe dedupe;

    public ReminderScheduler(MedicationScheduleRepository schedules, UserMedicationRepository inventory,
                             NotificationService notifications, NotificationDedupe dedupe) {
        this.schedules = schedules; this.inventory = inventory;
        this.notifications = notifications; this.dedupe = dedupe;
    }

    @Scheduled(cron = "0 * * * * *")  // top of every minute
    @Transactional(readOnly = true)
    public void tick() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        sendDoseReminders(now);
        sendLowStock();
        sendExpiry();
    }

    private void sendDoseReminders(LocalDateTime now) {
        for (MedicationSchedule s : schedules.findAllActive()) {
            if (!ScheduleService.isDue(s, now)) continue;
            String key = "dose:" + s.getId() + ":" + now;
            if (!dedupe.firstTimeForMinute(key)) continue;
            UserMedication um = s.getUserMedication();
            notifications.notifyUser(um.getUserId(), NotificationType.DOSE_REMINDER,
                    "İlaç zamanı: " + um.getMedication().getName(),
                    "Doz: " + s.getDosageAmount() + " " + um.getUnit(),
                    Map.of("userMedicationId", um.getId().toString(),
                           "scheduleId", s.getId().toString(),
                           "action", "MARK_TAKEN"));
        }
    }

    private void sendLowStock() {
        for (UserMedication um : inventory.findLowStock()) {
            String key = "low:" + um.getId() + ":" + LocalDate.now();
            if (!dedupe.firstTimeToday(key)) continue;
            notifications.notifyUser(um.getUserId(), NotificationType.LOW_STOCK,
                    "Az kaldı: " + um.getMedication().getName(),
                    "Kalan: " + um.getQuantity() + " " + um.getUnit(),
                    Map.of("userMedicationId", um.getId().toString()));
        }
    }

    private void sendExpiry() {
        for (UserMedication um : inventory.findExpiringSoon()) {
            String key = "exp:" + um.getId() + ":" + LocalDate.now();
            if (!dedupe.firstTimeToday(key)) continue;
            long days = ChronoUnit.DAYS.between(LocalDate.now(), um.getExpirationDate());
            notifications.notifyUser(um.getUserId(), NotificationType.EXPIRY_WARNING,
                    "Yakında dolacak: " + um.getMedication().getName(),
                    days + " gün kaldı", Map.of("userMedicationId", um.getId().toString()));
        }
        for (UserMedication um : inventory.findExpired()) {
            String key = "expd:" + um.getId() + ":" + LocalDate.now();
            if (!dedupe.firstTimeToday(key)) continue;
            notifications.notifyUser(um.getUserId(), NotificationType.EXPIRED,
                    "Süresi doldu: " + um.getMedication().getName(),
                    "Son kullanma: " + um.getExpirationDate(),
                    Map.of("userMedicationId", um.getId().toString()));
        }
    }
}
```

#### Add scheduler/expiry queries — `UserMedicationRepository` (extend)

```java
// add to com.eczam.inventory.UserMedicationRepository
import org.springframework.data.jpa.repository.Query;
import java.util.List;

@Query(value = """
        SELECT um.* FROM user_medications um
        JOIN users u ON u.id = um.user_id
        WHERE um.quantity <= (u.notification_preferences->>'low_stock_threshold')::numeric
        """, nativeQuery = true)
List<UserMedication> findLowStock();

@Query(value = """
        SELECT um.* FROM user_medications um
        JOIN users u ON u.id = um.user_id
        WHERE um.expiration_date IS NOT NULL
          AND um.expiration_date BETWEEN CURRENT_DATE
              AND CURRENT_DATE + ((u.notification_preferences->>'expiry_warning_days')::int)
        """, nativeQuery = true)
List<UserMedication> findExpiringSoon();

@Query(value = """
        SELECT * FROM user_medications WHERE expiration_date < CURRENT_DATE
        """, nativeQuery = true)
List<UserMedication> findExpired();

// Per-user variants for the Expiration page:
@Query(value = """
        SELECT um.* FROM user_medications um
        JOIN users u ON u.id = um.user_id
        WHERE um.user_id = :userId AND um.expiration_date IS NOT NULL
          AND um.expiration_date BETWEEN CURRENT_DATE
              AND CURRENT_DATE + COALESCE(:days, (u.notification_preferences->>'expiry_warning_days')::int)
        ORDER BY um.expiration_date ASC
        """, nativeQuery = true)
List<UserMedication> findExpiringSoonForUser(java.util.UUID userId, Integer days);

@Query(value = """
        SELECT * FROM user_medications
        WHERE user_id = :userId AND expiration_date < CURRENT_DATE
        ORDER BY expiration_date ASC
        """, nativeQuery = true)
List<UserMedication> findExpiredForUser(java.util.UUID userId);
```

### Expiration page endpoints

#### `backend/src/main/java/com/eczam/expiration/ExpirationController.java`

```java
package com.eczam.expiration;

import com.eczam.inventory.UserMedicationRepository;
import com.eczam.inventory.UserMedicationService;
import com.eczam.inventory.dto.InventoryDtos.InventoryItem;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import com.eczam.users.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/expiration")
public class ExpirationController {

    private final UserMedicationRepository repo;
    private final UserRepository users;

    public ExpirationController(UserMedicationRepository repo, UserRepository users) {
        this.repo = repo; this.users = users;
    }

    @GetMapping("/expiring-soon")
    public ApiResponse<List<InventoryItem>> expiringSoon(@CurrentUser UUID userId,
                                                         @RequestParam(required = false) Integer days) {
        int warn = users.findById(userId).map(u -> u.getNotificationPreferences().expiryWarningDays()).orElse(30);
        int low = users.findById(userId).map(u -> u.getNotificationPreferences().lowStockThreshold()).orElse(7);
        return ApiResponse.ok(repo.findExpiringSoonForUser(userId, days).stream()
                .map(um -> UserMedicationService.toItem(um, low, warn)).toList());
    }

    @GetMapping("/expired")
    public ApiResponse<List<InventoryItem>> expired(@CurrentUser UUID userId) {
        int warn = users.findById(userId).map(u -> u.getNotificationPreferences().expiryWarningDays()).orElse(30);
        int low = users.findById(userId).map(u -> u.getNotificationPreferences().lowStockThreshold()).orElse(7);
        return ApiResponse.ok(repo.findExpiredForUser(userId).stream()
                .map(um -> UserMedicationService.toItem(um, low, warn)).toList());
    }
}
```

> Register `VapidProperties` via `@EnableConfigurationProperties(VapidProperties.class)`
> (add to `SecurityConfig` or a dedicated `@Configuration`).

---

## 3. Frontend

### `frontend/public/sw.js` (push handler — Phase 6 merges this with Workbox)

```js
self.addEventListener("push", (event) => {
  const payload = event.data ? event.data.json() : {};
  const { title = "ECZAM", body = "", data = {} } = payload;
  const actions = data.action === "MARK_TAKEN" ? [{ action: "mark-taken", title: "✓ Aldım" }] : [];
  event.waitUntil(self.registration.showNotification(title, { body, data, actions, badge: "/icon-badge.png", icon: "/icon-192.png" }));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const data = event.notification.data || {};
  // Open the app; pass intent so the page can log the dose with the user's token.
  const url = event.action === "mark-taken" && data.userMedicationId
    ? `/?logDose=${data.userMedicationId}&scheduleId=${data.scheduleId || ""}`
    : "/";
  event.waitUntil(clients.openWindow(url));
});
```

### `frontend/src/services/pushService.ts`

```ts
import { apiClient } from "./apiClient";
import type { ApiResponse } from "../types";

function urlBase64ToUint8Array(base64: string): Uint8Array {
  const padding = "=".repeat((4 - (base64.length % 4)) % 4);
  const b64 = (base64 + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(b64);
  return Uint8Array.from([...raw].map((c) => c.charCodeAt(0)));
}

export async function getVapidPublicKey(): Promise<string> {
  const r = await apiClient.get<ApiResponse<{ publicKey: string }>>("/push/vapid-public-key");
  return r.data.data!.publicKey;
}

export async function registerPush(): Promise<boolean> {
  if (!("serviceWorker" in navigator) || !("PushManager" in window)) return false;
  const permission = await Notification.requestPermission();
  if (permission !== "granted") return false;

  const reg = await navigator.serviceWorker.register("/sw.js");
  const key = import.meta.env.VITE_VAPID_PUBLIC_KEY || (await getVapidPublicKey());
  const sub = await reg.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(key),
  });
  const json = sub.toJSON();
  await apiClient.post("/push/subscriptions", {
    endpoint: json.endpoint,
    keys: { p256dh: json.keys!.p256dh, auth: json.keys!.auth },
    userAgent: navigator.userAgent,
  });
  return true;
}
```

### `frontend/src/hooks/useNotifications.ts`

```ts
import { useEffect, useState } from "react";
import { registerPush } from "../services/pushService";

export function useNotifications() {
  const [enabled, setEnabled] = useState(Notification?.permission === "granted");
  const [asked, setAsked] = useState(false);

  async function enable() {
    setAsked(true);
    const ok = await registerPush();
    setEnabled(ok);
    return ok;
  }

  useEffect(() => {
    if ("serviceWorker" in navigator) navigator.serviceWorker.register("/sw.js").catch(() => {});
  }, []);

  return { enabled, asked, enable };
}
```

### `frontend/src/features/notifications/EnablePushPrompt.tsx` (onboarding)

```tsx
import { useNotifications } from "../../hooks/useNotifications";

export default function EnablePushPrompt() {
  const { enabled, enable } = useNotifications();
  if (enabled) return null;
  return (
    <div role="region" aria-label="Bildirim izni" className="rounded border bg-blue-50 p-4">
      <p className="text-lg">İlaç hatırlatmaları için bildirimleri açın.</p>
      <button onClick={enable} className="mt-2 rounded bg-blue-700 px-4 py-2 text-lg text-white">
        Bildirimleri Aç
      </button>
    </div>
  );
}
```

### Handle the "Mark as taken" deep link — add to `App.tsx`

```tsx
import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { logDose } from "./services/logService";

// Inside an authenticated layout component:
function DoseDeepLink() {
  const [params, setParams] = useSearchParams();
  useEffect(() => {
    const um = params.get("logDose");
    if (um) {
      logDose(um, 1, params.get("scheduleId") || undefined)
        .finally(() => { params.delete("logDose"); params.delete("scheduleId"); setParams(params, { replace: true }); });
    }
  }, [params, setParams]);
  return null;
}
// Render <DoseDeepLink /> within ProtectedRoute's element tree.
```

### `frontend/src/pages/Expiration.tsx`

```tsx
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../services/apiClient";
import type { ApiResponse } from "../types";
import type { InventoryItem } from "../services/inventoryService";

const fetchList = (path: string) => async (): Promise<InventoryItem[]> => {
  const r = await apiClient.get<ApiResponse<InventoryItem[]>>(path);
  return r.data.data!;
};

export default function Expiration() {
  const soon = useQuery({ queryKey: ["expiring-soon"], queryFn: fetchList("/expiration/expiring-soon") });
  const expired = useQuery({ queryKey: ["expired"], queryFn: fetchList("/expiration/expired") });

  return (
    <main className="mx-auto max-w-2xl p-6">
      <h1 className="mb-6 text-3xl font-bold">Son Kullanma</h1>

      <section className="mb-8">
        <h2 className="mb-2 text-2xl font-semibold text-red-700">Süresi Dolmuş</h2>
        <ul className="space-y-2">
          {expired.data?.map((i) => (
            <li key={i.id} className="rounded border border-red-300 bg-red-50 p-3 text-lg">
              {i.medicationName} — {i.expirationDate}
            </li>
          ))}
          {expired.data?.length === 0 && <p className="text-gray-600">Süresi dolmuş ilaç yok.</p>}
        </ul>
      </section>

      <section>
        <h2 className="mb-2 text-2xl font-semibold text-amber-700">Yakında Dolacak</h2>
        <ul className="space-y-2">
          {soon.data?.map((i) => (
            <li key={i.id} className="rounded border border-amber-300 bg-amber-50 p-3 text-lg">
              {i.medicationName} — {i.expirationDate}
            </li>
          ))}
          {soon.data?.length === 0 && <p className="text-gray-600">Yakında dolacak ilaç yok.</p>}
        </ul>
      </section>
    </main>
  );
}
```

### Wire route — `App.tsx`

```tsx
import Expiration from "./pages/Expiration";
// inside ProtectedRoute:
<Route path="/expiration" element={<Expiration />} />
```

---

## 4. Exit criteria (Phase 4)

- [ ] Push permission requested in onboarding; subscription stored on the backend.
- [ ] Scheduler fires a `DOSE_REMINDER` at the scheduled minute (no duplicates per minute).
- [ ] `LOW_STOCK` fires when quantity ≤ threshold; `EXPIRY_WARNING`/`EXPIRED` fire per window.
- [ ] Tapping "✓ Aldım" on a notification logs the dose (inventory decrements).
- [ ] Expiration page lists expiring-soon and expired items.

## 5. Tests (Phase 4)

- Scheduler: due-selection within the minute window; dedupe prevents duplicate sends;
  low-stock/expiry triggers fire on the right rows (integration with Testcontainers).
- Push: subscribe/unsubscribe endpoints; stale endpoint (404/410) pruning.
- Frontend: `pushService` base64 conversion; EnablePushPrompt flow; Expiration page.

Covers FR-050…054, FR-090…102, NFR-020, UC-006/007. Next:
[phase-5-ai-assistant-tts.md](phase-5-ai-assistant-tts.md).
