
package ca.sheridancollege.shtirthb.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.sheridancollege.shtirthb.dto.IntakeClientStatusResponse;
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

    // Client: submit an intake request.
    @PostMapping
    public ResponseEntity<String> submitIntake(
            @Valid @RequestBody IntakeSubmissionRequest request,
            Authentication authentication) {

        String userEmail = authentication.getName();

        intakeSubmissionService.submitIntake(request, userEmail);

        return ResponseEntity.ok(
                "Intake submission received successfully"
        );
    }

    // Client: retrieve only the status of their latest intake request.
    @GetMapping("/status")
    public ResponseEntity<IntakeClientStatusResponse> getIntakeStatus(
            Authentication authentication) {

        String userEmail = authentication.getName();

        IntakeClientStatusResponse response =
                intakeSubmissionService.getClientIntakeStatus(userEmail);

        if (response == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }
}
