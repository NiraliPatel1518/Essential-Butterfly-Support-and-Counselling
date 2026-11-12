
package ca.sheridancollege.shtirthb.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.sheridancollege.shtirthb.dto.AdminEmailChangeRequest;
import ca.sheridancollege.shtirthb.dto.AdminEmailOtpRequest;
import ca.sheridancollege.shtirthb.dto.AdminProfileResponse;
import ca.sheridancollege.shtirthb.service.AdminEmailSecurityService;
import ca.sheridancollege.shtirthb.service.JwtService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin-account")
public class AdminAccountController {

    private final AdminEmailSecurityService securityService;
    private final JwtService jwtService;

    public AdminAccountController(
            AdminEmailSecurityService securityService,
            JwtService jwtService) {

        this.securityService = securityService;
        this.jwtService = jwtService;
    }

    @GetMapping("/profile")
    public ResponseEntity<AdminProfileResponse> getProfile(
            Authentication authentication) {

        return ResponseEntity.ok(
                securityService.getProfile(
                        authentication.getName()
                )
        );
    }

    @PostMapping("/email-change/request")
    public ResponseEntity<String> requestEmailChange(
            @Valid @RequestBody AdminEmailChangeRequest request,
            Authentication authentication) {

        securityService.requestEmailChange(
                request,
                authentication.getName()
        );

        return ResponseEntity.ok(
                "A verification code has been sent to the new email address."
        );
    }

    @PostMapping("/email-change/verify")
    public ResponseEntity<String> verifyEmailChange(
            @Valid @RequestBody AdminEmailOtpRequest request,
            Authentication authentication) {

        var admin = securityService.verifyEmailChange(
                request,
                authentication.getName()
        );

        // Return a new JWT containing the updated email as its subject.
        return ResponseEntity.ok(
                jwtService.generateToken(
                        admin.getEmail(),
                        "ADMIN"
                )
        );
    }

    @PostMapping("/email-change/resend")
    public ResponseEntity<String> resendEmailChange(
            Authentication authentication) {

        securityService.resendEmailChangeOtp(
                authentication.getName()
        );

        return ResponseEntity.ok(
                "A new verification code has been sent to the new email address."
        );
    }
}
