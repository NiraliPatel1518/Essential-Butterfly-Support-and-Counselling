package ca.sheridancollege.shtirthb.controller;

import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ca.sheridancollege.shtirthb.dto.ResetPasswordRequest;
import ca.sheridancollege.shtirthb.service.PasswordResetService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/password")
public class PasswordResetController {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile(
                    "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
            );

    private final PasswordResetService passwordResetService;

    public PasswordResetController(
            PasswordResetService passwordResetService) {

        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/forgot")
    public ResponseEntity<String> forgotPassword(
            @RequestParam String type,
            @RequestParam String email) {

        String normalizedEmail = email.trim().toLowerCase();

        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {

            return ResponseEntity.badRequest()
                    .body("Please enter a valid email address");
        }

        if ("CLIENT".equalsIgnoreCase(type)) {

            passwordResetService.requestClientPasswordReset(
                    normalizedEmail
            );

        } else if ("ADMIN".equalsIgnoreCase(type)) {

            passwordResetService.requestAdminPasswordReset(
                    normalizedEmail
            );

        } else {

            return ResponseEntity.badRequest()
                    .body("Invalid account type");
        }

        return ResponseEntity.ok(
                "If an account exists with this email, a password reset link has been generated."
        );
    }

    @PostMapping("/reset")
    public ResponseEntity<String> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        if (!request.getNewPassword()
                .equals(request.getConfirmPassword())) {

            return ResponseEntity.badRequest()
                    .body("Passwords do not match");
        }

        boolean resetSuccessful =
                passwordResetService.resetPassword(
                        request.getToken(),
                        request.getNewPassword()
                );

        if (!resetSuccessful) {

            return ResponseEntity.badRequest()
                    .body("Invalid, expired, or already used reset token");
        }

        return ResponseEntity.ok(
                "Password reset successfully"
        );
    }
}