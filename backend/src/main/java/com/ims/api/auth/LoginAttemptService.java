package com.ims.api.auth;

import com.ims.api.audit.SecurityAuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class LoginAttemptService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final SecurityAuditService auditService;
    private final Clock clock;

    public LoginAttemptService(
            AppUserRepository users,
            SecurityAuditService auditService,
            Clock clock
    ) {
        this.users = users;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(String email) {
        Instant now = clock.instant();
        var maybeUser = users.findByEmailIgnoreCaseForUpdate(email);
        if (maybeUser.isEmpty()) {
            auditService.record("AUTH_LOGIN_FAILURE", email, "AUTH", email, "Failed sign-in attempt for unknown account");
            return;
        }
        AppUser user = maybeUser.get();
        boolean lockedNow = user.recordFailedLogin(now, MAX_FAILED_ATTEMPTS, LOCK_DURATION);
        users.saveAndFlush(user);
        if (lockedNow) {
            auditService.record("AUTH_ACCOUNT_LOCKED", email, "USER", String.valueOf(user.getId()),
                    "Account locked for 15 minutes after " + user.getFailedLoginAttempts() + " failed sign-in attempts");
        } else {
            auditService.record("AUTH_LOGIN_FAILURE", email, "USER", String.valueOf(user.getId()),
                    "Failed sign-in attempt (" + user.getFailedLoginAttempts() + "/" + MAX_FAILED_ATTEMPTS + ")");
        }
    }
}
