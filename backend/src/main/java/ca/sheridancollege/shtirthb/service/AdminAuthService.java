package ca.sheridancollege.shtirthb.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;

@Service
public class AdminAuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAuthService(
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder) {

        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AdminUser authenticate(String email, String password) {

        String normalizedEmail = email.trim().toLowerCase();

        AdminUser admin = adminUserRepository
                .findByEmail(normalizedEmail)
                .orElse(null);

        if (admin == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        if (admin.getLockedUntil() != null) {

            if (now.isBefore(admin.getLockedUntil())) {
                throw new IllegalStateException(
                        "Admin account is temporarily locked"
                );
            }

            admin.setLockedUntil(null);
            admin.setFailedLoginAttempts(0);

            adminUserRepository.save(admin);
        }

        if (!passwordEncoder.matches(password, admin.getPassword())) {

            int failedAttempts =
                    admin.getFailedLoginAttempts() + 1;

            admin.setFailedLoginAttempts(failedAttempts);

            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {

                admin.setLockedUntil(
                        now.plusMinutes(LOCKOUT_MINUTES)
                );
            }

            adminUserRepository.save(admin);

            return null;
        }

        admin.setFailedLoginAttempts(0);
        admin.setLockedUntil(null);

        adminUserRepository.save(admin);

        return admin;
    }

    public void unlockAfterPasswordReset(AdminUser admin) {

        admin.setFailedLoginAttempts(0);
        admin.setLockedUntil(null);

        adminUserRepository.save(admin);
    }
}