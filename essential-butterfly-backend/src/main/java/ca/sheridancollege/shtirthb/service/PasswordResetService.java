package ca.sheridancollege.shtirthb.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.PasswordResetToken;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.PasswordResetTokenRepository;

@Service
public class PasswordResetService {

    private static final int TOKEN_MINUTES = 30;

    private final ClientUserRepository clientUserRepository;
    private final AdminUserRepository adminUserRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final String frontendBaseUrl;

    public PasswordResetService(
            ClientUserRepository clientUserRepository,
            AdminUserRepository adminUserRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.frontend-base-url:http://localhost:5173}")
            String frontendBaseUrl) {

        this.clientUserRepository = clientUserRepository;
        this.adminUserRepository = adminUserRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public void requestPasswordReset(String email) {

        String normalizedEmail = email.trim().toLowerCase();

        ClientUser client = clientUserRepository
                .findByEmail(normalizedEmail)
                .orElse(null);

        AdminUser admin = adminUserRepository
                .findByEmail(normalizedEmail)
                .orElse(null);

        // An address can no longer belong to both account types; if legacy
        // data somehow does, do nothing rather than guess.
        if (client != null && admin != null) {
            return;
        }

        if (client != null) {
            createResetToken(null, client);

        } else if (admin != null) {
            createResetToken(admin, null);
        }
    }

    private void createResetToken(
            AdminUser admin,
            ClientUser client) {

        // The secret exists only in the link. Only its hash is stored.
        String token = UUID.randomUUID().toString();

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime expiresAt = now.plusMinutes(TOKEN_MINUTES);

        String tokenHash = hash(token);

        PasswordResetToken resetToken;

        if (admin != null) {

            // Only one unused token may exist per account.
            passwordResetTokenRepository.revokeActiveForAdmin(admin, now);

            resetToken = PasswordResetToken.forAdmin(
                    tokenHash,
                    admin,
                    expiresAt
            );

        } else {

            passwordResetTokenRepository.revokeActiveForClient(client, now);

            resetToken = PasswordResetToken.forClient(
                    tokenHash,
                    client,
                    expiresAt
            );
        }

        passwordResetTokenRepository.save(resetToken);

        String resetLink = frontendBaseUrl
                + "/reset-password?token="
                + token;

        // Development only: replace console output with email delivery
        // before deploying the application.
        System.out.println();
        System.out.println("======================================");
        System.out.println("PASSWORD RESET REQUEST");
        System.out.println("User type: " + resetToken.getUserType());
        System.out.println("Reset link: " + resetLink);
        System.out.println("Expires in: " + TOKEN_MINUTES + " minutes");
        System.out.println("======================================");
        System.out.println();
    }

    @Transactional
    public boolean resetPassword(
            String token,
            String newPassword) {

        if (token == null
                || token.isBlank()
                || newPassword == null
                || newPassword.length() < 8) {

            return false;
        }

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHash(hash(token.trim()))
                .orElse(null);

        if (resetToken == null || resetToken.isUsed()) {
            return false;
        }

        if (!resetToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            return false;
        }

        String encodedPassword =
                passwordEncoder.encode(newPassword);

        if (resetToken.getClientUser() != null) {

            ClientUser user = resetToken.getClientUser();

            user.setPassword(encodedPassword);
            clientUserRepository.save(user);

        } else if (resetToken.getAdminUser() != null) {

            AdminUser admin = resetToken.getAdminUser();

            admin.setPassword(encodedPassword);
            admin.setFailedLoginAttempts(0);
            admin.setLockedUntil(null);

            adminUserRepository.save(admin);

        } else {
            return false;
        }

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        return true;
    }

    // SHA-256 of the token, as 64 lower-case hex characters.
    private static String hash(String token) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(
                            token.getBytes(StandardCharsets.UTF_8)
                    )
            );

        } catch (NoSuchAlgorithmException ex) {

            // Every Java platform is required to provide SHA-256.
            throw new IllegalStateException(ex);
        }
    }
}
