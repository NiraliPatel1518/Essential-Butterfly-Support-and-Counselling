package ca.sheridancollege.shtirthb.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import ca.sheridancollege.shtirthb.dto.IntakeSubmissionRequest;
import ca.sheridancollege.shtirthb.service.IntakeSubmissionService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/intake")
public class IntakeSubmissionController {

    private final IntakeSubmissionService intakeSubmissionService;

    public IntakeSubmissionController(
            IntakeSubmissionService intakeSubmissionService) {
        this.intakeSubmissionService = intakeSubmissionService;
    }

    @PostMapping
    public ResponseEntity<String> submitIntake(
            @Valid @RequestBody IntakeSubmissionRequest request,
            Authentication authentication) {

        try {
            String userEmail = authentication.getName();

            intakeSubmissionService.submitIntake(
                    request,
                    userEmail
            );

            return ResponseEntity.ok(
                    "Intake submission received successfully"
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }
}