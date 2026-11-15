package com.ims.api.auth;

import com.ims.api.warehouse.Warehouse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.stream.Collectors;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank @Size(max = 160) String fullName
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 12, max = 72) String newPassword
    ) {
    }

    public record PasswordResetRequest(
            @NotBlank @Email @Size(max = 254) String email
    ) {
    }

    public record PasswordResetTokenResponse(
            String message,
            String resetToken,
            long expiresInSeconds
    ) {
    }

    public record PasswordResetConfirmRequest(
            @NotBlank @Size(min = 16, max = 128) String resetToken,
            @NotBlank @Size(min = 12, max = 72) String newPassword
    ) {
    }

    public record UserResponse(
            Long id,
            String email,
            String fullName,
            Set<String> roles,
            Set<Long> assignedWarehouseIds
    ) {
        public UserResponse(Long id, String email, String fullName, Set<String> roles) {
            this(id, email, fullName, roles, Set.of());
        }

        public static UserResponse from(AppUser user) {
            return new UserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getRoles().stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet()),
                    user.getAssignedWarehouses().stream().map(Warehouse::getId).collect(Collectors.toUnmodifiableSet())
            );
        }
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {
    }

    public record TokenBundle(TokenResponse response, String rawRefreshToken, long refreshTtlSeconds) {
    }
}
