package ca.sheridancollege.shtirthb.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;

@Service
public class AdminAuthService {

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

        if (!passwordEncoder.matches(password, admin.getPassword())) {
            return null;
        }

        return admin;
    }
}