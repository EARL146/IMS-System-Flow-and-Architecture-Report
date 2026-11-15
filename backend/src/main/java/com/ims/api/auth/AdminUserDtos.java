package com.ims.api.auth;

import com.ims.api.warehouse.Warehouse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class AdminUserDtos {
    private AdminUserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank @Size(max = 160) String fullName,
            @NotEmpty Set<String> roles,
            Set<Long> assignedWarehouseIds
    ) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 160) String fullName,
            boolean enabled,
            boolean unlockAccount,
            @NotEmpty Set<String> roles,
            Set<Long> assignedWarehouseIds
    ) {
    }

    public record AdminUserResponse(
            Long id,
            String email,
            String fullName,
            boolean enabled,
            int failedLoginAttempts,
            Instant lockedUntil,
            List<String> roles,
            List<Long> assignedWarehouseIds,
            List<String> assignedWarehouseNames,
            Instant createdAt
    ) {
        public static AdminUserResponse from(AppUser user) {
            return new AdminUserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    user.isEnabled(),
                    user.getFailedLoginAttempts(),
                    user.getLockedUntil(),
                    user.getRoles().stream().map(Role::getCode).sorted().toList(),
                    user.getAssignedWarehouses().stream().map(Warehouse::getId).sorted().toList(),
                    user.getAssignedWarehouses().stream().map(Warehouse::getName).sorted().toList(),
                    user.getCreatedAt()
            );
        }
    }
}
