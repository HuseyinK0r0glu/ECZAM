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
