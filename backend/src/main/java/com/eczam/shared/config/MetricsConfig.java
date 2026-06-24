package com.eczam.shared.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines named business metric counters exposed via Actuator/Prometheus.
 * Inject any counter by its bean name where needed (e.g. in AuthService, MedicationLogService).
 */
@Configuration
public class MetricsConfig {

    // ---- Auth metrics ----

    @Bean
    public Counter userRegistrationsCounter(MeterRegistry registry) {
        return Counter.builder("eczam.users.registered")
                .description("Total user registrations")
                .register(registry);
    }

    @Bean
    public Counter loginSuccessCounter(MeterRegistry registry) {
        return Counter.builder("eczam.auth.login.success")
                .description("Successful login count")
                .register(registry);
    }

    @Bean
    public Counter loginFailureCounter(MeterRegistry registry) {
        return Counter.builder("eczam.auth.login.failure")
                .description("Failed login attempts")
                .register(registry);
    }

    @Bean
    public Counter googleLoginCounter(MeterRegistry registry) {
        return Counter.builder("eczam.auth.google.login")
                .description("Google OAuth logins")
                .register(registry);
    }

    @Bean
    public Counter accountLockedCounter(MeterRegistry registry) {
        return Counter.builder("eczam.auth.account.locked")
                .description("Accounts locked due to too many failed attempts")
                .register(registry);
    }

    // ---- Medication metrics ----

    @Bean
    public Counter doseLoggedCounter(MeterRegistry registry) {
        return Counter.builder("eczam.doses.logged")
                .description("Total doses logged")
                .register(registry);
    }

    @Bean
    public Counter lowStockAlertsCounter(MeterRegistry registry) {
        return Counter.builder("eczam.inventory.low_stock_alerts")
                .description("Low stock notifications sent")
                .register(registry);
    }

    @Bean
    public Counter expiryAlertsCounter(MeterRegistry registry) {
        return Counter.builder("eczam.inventory.expiry_alerts")
                .description("Expiry notifications sent")
                .register(registry);
    }

    // ---- AI metrics ----

    @Bean
    public Counter aiQueriesCounter(MeterRegistry registry) {
        return Counter.builder("eczam.ai.queries")
                .description("Total AI assistant queries")
                .register(registry);
    }
}
