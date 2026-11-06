package com.ims.api.auth;

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
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final RefreshSessionRepository refreshSessions;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final AuthProperties authProperties;
    private final Clock clock;

    public AuthService(
            AuthenticationManager authenticationManager,
            AppUserRepository users,
            RoleRepository roles,
            RefreshSessionRepository refreshSessions,
            PasswordEncoder passwordEncoder,
            AccessTokenService accessTokens,
            AuthProperties authProperties,
            Clock clock
    ) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.roles = roles;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.authProperties = authProperties;
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
        return AuthDtos.UserResponse.from(users.save(user));
    }

    @Transactional
    public AuthDtos.TokenBundle login(AuthDtos.LoginRequest request) {
        String email = normalizeEmail(request.email());
        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (AuthenticationException exception) {
            throw ApiException.unauthorized();
        }

        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(ApiException::unauthorized);
        if (!user.isEnabled()) {
            throw ApiException.unauthorized();
        }
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

        if (oldSession.getRevokedAt() != null || !oldSession.getExpiresAt().isAfter(now) || !oldSession.getUser().isEnabled()) {
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
                .ifPresent(session -> session.revoke(clock.instant()));
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
