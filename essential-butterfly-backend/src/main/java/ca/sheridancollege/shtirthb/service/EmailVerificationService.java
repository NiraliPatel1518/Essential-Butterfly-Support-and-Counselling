package ca.sheridancollege.shtirthb.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.config.AppProperties;
import ca.sheridancollege.shtirthb.dto.ResendVerificationRequest;
import ca.sheridancollege.shtirthb.dto.VerifyEmailRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

@Service
public class EmailVerificationService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int EXPIRY_MINUTES = 10;
    private static final int MAX_RESENDS = 3;

    private final ClientUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String fromAddress;

    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(
            ClientUserRepository users,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            AppProperties properties) {

        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.fromAddress = properties.getMail().getFromAddress();
    }

    /*
     * Initial OTP delivery.
     *
     * Does not reset incorrect attempts or resend count.
     * A newly created ClientUser already starts with both values at zero.
     */
    @Transactional
    public void sendVerificationCode(ClientUser user) {
        sendNewCode(user);
    }

    /*
     * Verifies the OTP.
     *
     * ValidationException is deliberately excluded from rollback because
     * incorrect-attempt increments and fifth-attempt deletion must persist.
     */
    @Transactional(noRollbackFor = AppExceptions.ValidationException.class)
    public void verify(VerifyEmailRequest request) {

        String email = normalize(request.getEmail());

        ClientUser user = users.findByEmail(email)
                .orElseThrow(() -> new AppExceptions.ValidationException(
                        "This verification request is no longer valid. Please try signing up again."));

        if (user.isEmailVerified()) {
            return;
        }

        if (user.getEmailVerificationAttempts() >= MAX_ATTEMPTS) {
            users.delete(user);

            throw new AppExceptions.ValidationException(
                    "Your registration has been removed after five incorrect attempts. "
                    + "Select Try Again to create a new account.");
        }

        if (user.getEmailVerificationCodeHash() == null
                || user.getEmailVerificationCodeExpiry() == null
                || user.getEmailVerificationCodeExpiry().isBefore(LocalDateTime.now())) {

            throw new AppExceptions.ValidationException(
                    "Your verification code has expired. Request a new code.");
        }

        if (!passwordEncoder.matches(
                request.getCode(),
                user.getEmailVerificationCodeHash())) {

            int attempts = user.getEmailVerificationAttempts() + 1;
            user.setEmailVerificationAttempts(attempts);

            if (attempts >= MAX_ATTEMPTS) {
                users.delete(user);

                throw new AppExceptions.ValidationException(
                        "Your registration has been removed after five incorrect attempts. "
                        + "Select Try Again to create a new account.");
            }

            users.save(user);

            int remaining = MAX_ATTEMPTS - attempts;

            throw new AppExceptions.ValidationException(
                    "The verification code is incorrect. "
                    + remaining + " attempt(s) remaining.");
        }

        user.setEmailVerified(true);
        clearVerificationCode(user);

        users.save(user);
    }

    /*
     * Resend limits:
     * Initial email -> wait 60 seconds.
     * First resend  -> wait 90 seconds.
     * Second resend -> wait 120 seconds.
     * Third resend  -> no further resends.
     */
    @Transactional
    public void resend(ResendVerificationRequest request) {

        String email = normalize(request.getEmail());

        ClientUser user = users.findByEmail(email)
                .orElseThrow(() -> new AppExceptions.ResourceNotFoundException(
                        "Unverified account"));

        if (user.isEmailVerified()) {
            throw new AppExceptions.BusinessRuleException(
                    "This email address has already been verified.");
        }

        int resendCount = user.getEmailVerificationResendCount();

        if (resendCount >= MAX_RESENDS) {
            throw new AppExceptions.BusinessRuleException(
                    "You have reached the maximum number of resend requests. "
                    + "Please try signing up again.");
        }

        if (user.getEmailVerificationLastSentAt() == null) {
            throw new AppExceptions.BusinessRuleException(
                    "A verification code has not been sent for this account.");
        }

        int cooldownSeconds = getCooldownSeconds(resendCount);

        LocalDateTime nextAllowedTime =
                user.getEmailVerificationLastSentAt().plusSeconds(cooldownSeconds);

        LocalDateTime now = LocalDateTime.now();

        if (nextAllowedTime.isAfter(now)) {
            long secondsRemaining =
                    java.time.Duration.between(now, nextAllowedTime).getSeconds() + 1;

            throw new AppExceptions.BusinessRuleException(
                    "Please wait " + secondsRemaining
                    + " second(s) before requesting another code.");
        }

        /*
         * Send first. Increment the persistent resend counter only after
         * successful delivery.
         */
        sendNewCode(user);

        user.setEmailVerificationResendCount(resendCount + 1);
        users.save(user);
    }

    private int getCooldownSeconds(int resendCount) {

        return switch (resendCount) {
            case 0 -> 60;
            case 1 -> 90;
            case 2 -> 120;
            default -> Integer.MAX_VALUE;
        };
    }

    private void sendNewCode(ClientUser user) {

        if (fromAddress == null || fromAddress.isBlank()) {
            throw new AppExceptions.ServiceUnavailableException(
                    "Email delivery is not configured. Please contact support.");
        }

        LocalDateTime now = LocalDateTime.now();

        String code = String.format(
                java.util.Locale.ROOT,
                "%06d",
                secureRandom.nextInt(1_000_000));

        user.setEmailVerificationCodeHash(passwordEncoder.encode(code));
        user.setEmailVerificationCodeExpiry(now.plusMinutes(EXPIRY_MINUTES));
        user.setEmailVerificationLastSentAt(now);

        /*
         * Important:
         * Do not reset emailVerificationAttempts here.
         * Incorrect attempts remain cumulative across resends.
         */

        users.save(user);

        try {
            var message = mailSender.createMimeMessage();

            var helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(fromAddress);
            helper.setTo(user.getEmail());
            helper.setSubject("Verify your Essential Butterfly account");

            helper.setText(
                    "Your Essential Butterfly verification code is: " + code
                    + "\n\nThis code expires in 10 minutes. "
                    + "If you did not create this account, you can ignore this email.",
                    """
                    <!doctype html>
                    <html>
                    <body style="font-family:Arial,sans-serif;color:#352047;line-height:1.6">
                    <div style="max-width:560px;margin:auto;padding:28px">
                    <h2 style="color:#51247b">Verify your email</h2>
                    <p>Thank you for creating an Essential Butterfly account.</p>
                    <p>Enter this verification code to confirm your email address:</p>
                    <div style="font-size:32px;font-weight:700;letter-spacing:8px;color:#51247b;padding:16px 0">%s</div>
                    <p>This code expires in <strong>10 minutes</strong>.</p>
                    <p>If you did not create this account, you can ignore this email.</p>
                    </div>
                    </body>
                    </html>
                    """.formatted(code));

            mailSender.send(message);

        } catch (Exception ex) {
            throw new AppExceptions.ServiceUnavailableException(
                    "We could not send the verification email. Please try again.",
                    ex);
        }
    }

    private void clearVerificationCode(ClientUser user) {

        user.setEmailVerificationCodeHash(null);
        user.setEmailVerificationCodeExpiry(null);
        user.setEmailVerificationAttempts(0);
        user.setEmailVerificationLastSentAt(null);
        user.setEmailVerificationResendCount(0);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}