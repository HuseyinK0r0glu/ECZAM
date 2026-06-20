package com.eczam.users;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NotificationPreferences(
        boolean push,
        boolean email,
        @JsonProperty("low_stock_threshold") int lowStockThreshold,
        @JsonProperty("expiry_warning_days") int expiryWarningDays) {

    public static NotificationPreferences defaults() {
        return new NotificationPreferences(true, false, 7, 30);
    }
}
