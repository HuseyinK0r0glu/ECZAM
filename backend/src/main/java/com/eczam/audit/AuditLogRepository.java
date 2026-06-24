package com.eczam.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);

    Page<AuditLog> findByEventType(String eventType, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:eventType IS NULL OR a.eventType = :eventType) AND " +
           "(:from IS NULL OR a.createdAt >= :from) AND " +
           "(:to IS NULL OR a.createdAt <= :to) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> search(@Param("userId") UUID userId,
                          @Param("eventType") String eventType,
                          @Param("from") OffsetDateTime from,
                          @Param("to") OffsetDateTime to,
                          Pageable pageable);
}
