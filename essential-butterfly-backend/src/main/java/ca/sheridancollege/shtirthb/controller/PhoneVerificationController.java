
package ca.sheridancollege.shtirthb.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.sheridancollege.shtirthb.dto.PhoneNumberRequest;
import ca.sheridancollege.shtirthb.dto.PhoneVerificationStatusResponse;
import ca.sheridancollege.shtirthb.dto.VerifyPhoneRequest;
import ca.sheridancollege.shtirthb.service.PhoneVerificationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/phone-verification")
public class PhoneVerificationController {

    private final PhoneVerificationService phoneVerificationService;

    public PhoneVerificationController(
            PhoneVerificationService phoneVerificationService) {

        this.phoneVerificationService = phoneVerificationService;
    }

    @GetMapping("/status")
    public ResponseEntity<PhoneVerificationStatusResponse> getStatus(
            Authentication authentication) {

        return ResponseEntity.ok(
                phoneVerificationService.getStatus(
                        authentication.getName()));
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendCode(
            @Valid @RequestBody PhoneNumberRequest request,
            Authentication authentication) {

        phoneVerificationService.startVerification(
                request,
                authentication.getName());

        return ResponseEntity.ok(
                "A verification code has been sent to your phone number.");
    }

    @PostMapping("/verify")
    public ResponseEntity<String> verify(
            @Valid @RequestBody VerifyPhoneRequest request,
            Authentication authentication) {

        phoneVerificationService.verify(
                request,
                authentication.getName());

        return ResponseEntity.ok(
                "Phone number verified successfully.");
    }

    @PostMapping("/resend")
    public ResponseEntity<String> resend(
            Authentication authentication) {

        phoneVerificationService.resend(
                authentication.getName());

        return ResponseEntity.ok(
                "A new verification code has been sent.");
    }
}
