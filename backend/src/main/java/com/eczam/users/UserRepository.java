package com.eczam.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByGoogleSub(String googleSub);

    boolean existsByGoogleSub(String googleSub);

    /** Find active (non-deleted) user by email. */
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findActiveByEmail(@Param("email") String email);

    /** Admin: paginated list of all active users. */
    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL ORDER BY u.createdAt DESC")
    Page<User> findAllActive(Pageable pageable);

    /** Admin: search by email or displayName. */
    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL AND " +
           "(LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           " LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY u.createdAt DESC")
    Page<User> searchActive(@Param("q") String query, Pageable pageable);
}
