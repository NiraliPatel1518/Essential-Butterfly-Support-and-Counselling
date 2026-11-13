
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
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger log =
            LoggerFactory.getLogger(PhoneVerificationService.class);

    private static final Pattern APPROVED =
            Pattern.compile("\"status\"\\s*:\\s*\"approved\"");

    private final ClientUserRepository users;
    private final String accountSid;
    private final String authToken;
    private final String verifyServiceSid;
    private final String verifyBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public PhoneVerificationService(
            ClientUserRepository users,
            @Value("${app.twilio.account-sid:}") String accountSid,
            @Value("${app.twilio.auth-token:}") String authToken,
            @Value("${app.twilio.verify-service-sid:}") String verifyServiceSid,
            @Value("${app.twilio.verify-base-url:https://verify.twilio.com}") String verifyBaseUrl) {

        this.users = users;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.verifyServiceSid = verifyServiceSid;
        this.verifyBaseUrl = verifyBaseUrl.replaceAll("/+$", "");
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

        // One phone number belongs to one account only.
        ensureNumberAvailable(requestedNumber, user);

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
                || user.getPhoneVerificationLastSentAt() == null
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

        // Twilio Verify holds the code and decides whether it is correct.
        if (!checkCode(user.getPendingPhoneNumber(), request.getCode())) {

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

        // Another account may have verified this number in the meantime.
        ensureNumberAvailable(user.getPendingPhoneNumber(), user);

        /*
         * Only after successful verification do we replace
         * the customer's existing verified phone number.
         */
        user.setPhoneNumber(user.getPendingPhoneNumber());

        // A stored phone number is, by definition, a verified one; there
        // is no separate "verified" flag to set.

        // Deletes the phone_verifications row: nothing is left pending.
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

    /*
     * Asks Twilio Verify to generate and text a code. The code itself is
     * never seen or stored by this application.
     */
    private void sendNewCode(ClientUser user) {

        HttpResponse<String> response = callVerify(
                "Verifications",
                "To=" + encode(user.getPendingPhoneNumber())
                + "&Channel=sms");

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            log.error("Twilio Verify rejected the send request: HTTP {} {}",
                    response.statusCode(), response.body());

            throw new AppExceptions.ServiceUnavailableException(
                    "We could not send the verification text. "
                    + "Check the phone number and try again.",
                    new IllegalStateException(
                            "Twilio returned HTTP "
                            + response.statusCode()));
        }

        LocalDateTime now = LocalDateTime.now();

        // No code is kept locally; the expiry only mirrors Twilio's.
        user.setPhoneVerificationCodeHash(null);

        user.setPhoneVerificationCodeExpiry(
                now.plusMinutes(EXPIRY_MINUTES));

        user.setPhoneVerificationLastSentAt(now);

        users.save(user);
    }

    /*
     * Asks Twilio Verify whether the code is right for this number.
     * Returns true only when Twilio says "approved".
     */
    private boolean checkCode(String phoneNumber, String code) {

        HttpResponse<String> response = callVerify(
                "VerificationCheck",
                "To=" + encode(phoneNumber)
                + "&Code=" + encode(code));

        int status = response.statusCode();

        if (status == 404) {

            // Twilio no longer has an open verification for this number
            // (expired, or already approved).
            throw new AppExceptions.ValidationException(
                    "Your verification code has expired. Request a new code.");
        }

        if (status < 200 || status >= 300) {

            log.error("Twilio Verify rejected the check request: HTTP {} {}",
                    status, response.body());

            throw new AppExceptions.ServiceUnavailableException(
                    "We could not check the verification code right now. "
                    + "Please try again.",
                    new IllegalStateException(
                            "Twilio returned HTTP " + status));
        }

        return APPROVED.matcher(response.body()).find();
    }

    /*
     * Calls Twilio's Verify API.
     */
    private HttpResponse<String> callVerify(
            String path,
            String form) {

        if (accountSid.isBlank()
                || authToken.isBlank()
                || verifyServiceSid.isBlank()) {

            throw new AppExceptions.ServiceUnavailableException(
                    "Phone verification is not configured. Please contact support.");
        }

        try {

            String credentials =
                    Base64.getEncoder().encodeToString(
                            (accountSid + ":" + authToken)
                                    .getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            verifyBaseUrl + "/v2/Services/"
                            + verifyServiceSid + "/" + path))
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

            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();

            throw new AppExceptions.ServiceUnavailableException(
                    "We could not reach the phone verification service. "
                    + "Please try again.",
                    ex);

        } catch (Exception ex) {

            throw new AppExceptions.ServiceUnavailableException(
                    "We could not reach the phone verification service. "
                    + "Please try again.",
                    ex);
        }
    }

    private String encode(String value) {

        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8);
    }

    private void ensureNumberAvailable(
            String phoneNumber,
            ClientUser user) {

        if (users.existsByPhoneNumberAndIdNot(
                phoneNumber,
                user.getId())) {

            throw new AppExceptions.ConflictException(
                    "This phone number is already linked to another account. "
                    + "Please use a different number.");
        }
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
