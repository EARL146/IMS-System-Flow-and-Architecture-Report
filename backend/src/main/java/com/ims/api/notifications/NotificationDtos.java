package com.ims.api.notifications;

import java.time.Instant;
import java.util.List;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    public record NotificationItemResponse(
            Long id,
            String notificationType,
            String title,
            String message,
            String referenceType,
            String referenceId,
            boolean read,
            Instant createdAt,
            Instant readAt
    ) {
        public static NotificationItemResponse from(UserNotification notification) {
            return new NotificationItemResponse(
                    notification.getId(),
                    notification.getNotificationType(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getReferenceType(),
                    notification.getReferenceId(),
                    notification.isRead(),
                    notification.getCreatedAt(),
                    notification.getReadAt()
            );
        }
    }

    public record NotificationFeedResponse(
            long unreadCount,
            List<NotificationItemResponse> items
    ) {
    }
}
