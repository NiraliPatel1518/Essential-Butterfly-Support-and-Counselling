
package ca.sheridancollege.shtirthb.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.sheridancollege.shtirthb.dto.IntakeAdminResponse;
import ca.sheridancollege.shtirthb.dto.RejectIntakeRequest;
import ca.sheridancollege.shtirthb.service.IntakeSubmissionService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/intake-submissions")
public class AdminIntakeSubmissionController {

    private final IntakeSubmissionService intakeSubmissionService;

    public AdminIntakeSubmissionController(
            IntakeSubmissionService intakeSubmissionService) {

        this.intakeSubmissionService = intakeSubmissionService;
    }

    // Admin: retrieve all intake requests.
    @GetMapping
    public ResponseEntity<List<IntakeAdminResponse>> getAllSubmissions() {

        return ResponseEntity.ok(
                intakeSubmissionService.getAllSubmissions()
        );
    }

    // Admin: open a request and move SUBMITTED to IN_PROGRESS.
    @GetMapping("/{id}")
    public ResponseEntity<IntakeAdminResponse> getSubmissionById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                intakeSubmissionService.getSubmissionById(id)
        );
    }

    // Admin: accept a request.
    @PostMapping("/{id}/accept")
    public ResponseEntity<IntakeAdminResponse> acceptSubmission(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                intakeSubmissionService.acceptSubmission(id)
        );
    }

    // Admin: reject a request with a required reason.
    @PostMapping("/{id}/reject")
    public ResponseEntity<IntakeAdminResponse> rejectSubmission(
            @PathVariable Long id,
            @Valid @RequestBody RejectIntakeRequest request) {

        return ResponseEntity.ok(
                intakeSubmissionService.rejectSubmission(
                        id,
                        request.getRejectionReason()
                )
        );
    }
}
