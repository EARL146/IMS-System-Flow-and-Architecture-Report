package com.ims.api.auth;

import com.ims.api.config.BootstrapProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {
    private final BootstrapProperties properties;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminRunner(
            BootstrapProperties properties,
            AppUserRepository users,
            RoleRepository roles,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.adminEmail() == null ? "" : properties.adminEmail().trim().toLowerCase(Locale.ROOT);
        String password = properties.adminPassword() == null ? "" : properties.adminPassword();
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || password.length() < 16) {
            throw new IllegalStateException("Set both IMS_BOOTSTRAP_ADMIN_EMAIL and an IMS_BOOTSTRAP_ADMIN_PASSWORD of at least 16 characters");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }

        Role ownerRole = roles.findByCode("OWNER")
                .orElseThrow(() -> new IllegalStateException("Owner role has not been seeded"));
        AppUser owner = new AppUser(email, passwordEncoder.encode(password), "Business Owner");
        owner.addRole(ownerRole);
        users.save(owner);
    }
}
