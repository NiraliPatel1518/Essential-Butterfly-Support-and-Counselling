
package ca.sheridancollege.shtirthb.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.sheridancollege.shtirthb.dto.LoginRequest;
import ca.sheridancollege.shtirthb.dto.SignupRequest;
import ca.sheridancollege.shtirthb.dto.VerifyEmailRequest;
import ca.sheridancollege.shtirthb.dto.ResendVerificationRequest;
import ca.sheridancollege.shtirthb.service.EmailVerificationService;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.service.AdminEmailSecurityService;
import ca.sheridancollege.shtirthb.service.AuthService;
import ca.sheridancollege.shtirthb.service.JwtService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final ClientUserRepository clientUserRepository;
    private final EmailVerificationService emailVerificationService;
    private final AdminEmailSecurityService adminEmailSecurityService;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            ClientUserRepository clientUserRepository,
            EmailVerificationService emailVerificationService,
            AdminEmailSecurityService adminEmailSecurityService) {

        this.authService = authService;
        this.jwtService = jwtService;
        this.clientUserRepository = clientUserRepository;
        this.emailVerificationService = emailVerificationService;
        this.adminEmailSecurityService = adminEmailSecurityService;
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signup(
            @Valid @RequestBody SignupRequest request) {

        authService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        "Account created. A verification code has been sent to your email address."
                );
    }

    @PostMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {

        emailVerificationService.verify(request);

        return ResponseEntity.ok(
                "Email address verified successfully. You can now log in."
        );
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {

        emailVerificationService.resend(request);

        return ResponseEntity.ok(
                "A new verification code has been sent."
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request) {

        AuthService.AuthenticatedAccount account =
                authService.authenticate(request);

        if (account == null) {
            throw new AppExceptions.InvalidCredentialsException();
        }

        /*
         * IMPORTANT:
         * Admin password verification alone does not issue a JWT.
         * An OTP challenge must be completed first.
         */
        if ("ADMIN".equals(account.role())) {

            return ResponseEntity.ok(
                    adminEmailSecurityService.startLoginVerification(
                            account.email()
                    )
            );
        }

        // Client login remains unchanged.
        return ResponseEntity.ok(
                jwtService.generateToken(
                        account.email(),
                        account.role()
                )
        );
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> getCurrentUser(
            Authentication authentication) {

        String email = authentication.getName();

        Optional<ClientUser> userOptional =
                clientUserRepository.findByEmail(email);

        if (userOptional.isEmpty()) {

            throw new AppExceptions.ResourceNotFoundException(
                    "User account"
            );
        }

        ClientUser user = userOptional.get();

        return ResponseEntity.ok(
                Map.of("fullName", user.getFullName())
        );
    }
}
