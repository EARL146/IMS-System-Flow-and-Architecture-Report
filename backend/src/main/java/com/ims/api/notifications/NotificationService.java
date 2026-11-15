package com.ims.api.notifications;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.common.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class NotificationService {
    private final UserNotificationRepository notifications;
    private final AppUserRepository users;
    private final Clock clock;

    public NotificationService(
            UserNotificationRepository notifications,
            AppUserRepository users,
            Clock clock
    ) {
        this.notifications = notifications;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public void notifyUser(
            AppUser recipient,
            String notificationType,
            String title,
            String message,
            String referenceType,
            String referenceId
    ) {
        if (recipient == null) return;
        notifications.save(new UserNotification(recipient, notificationType, title, message, referenceType, referenceId));
    }

    @Transactional(readOnly = true)
    public NotificationDtos.NotificationFeedResponse feed(String email, int size) {
        AppUser user = requireUser(email);
        long unread = notifications.countByRecipient_IdAndReadFalse(user.getId());
        var items = notifications.findByRecipient_IdOrderByCreatedAtDescIdDesc(user.getId(), PageRequest.of(0, size))
                .map(NotificationDtos.NotificationItemResponse::from)
                .getContent();
        return new NotificationDtos.NotificationFeedResponse(unread, items);
    }

    @Transactional
    public NotificationDtos.NotificationItemResponse markRead(long notificationId, String email) {
        AppUser user = requireUser(email);
        UserNotification notification = notifications.findByIdAndRecipient_Id(notificationId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        notification.markRead(clock.instant());
        return NotificationDtos.NotificationItemResponse.from(notifications.saveAndFlush(notification));
    }

    @Transactional
    public void markAllRead(String email) {
        AppUser user = requireUser(email);
        notifications.markAllRead(user.getId(), clock.instant());
    }

    private AppUser requireUser(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(ApiException::unauthorized);
    }
}
