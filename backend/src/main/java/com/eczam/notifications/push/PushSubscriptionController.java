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
