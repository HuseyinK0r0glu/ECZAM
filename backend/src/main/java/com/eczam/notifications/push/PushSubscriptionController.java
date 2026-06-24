package com.eczam.notifications.push;

import com.eczam.notifications.push.dto.PushDtos.*;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Push Notifications", description = "Web Push (VAPID) subscription management. Get the public key, subscribe a device, and unsubscribe.")
@RestController
@RequestMapping("/push")
public class PushSubscriptionController {

    private final PushSubscriptionService service;
    public PushSubscriptionController(PushSubscriptionService service) { this.service = service; }

    @Operation(summary = "Get VAPID public key",
               description = "Returns the server's VAPID public key. Pass this to `PushManager.subscribe({ applicationServerKey })` in the browser's Service Worker to create a push subscription.",
               security = {})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "VAPID public key (Base64url-encoded)")
    @GetMapping("/vapid-public-key")
    public ApiResponse<VapidKey> vapidKey() {
        return ApiResponse.ok(service.publicKey());
    }

    @Operation(summary = "Register a push subscription",
               description = "Saves the browser push subscription (endpoint + keys) for the authenticated user. Call this after a successful `PushManager.subscribe()` in the Service Worker.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Subscription saved — returns subscription ID"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Validation failed", content = @Content)
    })
    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubscriptionView> subscribe(@CurrentUser UUID userId,
                                                   @Valid @RequestBody SubscribeRequest req) {
        return ApiResponse.ok(service.subscribe(userId, req));
    }

    @Operation(summary = "Unregister a push subscription",
               description = "Removes the subscription. Call this when the user disables notifications or the browser revokes the push permission.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Subscription removed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found or not owned by caller", content = @Content)
    })
    @DeleteMapping("/subscriptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.unsubscribe(userId, id);
    }
}
