
package ca.sheridancollege.shtirthb.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.config.AppProperties;
import ca.sheridancollege.shtirthb.dto.AdminEmailChangeRequest;
import ca.sheridancollege.shtirthb.dto.AdminLoginChallengeResponse;
import ca.sheridancollege.shtirthb.dto.AdminProfileResponse;
import ca.sheridancollege.shtirthb.dto.AdminEmailOtpRequest;
import ca.sheridancollege.shtirthb.dto.AdminLoginOtpRequest;
import ca.sheridancollege.shtirthb.dto.AdminLoginResendRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

@Service
public class AdminEmailSecurityService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int EXPIRY_MINUTES = 10;
    private static final int MAX_RESENDS = 3;

    private final AdminUserRepository admins;
    private final ClientUserRepository clients;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String fromAddress;

    private final SecureRandom secureRandom = new SecureRandom();

    public AdminEmailSecurityService(
            AdminUserRepository admins,
            ClientUserRepository clients,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            AppProperties properties) {

        this.admins = admins;
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.fromAddress = properties.getMail().getFromAddress();
    }

    // ==========================================
    // ADMIN LOGIN TWO-FACTOR AUTHENTICATION
    // ==========================================

    @Transactional
    public AdminLoginChallengeResponse startLoginVerification(
            String email) {

        AdminUser admin = findAdminByEmail(email);

        String challengeSecret =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(randomBytes(32));

        /*
         * The database stores only a BCrypt hash of this challenge.
         * The admin ID allows us to retrieve the correct account.
         */
        String challengeToken =
                admin.getId() + "." + challengeSecret;

        String code = newOtp();

        sendOtp(
                admin.getEmail(),
                code,
                "Admin sign-in verification",
                "Use this code to finish signing in to Essential Butterfly Admin."
        );

        LocalDateTime now = LocalDateTime.now();

        admin.setLoginChallengeTokenHash(
                passwordEncoder.encode(challengeToken)
        );

        admin.setLoginOtpHash(
                passwordEncoder.encode(code)
        );

        admin.setLoginOtpExpiry(
                now.plusMinutes(EXPIRY_MINUTES)
        );

        admin.setLoginOtpLastSentAt(now);

        admin.setLoginOtpAttempts(0);
        admin.setLoginOtpResendCount(0);

        admins.save(admin);

        return new AdminLoginChallengeResponse(
                true,
                challengeToken,
                maskEmail(admin.getEmail())
        );
    }

    @Transactional(
        noRollbackFor = AppExceptions.ValidationException.class
    )
    public AdminUser verifyLoginOtp(
            AdminLoginOtpRequest request) {

        AdminUser admin = findAdminFromChallenge(
                request.getChallengeToken()
        );

        if (admin.getLoginOtpHash() == null
                || admin.getLoginOtpExpiry() == null) {

            throw new AppExceptions.ValidationException(
                    "This sign-in verification has expired. Please log in again."
            );
        }

        if (admin.getLoginOtpExpiry()
                .isBefore(LocalDateTime.now())) {

            throw new AppExceptions.ValidationException(
                    "Your verification code has expired. Request a new code."
            );
        }

        if (admin.getLoginOtpAttempts() >= MAX_ATTEMPTS) {

            clearLoginChallenge(admin);
            admins.save(admin);

            throw new AppExceptions.ValidationException(
                    "You have reached five incorrect attempts. Please log in again to request a new code."
            );
        }

        if (!passwordEncoder.matches(
                request.getCode(),
                admin.getLoginOtpHash())) {

            int attempts =
                    admin.getLoginOtpAttempts() + 1;

            admin.setLoginOtpAttempts(attempts);

            if (attempts >= MAX_ATTEMPTS) {

                clearLoginChallenge(admin);
                admins.save(admin);

                throw new AppExceptions.ValidationException(
                        "You have reached five incorrect attempts. Please log in again to request a new code."
                );
            }

            admins.save(admin);

            throw new AppExceptions.ValidationException(
                    "The verification code is incorrect. "
                    + (MAX_ATTEMPTS - attempts)
                    + " attempt(s) remaining."
            );
        }

        // OTP is correct. Consume the challenge.
        clearLoginChallenge(admin);

        admins.save(admin);

        return admin;
    }

    @Transactional
    public void resendLoginOtp(
            AdminLoginResendRequest request) {

        AdminUser admin = findAdminFromChallenge(
                request.getChallengeToken()
        );

        ensureActiveLoginChallenge(admin);

        enforceResendRules(
                admin.getLoginOtpResendCount(),
                admin.getLoginOtpLastSentAt()
        );

        String code = newOtp();

        sendOtp(
                admin.getEmail(),
                code,
                "Your new admin sign-in code",
                "Use this new code to finish signing in to Essential Butterfly Admin."
        );

        LocalDateTime now = LocalDateTime.now();

        admin.setLoginOtpHash(
                passwordEncoder.encode(code)
        );

        admin.setLoginOtpExpiry(
                now.plusMinutes(EXPIRY_MINUTES)
        );

        admin.setLoginOtpLastSentAt(now);

        admin.setLoginOtpResendCount(
                admin.getLoginOtpResendCount() + 1
        );

        admins.save(admin);
    }

    // ==========================================
    // ADMIN PROFILE
    // ==========================================

    @Transactional(readOnly = true)
    public AdminProfileResponse getProfile(
            String currentEmail) {

        AdminUser admin = findAdminByEmail(currentEmail);

        return new AdminProfileResponse(
                admin.getFullName(),
                admin.getEmail()
        );
    }

    // ==========================================
    // CHANGE ADMIN EMAIL
    // ==========================================

    @Transactional
    public void requestEmailChange(
            AdminEmailChangeRequest request,
            String currentEmail) {

        AdminUser admin = findAdminByEmail(currentEmail);

        String newEmail =
                normalizeEmail(request.getEmail());

        if (newEmail.equals(admin.getEmail())) {

            throw new AppExceptions.BusinessRuleException(
                    "The new email address is the same as your current email address."
            );
        }

        ensureEmailAvailable(newEmail, admin);

        if (newEmail.equals(admin.getPendingEmail())
                && admin.getEmailChangeOtpHash() != null) {

            throw new AppExceptions.BusinessRuleException(
                    "A verification code has already been sent to this address. Use Resend code if needed."
            );
        }

        String code = newOtp();

        sendOtp(
                newEmail,
                code,
                "Verify your new admin email address",
                "Use this code to confirm the new email address for your Essential Butterfly administrator account. "
                + "Your current login email will not change unless this code is verified."
        );

        LocalDateTime now = LocalDateTime.now();

        admin.setPendingEmail(newEmail);

        admin.setEmailChangeOtpHash(
                passwordEncoder.encode(code)
        );

        admin.setEmailChangeOtpExpiry(
                now.plusMinutes(EXPIRY_MINUTES)
        );

        admin.setEmailChangeOtpLastSentAt(now);

        admin.setEmailChangeOtpAttempts(0);
        admin.setEmailChangeOtpResendCount(0);

        admins.save(admin);
    }

    @Transactional(
        noRollbackFor = AppExceptions.ValidationException.class
    )
    public AdminUser verifyEmailChange(
            AdminEmailOtpRequest request,
            String currentEmail) {

        AdminUser admin = findAdminByEmail(currentEmail);

        if (admin.getPendingEmail() == null
                || admin.getEmailChangeOtpHash() == null
                || admin.getEmailChangeOtpExpiry() == null) {

            throw new AppExceptions.ValidationException(
                    "There is no active email change request. Enter the new email address again."
            );
        }

        if (admin.getEmailChangeOtpExpiry()
                .isBefore(LocalDateTime.now())) {

            throw new AppExceptions.ValidationException(
                    "Your verification code has expired. Request a new code."
            );
        }

        if (admin.getEmailChangeOtpAttempts() >= MAX_ATTEMPTS) {

            clearEmailChange(admin);
            admins.save(admin);

            throw new AppExceptions.ValidationException(
                    "You have reached five incorrect attempts. Start the email change again."
            );
        }

        if (!passwordEncoder.matches(
                request.getCode(),
                admin.getEmailChangeOtpHash())) {

            int attempts =
                    admin.getEmailChangeOtpAttempts() + 1;

            admin.setEmailChangeOtpAttempts(attempts);

            if (attempts >= MAX_ATTEMPTS) {

                clearEmailChange(admin);
                admins.save(admin);

                throw new AppExceptions.ValidationException(
                        "You have reached five incorrect attempts. Your current email address has not changed."
                );
            }

            admins.save(admin);

            throw new AppExceptions.ValidationException(
                    "The verification code is incorrect. "
                    + (MAX_ATTEMPTS - attempts)
                    + " attempt(s) remaining."
            );
        }

        String newEmail = admin.getPendingEmail();

        // Check again immediately before replacing the current email.
        ensureEmailAvailable(newEmail, admin);

        /*
         * The new address is currently stored as the pending change, and
         * every address is globally unique. Remove the pending row and send
         * that DELETE to the database first, then promote the address to
         * the sign-in address. Both steps are in one transaction.
         */
        clearEmailChange(admin);

        admins.saveAndFlush(admin);

        admin.setEmail(newEmail);

        admins.save(admin);

        return admin;
    }

    @Transactional
    public void resendEmailChangeOtp(
            String currentEmail) {

        AdminUser admin = findAdminByEmail(currentEmail);

        if (admin.getPendingEmail() == null
                || admin.getEmailChangeOtpHash() == null) {

            throw new AppExceptions.BusinessRuleException(
                    "There is no active email change request."
            );
        }

        if (admin.getEmailChangeOtpAttempts() >= MAX_ATTEMPTS) {

            throw new AppExceptions.BusinessRuleException(
                    "You have reached five incorrect attempts. Start the email change again."
            );
        }

        enforceResendRules(
                admin.getEmailChangeOtpResendCount(),
                admin.getEmailChangeOtpLastSentAt()
        );

        String code = newOtp();

        sendOtp(
                admin.getPendingEmail(),
                code,
                "Your new admin email verification code",
                "Use this code to confirm the new email address for your Essential Butterfly administrator account."
        );

        LocalDateTime now = LocalDateTime.now();

        admin.setEmailChangeOtpHash(
                passwordEncoder.encode(code)
        );

        admin.setEmailChangeOtpExpiry(
                now.plusMinutes(EXPIRY_MINUTES)
        );

        admin.setEmailChangeOtpLastSentAt(now);

        admin.setEmailChangeOtpResendCount(
                admin.getEmailChangeOtpResendCount() + 1
        );

        admins.save(admin);
    }

    // ==========================================
    // RESEND POLICY
    // ==========================================

    private void enforceResendRules(
            int resendCount,
            LocalDateTime lastSentAt) {

        if (resendCount >= MAX_RESENDS) {

            throw new AppExceptions.BusinessRuleException(
                    "You have reached the maximum number of resend requests."
            );
        }

        if (lastSentAt == null) {

            throw new AppExceptions.BusinessRuleException(
                    "A verification code has not been sent yet."
            );
        }

        int cooldown = switch (resendCount) {
            case 0 -> 60;
            case 1 -> 90;
            case 2 -> 120;
            default -> Integer.MAX_VALUE;
        };

        LocalDateTime allowedAt =
                lastSentAt.plusSeconds(cooldown);

        if (allowedAt.isAfter(LocalDateTime.now())) {

            long seconds = Duration.between(
                    LocalDateTime.now(),
                    allowedAt
            ).getSeconds() + 1;

            throw new AppExceptions.BusinessRuleException(
                    "Please wait " + seconds
                    + " second(s) before requesting another code."
            );
        }
    }

    // ==========================================
    // CHALLENGE VALIDATION
    // ==========================================

    private AdminUser findAdminFromChallenge(
            String challengeToken) {

        if (challengeToken == null) {

            throw new AppExceptions.ValidationException(
                    "Your sign-in verification is invalid. Please log in again."
            );
        }

        String[] parts =
                challengeToken.split("\\.", 2);

        if (parts.length != 2) {

            throw new AppExceptions.ValidationException(
                    "Your sign-in verification is invalid. Please log in again."
            );
        }

        try {

            Long id = Long.valueOf(parts[0]);

            AdminUser admin = admins.findById(id)
                    .orElseThrow(() ->
                            new AppExceptions.ValidationException(
                                    "Your sign-in verification is invalid. Please log in again."
                            )
                    );

            if (admin.getLoginChallengeTokenHash() == null
                    || !passwordEncoder.matches(
                            challengeToken,
                            admin.getLoginChallengeTokenHash())) {

                throw new AppExceptions.ValidationException(
                        "Your sign-in verification is invalid or has already been used. Please log in again."
                );
            }

            return admin;

        } catch (NumberFormatException ex) {

            throw new AppExceptions.ValidationException(
                    "Your sign-in verification is invalid. Please log in again."
            );
        }
    }

    private void ensureActiveLoginChallenge(
            AdminUser admin) {

        if (admin.getLoginChallengeTokenHash() == null
                || admin.getLoginOtpHash() == null) {

            throw new AppExceptions.BusinessRuleException(
                    "Your sign-in verification is no longer active. Please log in again."
            );
        }

        if (admin.getLoginOtpAttempts() >= MAX_ATTEMPTS) {

            clearLoginChallenge(admin);
            admins.save(admin);

            throw new AppExceptions.BusinessRuleException(
                    "You have reached five incorrect attempts. Please log in again."
            );
        }
    }

    // ==========================================
    // EMAIL UNIQUENESS
    // ==========================================

    private void ensureEmailAvailable(
            String email,
            AdminUser currentAdmin) {

        /*
         * existsByEmail also sees PENDING changes, so an address that
         * someone else is in the middle of verifying counts as taken.
         * The administrator's own sign-in address and own pending
         * address are not conflicts.
         */
        boolean ownAddress =
                email.equals(currentAdmin.getEmail())
                || email.equals(currentAdmin.getPendingEmail());

        if (!ownAddress && admins.existsByEmail(email)) {

            throw new AppExceptions.ConflictException(
                    "An administrator account already uses this email address."
            );
        }

        if (clients.existsByEmail(email)) {

            throw new AppExceptions.ConflictException(
                    "A client account already uses this email address. Choose a different email."
            );
        }
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private AdminUser findAdminByEmail(String email) {

        return admins.findByEmail(
                normalizeEmail(email))
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Administrator account"
                        )
                );
    }

    private String normalizeEmail(String email) {

        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String newOtp() {

        return String.format(
                Locale.ROOT,
                "%06d",
                secureRandom.nextInt(1_000_000)
        );
    }

    private byte[] randomBytes(int length) {

        byte[] bytes = new byte[length];

        secureRandom.nextBytes(bytes);

        return bytes;
    }

    private String maskEmail(String email) {

        int at = email.indexOf('@');

        if (at <= 1) {
            return "***" + email.substring(at);
        }

        return email.substring(0, 2)
                + "***"
                + email.substring(at);
    }

    // ==========================================
    // GMAIL OTP DELIVERY
    // ==========================================

    private void sendOtp(
            String to,
            String code,
            String subject,
            String introduction) {

        if (fromAddress == null || fromAddress.isBlank()) {

            throw new AppExceptions.ServiceUnavailableException(
                    "Email delivery is not configured. Please contact support."
            );
        }

        try {

            var message =
                    mailSender.createMimeMessage();

            var helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);

            helper.setText(
                    introduction
                    + "\n\nYour verification code is: "
                    + code
                    + "\n\nThis code expires in 10 minutes. Do not share it with anyone.",
                    """
                    <!doctype html>
                    <html>
                    <body style="font-family:Arial,sans-serif;color:#352047;line-height:1.6">
                    <div style="max-width:560px;margin:auto;padding:28px">
                    <h2 style="color:#51247b">Essential Butterfly Admin</h2>
                    <p>%s</p>
                    <p>Your verification code is:</p>
                    <div style="font-size:32px;font-weight:700;letter-spacing:8px;color:#51247b;padding:16px 0">%s</div>
                    <p>This code expires in <strong>10 minutes</strong>.</p>
                    <p>Never share this code with anyone.</p>
                    </div>
                    </body>
                    </html>
                    """.formatted(introduction, code)
            );

            mailSender.send(message);

        } catch (Exception ex) {

            throw new AppExceptions.ServiceUnavailableException(
                    "We could not send the verification email. Please try again.",
                    ex
            );
        }
    }

    // ==========================================
    // CLEAR VERIFICATION DATA
    // ==========================================

    private void clearLoginChallenge(AdminUser admin) {

        admin.setLoginChallengeTokenHash(null);
        admin.setLoginOtpHash(null);
        admin.setLoginOtpExpiry(null);
        admin.setLoginOtpLastSentAt(null);

        admin.setLoginOtpAttempts(0);
        admin.setLoginOtpResendCount(0);
    }

    private void clearEmailChange(AdminUser admin) {

        admin.setPendingEmail(null);
        admin.setEmailChangeOtpHash(null);
        admin.setEmailChangeOtpExpiry(null);
        admin.setEmailChangeOtpLastSentAt(null);

        admin.setEmailChangeOtpAttempts(0);
        admin.setEmailChangeOtpResendCount(0);
    }
}
