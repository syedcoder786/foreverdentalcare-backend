package com.clinic.config;

import com.clinic.entity.Admin;
import com.clinic.enums.Role;
import com.clinic.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator from ADMIN_EMAIL / ADMIN_PASSWORD environment variables if no admin exists.
 * No credentials are hard-coded anywhere in the codebase.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 10;

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminRepository.count() > 0) {
            return;
        }
        AppProperties.AdminBootstrap cfg = properties.adminBootstrap();
        if (cfg.email() == null || cfg.email().isBlank() || cfg.password() == null || cfg.password().isBlank()) {
            log.warn("No admin account exists. Set ADMIN_EMAIL and ADMIN_PASSWORD to create the initial administrator.");
            return;
        }
        if (cfg.password().length() < MIN_PASSWORD_LENGTH) {
            log.error("ADMIN_PASSWORD must be at least {} characters. Initial admin was NOT created.", MIN_PASSWORD_LENGTH);
            return;
        }
        Admin admin = new Admin();
        admin.setName(cfg.name() == null || cfg.name().isBlank() ? "Clinic Admin" : cfg.name().trim());
        admin.setEmail(cfg.email().trim().toLowerCase());
        admin.setPassword(passwordEncoder.encode(cfg.password()));
        admin.setRole(Role.ADMIN);
        adminRepository.save(admin);
        log.info("Initial administrator account created for {}", admin.getEmail());
    }
}
