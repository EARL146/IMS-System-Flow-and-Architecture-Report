package com.ims.api.auth;

import com.ims.api.config.AuthProperties;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "ims_refresh";

    private final AuthService authService;
    private final AuthProperties properties;

    public AuthController(AuthService authService, AuthProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.UserResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return tokenResponse(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return tokenResponse(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString()).build();
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @Valid @RequestBody AuthDtos.ChangePasswordRequest request,
            Authentication authentication
    ) {
        authService.changePassword(authentication.getName(), request);
    }

    @PostMapping("/password-reset/request")
    public AuthDtos.PasswordResetTokenResponse requestPasswordReset(
            @Valid @RequestBody AuthDtos.PasswordResetRequest request
    ) {
        return authService.requestPasswordReset(request);
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(
            @Valid @RequestBody AuthDtos.PasswordResetConfirmRequest request
    ) {
        authService.confirmPasswordReset(request);
    }

    private ResponseEntity<AuthDtos.TokenResponse> tokenResponse(AuthDtos.TokenBundle bundle) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, bundle.rawRefreshToken())
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ofSeconds(bundle.refreshTtlSeconds()))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(bundle.response());
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
