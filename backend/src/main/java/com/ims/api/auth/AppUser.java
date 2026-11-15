package com.ims.api.auth;

import com.ims.api.warehouse.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_users")
public class AppUser implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_warehouse_scopes",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "warehouse_id"))
    private Set<Warehouse> assignedWarehouses = new HashSet<>();

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.enabled = true;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        this.failedLoginAttempts += 1;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    public void resetFailedLogins() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public void setRoles(Collection<Role> nextRoles) {
        this.roles.clear();
        this.roles.addAll(nextRoles);
    }

    public Set<Warehouse> getAssignedWarehouses() {
        return assignedWarehouses;
    }

    public void setAssignedWarehouses(Collection<Warehouse> warehouses) {
        this.assignedWarehouses.clear();
        this.assignedWarehouses.addAll(warehouses);
    }

    public boolean canAccessWarehouse(long warehouseId) {
        boolean hasGlobalRole = roles.stream().anyMatch(role ->
                "OWNER".equals(role.getCode()) || "ADMIN".equals(role.getCode()) || "SUPER_ADMIN".equals(role.getCode()));
        if (hasGlobalRole || assignedWarehouses.isEmpty()) {
            return true;
        }
        return assignedWarehouses.stream().anyMatch(warehouse -> warehouse.getId().equals(warehouseId));
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                .toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
