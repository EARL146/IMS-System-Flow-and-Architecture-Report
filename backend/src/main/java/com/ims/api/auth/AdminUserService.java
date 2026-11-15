package com.ims.api.auth;

import com.ims.api.audit.SecurityAuditService;
import com.ims.api.common.ApiException;
import com.ims.api.common.PageResponse;
import com.ims.api.warehouse.Warehouse;
import com.ims.api.warehouse.WarehouseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AdminUserService {
    private static final List<String> DEFAULT_ROLES = List.of(
            "OWNER", "ADMIN", "INVENTORY_MANAGER", "PURCHASING_OFFICER",
            "WAREHOUSE_STAFF", "CASHIER", "ACCOUNTANT", "CUSTOMER_SUPPORT", "CUSTOMER"
    );

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final WarehouseRepository warehouses;
    private final RefreshSessionRepository refreshSessions;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService auditService;
    private final Clock clock;

    public AdminUserService(
            AppUserRepository users,
            RoleRepository roles,
            WarehouseRepository warehouses,
            RefreshSessionRepository refreshSessions,
            PasswordEncoder passwordEncoder,
            SecurityAuditService auditService,
            Clock clock
    ) {
        this.users = users;
        this.roles = roles;
        this.warehouses = warehouses;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserDtos.AdminUserResponse> list(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "email"));
        return PageResponse.from(users.findAll(pageable).map(AdminUserDtos.AdminUserResponse::from));
    }

    @Transactional(readOnly = true)
    public List<String> availableRoles() {
        return DEFAULT_ROLES;
    }

    @Transactional
    public AdminUserDtos.AdminUserResponse create(AdminUserDtos.CreateUserRequest request, String actorEmail) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        Set<Role> resolvedRoles = resolveRoles(request.roles());
        Set<Warehouse> resolvedWarehouses = resolveWarehouses(request.assignedWarehouseIds());

        AppUser user = new AppUser(email, passwordEncoder.encode(request.password()), request.fullName().trim());
        user.setRoles(resolvedRoles);
        user.setAssignedWarehouses(resolvedWarehouses);
        AppUser saved = users.saveAndFlush(user);

        auditService.record("USER_CREATED", actorEmail, "USER", String.valueOf(saved.getId()),
                "Created account " + email + " with roles " + request.roles());
        return AdminUserDtos.AdminUserResponse.from(saved);
    }

    @Transactional
    public AdminUserDtos.AdminUserResponse update(long userId, AdminUserDtos.UpdateUserRequest request, String actorEmail) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User account not found"));
        if (user.getEmail().equalsIgnoreCase(actorEmail) && !request.enabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot disable your own active account");
        }

        Set<Role> resolvedRoles = resolveRoles(request.roles());
        Set<Warehouse> resolvedWarehouses = resolveWarehouses(request.assignedWarehouseIds());

        user.setFullName(request.fullName().trim());
        user.setEnabled(request.enabled());
        if (request.unlockAccount()) {
            user.resetFailedLogins();
        }
        user.setRoles(resolvedRoles);
        user.setAssignedWarehouses(resolvedWarehouses);
        AppUser saved = users.saveAndFlush(user);

        if (!request.enabled()) {
            refreshSessions.revokeAllActiveForUser(saved.getId(), clock.instant());
        }

        auditService.record("USER_UPDATED", actorEmail, "USER", String.valueOf(saved.getId()),
                "Updated account " + saved.getEmail() + " (enabled=" + saved.isEnabled() + ", roles=" + request.roles() + ")");
        return AdminUserDtos.AdminUserResponse.from(saved);
    }

    private Set<Role> resolveRoles(Set<String> codes) {
        Set<Role> resolved = new HashSet<>();
        for (String raw : codes) {
            String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (!DEFAULT_ROLES.contains(code)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported role code: " + raw);
            }
            Role role = roles.findByCode(code)
                    .orElseGet(() -> roles.saveAndFlush(new Role(code, code.replace('_', ' '))));
            resolved.add(role);
        }
        return resolved;
    }

    private Set<Warehouse> resolveWarehouses(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<Warehouse> resolved = new HashSet<>();
        for (Long id : ids) {
            if (id == null) continue;
            Warehouse warehouse = warehouses.findById(id)
                    .orElseThrow(() -> ApiException.notFound("Warehouse not found: " + id));
            resolved.add(warehouse);
        }
        return resolved;
    }
}
