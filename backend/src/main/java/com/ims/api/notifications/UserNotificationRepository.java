package com.ims.api.notifications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    Page<UserNotification> findByRecipient_IdOrderByCreatedAtDescIdDesc( long recipientId, Pageable pageable);

    long countByRecipient_IdAndReadFalse(long recipientId);

    Optional<UserNotification> findByIdAndRecipient_Id(long id, long recipientId);

    @Modifying
    @Query("""
            update UserNotification n
            set n.read = true, n.readAt = :readAt
            where n.recipient.id = :recipientId and n.read = false
            """)
    int markAllRead(@Param("recipientId") long recipientId, @Param("readAt") Instant readAt);
}
