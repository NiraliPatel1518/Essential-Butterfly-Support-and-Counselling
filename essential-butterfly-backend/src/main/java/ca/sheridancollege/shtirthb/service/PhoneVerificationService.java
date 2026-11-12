
package ca.sheridancollege.shtirthb.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.security.SecureRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.dto.PhoneNumberRequest;
import ca.sheridancollege.shtirthb.dto.PhoneVerificationStatusResponse;
import ca.sheridancollege.shtirthb.dto.VerifyPhoneRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

@Service
public class PhoneVerificationService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int EXPIRY_MINUTES = 10;
    private static final int MAX_RESENDS = 3;

    private final ClientUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    private final SecureRandom secureRandom = new SecureRandom();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public PhoneVerificationService(
            ClientUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${app.twilio.account-sid:}") String accountSid,
            @Value("${app.twilio.auth-token:}") String authToken,
            @Value("${app.twilio.from-number:}") String fromNumber) {

        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
    }

    @Transactional(readOnly = true)
    public PhoneVerificationStatusResponse getStatus(String email) {

        ClientUser user = findUser(email);

        return new PhoneVerificationStatusResponse(
                user.isPhoneVerified(),
                user.getPhoneNumber(),
                user.getPendingPhoneNumber()
        );
    }

    /*
     * Starts verification for a new phone number.
     *
     * A previously verified number is deliberately not overwritten.
     */
    @Transactional
    public void startVerification(
            PhoneNumberRequest request,
            String email) {

        ClientUser user = findUser(email);

        String requestedNumber =
                normalizePhone(request.getPhoneNumber());

        if (user.isPhoneVerified()
                && requestedNumber.equals(user.getPhoneNumber())) {

            throw new AppExceptions.BusinessRuleException(
                    "This phone number is already verified.");
        }

        boolean changedPendingNumber =
                !requestedNumber.equals(user.getPendingPhoneNumber());

        if (changedPendingNumber) {

            user.setPendingPhoneNumber(requestedNumber);

            user.setPhoneVerificationAttempts(0);
            user.setPhoneVerificationResendCount(0);
            user.setPhoneVerificationLastSentAt(null);

            clearCode(user);

        } else if (user.getPhoneVerificationLastSentAt() != null) {

            throw new AppExceptions.BusinessRuleException(
                    "A verification code has already been sent. "
                    + "Please use Resend code if you need another one.");
        }

        if (user.getPhoneVerificationAttempts() >= MAX_ATTEMPTS) {

            throw new AppExceptions.BusinessRuleException(
                    "You have reached the maximum number of incorrect attempts "
                    + "for this phone number. Enter a different number or contact support.");
        }

        sendNewCode(user);
    }

    @Transactional(
        noRollbackFor = AppExceptions.ValidationException.class
    )
    public void verify(
            VerifyPhoneRequest request,
            String email) {

        ClientUser user = findUser(email);

        if (user.getPendingPhoneNumber() == null
                || user.getPhoneVerificationCodeHash() == null
                || user.getPhoneVerificationCodeExpiry() == null) {

            throw new AppExceptions.ValidationException(
                    "There is no active phone verification. "
                    + "Please enter your phone number and request a code.");
        }

        if (user.getPhoneVerificationAttempts() >= MAX_ATTEMPTS) {

            throw new AppExceptions.ValidationException(
                    "You have reached the maximum number of incorrect attempts. "
                    + "Enter a different phone number to restart verification or contact support.");
        }

        if (user.getPhoneVerificationCodeExpiry()
                .isBefore(LocalDateTime.now())) {

            throw new AppExceptions.ValidationException(
                    "Your verification code has expired. Request a new code.");
        }

        if (!passwordEncoder.matches(
                request.getCode(),
                user.getPhoneVerificationCodeHash())) {

            int attempts =
                    user.getPhoneVerificationAttempts() + 1;

            user.setPhoneVerificationAttempts(attempts);

            users.save(user);

            int remaining = MAX_ATTEMPTS - attempts;

            if (remaining == 0) {

                throw new AppExceptions.ValidationException(
                        "You have reached the maximum of five incorrect attempts. "
                        + "Enter a different phone number to restart verification or contact support.");
            }

            throw new AppExceptions.ValidationException(
                    "The verification code is incorrect. "
                    + remaining + " attempt(s) remaining.");
        }

        /*
         * Only after successful verification do we replace
         * the customer's existing verified phone number.
         */
        user.setPhoneNumber(user.getPendingPhoneNumber());

        user.setPhoneVerified(true);

        user.setPendingPhoneNumber(null);

        user.setPhoneVerificationAttempts(0);
        user.setPhoneVerificationResendCount(0);
        user.setPhoneVerificationLastSentAt(null);

        clearCode(user);

        users.save(user);
    }

    @Transactional
    public void resend(String email) {

        ClientUser user = findUser(email);

        if (user.getPendingPhoneNumber() == null
                || user.getPhoneVerificationLastSentAt() == null) {

            throw new AppExceptions.BusinessRuleException(
                    "Enter your phone number and request a verification code first.");
        }

        if (user.getPhoneVerificationAttempts() >= MAX_ATTEMPTS) {

            throw new AppExceptions.BusinessRuleException(
                    "You have reached the maximum number of incorrect attempts. "
                    + "Enter a different phone number to restart verification or contact support.");
        }

        int resendCount =
                user.getPhoneVerificationResendCount();

        if (resendCount >= MAX_RESENDS) {

            throw new AppExceptions.BusinessRuleException(
                    "You have reached the maximum number of resend requests. "
                    + "Enter a different phone number or contact support.");
        }

        int cooldownSeconds =
                getCooldownSeconds(resendCount);

        LocalDateTime nextAllowedTime =
                user.getPhoneVerificationLastSentAt()
                        .plusSeconds(cooldownSeconds);

        LocalDateTime now = LocalDateTime.now();

        if (nextAllowedTime.isAfter(now)) {

            long remaining =
                    Duration.between(now, nextAllowedTime)
                            .getSeconds() + 1;

            throw new AppExceptions.BusinessRuleException(
                    "Please wait " + remaining
                    + " second(s) before requesting another code.");
        }

        /*
         * Only increment the resend counter after successful SMS delivery.
         */
        sendNewCode(user);

        user.setPhoneVerificationResendCount(resendCount + 1);

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

        String code = String.format(
                Locale.ROOT,
                "%06d",
                secureRandom.nextInt(1_000_000));

        sendSms(
                user.getPendingPhoneNumber(),
                "Your Essential Butterfly verification code is "
                + code + ". It expires in 10 minutes. Do not share this code."
        );

        LocalDateTime now = LocalDateTime.now();

        user.setPhoneVerificationCodeHash(
                passwordEncoder.encode(code));

        user.setPhoneVerificationCodeExpiry(
                now.plusMinutes(EXPIRY_MINUTES));

        user.setPhoneVerificationLastSentAt(now);

        users.save(user);
    }

    /*
     * Sends an SMS through Twilio's Messages API.
     */
    private void sendSms(
            String toNumber,
            String body) {

        if (accountSid.isBlank()
                || authToken.isBlank()
                || fromNumber.isBlank()) {

            throw new AppExceptions.ServiceUnavailableException(
                    "Phone verification is not configured. Please contact support.");
        }

        try {

            String credentials =
                    Base64.getEncoder().encodeToString(
                            (accountSid + ":" + authToken)
                                    .getBytes(StandardCharsets.UTF_8));

            String form =
                    "From=" + encode(fromNumber)
                    + "&To=" + encode(toNumber)
                    + "&Body=" + encode(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "https://api.twilio.com/2010-04-01/Accounts/"
                            + accountSid + "/Messages.json"))
                    .timeout(Duration.ofSeconds(30))
                    .header(
                            "Authorization",
                            "Basic " + credentials)
                    .header(
                            "Content-Type",
                            "application/x-www-form-urlencoded")
                    .POST(
                            HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                throw new IllegalStateException(
                        "Twilio returned HTTP "
                        + response.statusCode());
            }

        } catch (Exception ex) {

            throw new AppExceptions.ServiceUnavailableException(
                    "We could not send the verification text. "
                    + "Check the phone number and try again.",
                    ex);
        }
    }

    private String encode(String value) {

        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8);
    }

    private String normalizePhone(String phoneNumber) {

        return phoneNumber.trim().replaceAll("[\\s()-]", "");
    }

    private ClientUser findUser(String email) {

        return users.findByEmail(
                email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "User account"));
    }

    private void clearCode(ClientUser user) {

        user.setPhoneVerificationCodeHash(null);
        user.setPhoneVerificationCodeExpiry(null);
    }
}
