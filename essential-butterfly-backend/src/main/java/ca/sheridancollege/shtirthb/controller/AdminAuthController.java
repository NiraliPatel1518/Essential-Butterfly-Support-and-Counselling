
package ca.sheridancollege.shtirthb.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.sheridancollege.shtirthb.dto.AdminLoginOtpRequest;
import ca.sheridancollege.shtirthb.dto.AdminLoginResendRequest;
import ca.sheridancollege.shtirthb.service.AdminEmailSecurityService;
import ca.sheridancollege.shtirthb.service.JwtService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin-auth")
public class AdminAuthController {

    private final AdminEmailSecurityService securityService;
    private final JwtService jwtService;

    public AdminAuthController(
            AdminEmailSecurityService securityService,
            JwtService jwtService) {

        this.securityService = securityService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login-otp/verify")
    public ResponseEntity<String> verifyLoginOtp(
            @Valid @RequestBody AdminLoginOtpRequest request) {

        var admin =
                securityService.verifyLoginOtp(request);

        return ResponseEntity.ok(
                jwtService.generateToken(
                        admin.getEmail(),
                        "ADMIN"
                )
        );
    }

    @PostMapping("/login-otp/resend")
    public ResponseEntity<String> resendLoginOtp(
            @Valid @RequestBody AdminLoginResendRequest request) {

        securityService.resendLoginOtp(request);

        return ResponseEntity.ok(
                "A new verification code has been sent to your admin email address."
        );
    }
}
