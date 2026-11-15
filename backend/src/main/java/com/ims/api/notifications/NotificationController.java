package com.ims.api.notifications;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationDtos.NotificationFeedResponse feed(
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return notificationService.feed(authentication.getName(), size);
    }

    @PostMapping("/{id}/read")
    public NotificationDtos.NotificationItemResponse markRead(
            @PathVariable @Min(1) long id,
            Authentication authentication
    ) {
        return notificationService.markRead(id, authentication.getName());
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(Authentication authentication) {
        notificationService.markAllRead(authentication.getName());
    }
}
