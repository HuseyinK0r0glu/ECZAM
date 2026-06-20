package com.eczam.notifications.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class PushDtos {

    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {}

    public record SubscribeRequest(
            @NotBlank String endpoint,
            @NotNull @Valid Keys keys,
            String userAgent) {}

    public record SubscriptionView(String id) {}
    public record VapidKey(String publicKey) {}

    private PushDtos() {}
}
