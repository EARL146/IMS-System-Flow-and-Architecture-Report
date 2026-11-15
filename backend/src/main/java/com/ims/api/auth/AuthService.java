package com.ims.api.auth;

import com.ims.api.audit.SecurityAuditService;
import com.ims.api.common.ApiException;
import com.ims.api.config.AuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(30);

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final RefreshSessionRepository refreshSessions;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final AuthProperties authProperties;
    private final LoginAttemptService loginAttemptService;
    private final SecurityAuditService auditService;
    private final Clock clock;

    public AuthService(
            AuthenticationManager authenticationManager,
            AppUserRepository users,
            RoleRepository roles,
            RefreshSessionRepository refreshSessions,
            PasswordResetTokenRepository passwordResetTokens,
            PasswordEncoder passwordEncoder,
            AccessTokenService accessTokens,
            AuthProperties authProperties,
            LoginAttemptService loginAttemptService,
            SecurityAuditService auditService,
            Clock clock
    ) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.roles = roles;
        this.refreshSessions = refreshSessions;
        this.passwordResetTokens = passwordResetTokens;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.authProperties = authProperties;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public AuthDtos.UserResponse register(AuthDtos.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }

        Role customerRole = roles.findByCode("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("Customer role has not been seeded"));
        AppUser user = new AppUser(email, passwordEncoder.encode(request.password()), request.fullName().trim());
        user.addRole(customerRole);
        AppUser saved = users.save(user);
        auditService.record("AUTH_REGISTER", email, "USER", String.valueOf(saved.getId()),
                "Customer account registered: " + email);
        return AuthDtos.UserResponse.from(saved);
    }

    @Transactional
    public AuthDtos.TokenBundle login(AuthDtos.LoginRequest request) {
        String email = normalizeEmail(request.email());
        Instant now = clock.instant();
        var existingUser = users.findByEmailIgnoreCase(email);
        if (existingUser.isPresent() && existingUser.get().isLockedAt(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "This account is temporarily locked due to repeated failed sign-in attempts. Try again later.");
        }

        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (AuthenticationException exception) {
            loginAttemptService.recordFailedLogin(email);
            throw ApiException.unauthorized();
        }

        AppUser user = users.findByEmailIgnoreCaseForUpdate(email)
                .orElseThrow(ApiException::unauthorized);
        if (!user.isEnabled()) {
            throw ApiException.unauthorized();
        }
        if (user.isLockedAt(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "This account is temporarily locked due to repeated failed sign-in attempts. Try again later.");
        }
        user.resetFailedLogins();
        users.save(user);
        auditService.record("AUTH_LOGIN_SUCCESS", user.getEmail(), "USER", String.valueOf(user.getId()),
                "Authenticated session started for " + user.getEmail());
        return issueBundle(user);
    }

    @Transactional
    public AuthDtos.TokenBundle refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized();
        }

        Instant now = clock.instant();
        RefreshSession oldSession = refreshSessions.findByTokenHashForUpdate(hashToken(rawRefreshToken))
                .orElseThrow(ApiException::unauthorized);

        if (oldSession.getRevokedAt() != null || !oldSession.getExpiresAt().isAfter(now)
                || !oldSession.getUser().isEnabled() || oldSession.getUser().isLockedAt(now)) {
            throw ApiException.unauthorized();
        }

        oldSession.revoke(now);
        AuthDtos.TokenBundle replacement = issueBundle(oldSession.getUser());
        RefreshSession newSession = refreshSessions.findByTokenHashForUpdate(hashToken(replacement.rawRefreshToken()))
                .orElseThrow(() -> new IllegalStateException("New refresh session was not persisted"));
        oldSession.setReplacedBy(newSession);
        refreshSessions.save(oldSession);
        return replacement;
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshSessions.findByTokenHashForUpdate(hashToken(rawRefreshToken))
                .filter(session -> session.getRevokedAt() == null)
                .ifPresent(session -> {
                    session.revoke(clock.instant());
                    auditService.record("AUTH_LOGOUT", session.getUser().getEmail(), "USER",
                            String.valueOf(session.getUser().getId()), "Session signed out");
                });
    }

    @Transactional
    public void changePassword(String email, AuthDtos.ChangePasswordRequest request) {
        AppUser user = users.findByEmailIgnoreCaseForUpdate(normalizeEmail(email))
                .orElseThrow(ApiException::unauthorized);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.resetFailedLogins();
        users.save(user);
        refreshSessions.revokeAllActiveForUser(user.getId(), clock.instant());
        auditService.record("AUTH_PASSWORD_CHANGED", user.getEmail(), "USER", String.valueOf(user.getId()),
                "Account password changed and active refresh sessions revoked");
    }

    @Transactional
    public AuthDtos.PasswordResetTokenResponse requestPasswordReset(AuthDtos.PasswordResetRequest request) {
        String email = normalizeEmail(request.email());
        var maybeUser = users.findByEmailIgnoreCaseForUpdate(email);
        if (maybeUser.isEmpty() || !maybeUser.get().isEnabled()) {
            return new AuthDtos.PasswordResetTokenResponse(
                    "If an active account exists for this email, a password reset token has been generated.",
                    null,
                    PASSWORD_RESET_TTL.toSeconds());
        }
        AppUser user = maybeUser.get();
        String rawToken = newRawRefreshToken();
        Instant expiresAt = clock.instant().plus(PASSWORD_RESET_TTL);
        passwordResetTokens.save(new PasswordResetToken(user, hashToken(rawToken), expiresAt));
        auditService.record("AUTH_PASSWORD_RESET_REQUESTED", user.getEmail(), "USER", String.valueOf(user.getId()),
                "Password reset token generated for " + user.getEmail());
        return new AuthDtos.PasswordResetTokenResponse(
                "Password reset token generated. Use it within 30 minutes to set a new password.",
                rawToken,
                PASSWORD_RESET_TTL.toSeconds());
    }

    @Transactional
    public void confirmPasswordReset(AuthDtos.PasswordResetConfirmRequest request) {
        Instant now = clock.instant();
        PasswordResetToken resetToken = passwordResetTokens.findByTokenHashForUpdate(hashToken(request.resetToken().trim()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired password reset token"));
        if (!resetToken.isUsableAt(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired password reset token");
        }
        AppUser user = users.findByEmailIgnoreCaseForUpdate(resetToken.getUser().getEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired password reset token"));
        resetToken.markUsed(now);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.resetFailedLogins();
        passwordResetTokens.save(resetToken);
        users.save(user);
        refreshSessions.revokeAllActiveForUser(user.getId(), now);
        auditService.record("AUTH_PASSWORD_RESET_COMPLETED", user.getEmail(), "USER", String.valueOf(user.getId()),
                "Password reset completed and active refresh sessions revoked");
    }

    private AuthDtos.TokenBundle issueBundle(AppUser user) {
        Instant now = clock.instant();
        String rawRefreshToken = newRawRefreshToken();
        RefreshSession session = new RefreshSession(
                user,
                hashToken(rawRefreshToken),
                now.plus(authProperties.refreshTokenTtl()));
        refreshSessions.save(session);

        AuthDtos.TokenResponse response = new AuthDtos.TokenResponse(
                accessTokens.issue(user),
                "Bearer",
                authProperties.accessTokenTtl().toSeconds(),
                AuthDtos.UserResponse.from(user));
        return new AuthDtos.TokenBundle(response, rawRefreshToken, authProperties.refreshTokenTtl().toSeconds());
    }

    private String newRawRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new AuthenticationServiceException("Required token hash algorithm is unavailable", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
