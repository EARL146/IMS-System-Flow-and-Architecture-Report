package com.ims.api.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from RefreshSession session join fetch session.user where session.tokenHash = :hash")
    Optional<RefreshSession> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("""
            update RefreshSession session
            set session.revokedAt = :revokedAt
            where session.user.id = :userId and session.revokedAt is null
            """)
    int revokeAllActiveForUser(@Param("userId") long userId, @Param("revokedAt") Instant revokedAt);
}
