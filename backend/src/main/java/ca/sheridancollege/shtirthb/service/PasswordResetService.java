package ca.sheridancollege.shtirthb.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.PasswordResetToken;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.PasswordResetTokenRepository;

@Service
public class PasswordResetService {

    private final ClientUserRepository clientUserRepository;
    private final AdminUserRepository adminUserRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(
            ClientUserRepository clientUserRepository,
            AdminUserRepository adminUserRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder) {

        this.clientUserRepository = clientUserRepository;
        this.adminUserRepository = adminUserRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void requestClientPasswordReset(String email) {

        String normalizedEmail = email.trim().toLowerCase();

        ClientUser user = clientUserRepository
                .findByEmail(normalizedEmail)
                .orElse(null);

        if (user == null) {
            return;
        }

        createResetToken(
                "CLIENT",
                user.getId()
        );
    }

    public void requestAdminPasswordReset(String email) {

        String normalizedEmail = email.trim().toLowerCase();

        AdminUser admin = adminUserRepository
                .findByEmail(normalizedEmail)
                .orElse(null);

        if (admin == null) {
            return;
        }

        createResetToken(
                "ADMIN",
                admin.getId()
        );
    }

    private void createResetToken(
            String userType,
            Long userId) {

        String token = UUID.randomUUID().toString();

        LocalDateTime expiresAt =
                LocalDateTime.now().plusMinutes(30);

        PasswordResetToken resetToken =
                new PasswordResetToken(
                        token,
                        userType,
                        userId,
                        expiresAt
                );

        passwordResetTokenRepository.save(resetToken);

        String resetPath =
                "ADMIN".equals(userType)
                        ? "/admin/reset-password?token="
                        : "/reset-password?token=";

        System.out.println();
        System.out.println("======================================");
        System.out.println("PASSWORD RESET REQUEST");
        System.out.println("User type: " + userType);
        System.out.println("Reset token: " + token);
        System.out.println(
                "Reset link: http://localhost:5173"
                        + resetPath
                        + token
        );
        System.out.println("Expires in: 30 minutes");
        System.out.println("======================================");
        System.out.println();
    }

    public boolean resetPassword(
            String token,
            String newPassword) {

        PasswordResetToken resetToken =
                passwordResetTokenRepository
                        .findByToken(token)
                        .orElse(null);

        if (resetToken == null) {
            return false;
        }

        if (resetToken.isUsed()) {
            return false;
        }

        if (resetToken.getExpiresAt()
                .isBefore(LocalDateTime.now())) {
            return false;
        }

        String encodedPassword =
                passwordEncoder.encode(newPassword);

        if ("CLIENT".equals(resetToken.getUserType())) {

            ClientUser user =
                    clientUserRepository
                            .findById(resetToken.getUserId())
                            .orElse(null);

            if (user == null) {
                return false;
            }

            user.setPassword(encodedPassword);
            clientUserRepository.save(user);

        } else if ("ADMIN".equals(resetToken.getUserType())) {

            AdminUser admin =
                    adminUserRepository
                            .findById(resetToken.getUserId())
                            .orElse(null);

            if (admin == null) {
                return false;
            }

            admin.setPassword(encodedPassword);
            adminUserRepository.save(admin);

        } else {
            return false;
        }

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        return true;
    }
}