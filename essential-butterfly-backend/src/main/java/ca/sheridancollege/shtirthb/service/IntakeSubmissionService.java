
package ca.sheridancollege.shtirthb.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.dto.IntakeAdminResponse;
import ca.sheridancollege.shtirthb.dto.IntakeClientStatusResponse;
import ca.sheridancollege.shtirthb.dto.IntakeSubmissionRequest;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.IntakeSubmission;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.IntakeSubmissionRepository;

@Service
public class IntakeSubmissionService {

    // NEW is included for older records already saved in the database.
    private static final List<String> PENDING_STATUSES =
            Arrays.asList("NEW", "SUBMITTED", "IN_PROGRESS");

    private final IntakeSubmissionRepository intakeSubmissionRepository;
    private final ClientUserRepository clientUserRepository;

    public IntakeSubmissionService(
            IntakeSubmissionRepository intakeSubmissionRepository,
            ClientUserRepository clientUserRepository) {

        this.intakeSubmissionRepository = intakeSubmissionRepository;
        this.clientUserRepository = clientUserRepository;
    }

    // Client: submit a new intake request.
    public void submitIntake(
            IntakeSubmissionRequest request,
            String userEmail) {

        ClientUser clientUser = clientUserRepository
                .findByEmail(userEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException("User account not found"));

        boolean hasPendingSubmission =
                intakeSubmissionRepository
                        .existsByClientUserAndStatusIn(
                                clientUser,
                                PENDING_STATUSES
                        );

        if (hasPendingSubmission) {
            throw new IllegalArgumentException(
                    "You already have an intake request under review. "
                    + "Please wait until a decision has been made."
            );
        }

        IntakeSubmission submission = new IntakeSubmission(
                clientUser,
                request.getClientFullName(),
                request.getParentGuardianName(),
                request.getContactInfo(),
                request.getPreferredContactMethod(),
                request.getOverview(),
                request.getSupportNeeds(),
                request.getRespiteGoals(),
                request.getSubjectFocus(),
                request.getAdditionalNotes()
        );

        intakeSubmissionRepository.save(submission);
    }

    // Admin: retrieve all intake requests without changing their status.
    @Transactional(readOnly = true)
    public List<IntakeAdminResponse> getAllSubmissions() {

        return intakeSubmissionRepository
                .findAllByOrderByCreatedAtDesc(Pageable.unpaged())
                .getContent()
                .stream()
                .map(this::mapToAdminResponse)
                .toList();
    }

    // Admin: opening a request changes NEW or SUBMITTED to IN_PROGRESS.
    @Transactional
    public IntakeAdminResponse getSubmissionById(Long id) {

        IntakeSubmission submission = findSubmission(id);

        if ("NEW".equals(submission.getStatus())
                || "SUBMITTED".equals(submission.getStatus())) {

            submission.setStatus("IN_PROGRESS");
            submission = intakeSubmissionRepository.save(submission);
        }

        return mapToAdminResponse(submission);
    }

    // Admin: accept a pending request.
    @Transactional
    public IntakeAdminResponse acceptSubmission(Long id) {

        IntakeSubmission submission = findSubmission(id);

        validatePendingStatus(submission);

        submission.setStatus("ACCEPTED");
        submission.setRejectionReason(null);

        return mapToAdminResponse(
                intakeSubmissionRepository.save(submission)
        );
    }

    // Admin: reject a pending request with a required reason.
    @Transactional
    public IntakeAdminResponse rejectSubmission(
            Long id,
            String rejectionReason) {

        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException(
                    "A rejection reason is required"
            );
        }

        IntakeSubmission submission = findSubmission(id);

        validatePendingStatus(submission);

        submission.setStatus("REJECTED");
        submission.setRejectionReason(rejectionReason.trim());

        return mapToAdminResponse(
                intakeSubmissionRepository.save(submission)
        );
    }

    // Client: retrieve only the status and, if rejected, the rejection reason.
    @Transactional(readOnly = true)
    public IntakeClientStatusResponse getClientIntakeStatus(
            String userEmail) {

        ClientUser clientUser = clientUserRepository
                .findByEmail(userEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException("User account not found"));

        List<IntakeSubmission> submissions =
                intakeSubmissionRepository
                        .findByClientUserOrderByCreatedAtDesc(
                                clientUser,
                                Pageable.ofSize(1)
                        )
                        .getContent();

        if (submissions.isEmpty()) {
            return null;
        }

        IntakeSubmission latestSubmission = submissions.get(0);

        String status = normalizeStatus(latestSubmission.getStatus());

        String rejectionReason = "REJECTED".equals(status)
                ? latestSubmission.getRejectionReason()
                : null;

        return new IntakeClientStatusResponse(
                status,
                rejectionReason
        );
    }

    private IntakeSubmission findSubmission(Long id) {

        return intakeSubmissionRepository
                .findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Intake submission not found"
                        ));
    }

    private void validatePendingStatus(IntakeSubmission submission) {

        if (!PENDING_STATUSES.contains(submission.getStatus())) {
            throw new IllegalArgumentException(
                    "This intake request has already been decided"
            );
        }
    }

    // Convert legacy database status NEW into the current status SUBMITTED.
    private String normalizeStatus(String status) {

        if ("NEW".equals(status)) {
            return "SUBMITTED";
        }

        return status;
    }

    private IntakeAdminResponse mapToAdminResponse(
            IntakeSubmission submission) {

        return new IntakeAdminResponse(
                submission.getId(),
                submission.getClientFullName(),
                submission.getParentGuardianName(),
                submission.getContactInfo(),
                submission.getPreferredContactMethod(),
                submission.getOverview(),
                submission.getSupportNeeds(),
                submission.getRespiteGoals(),
                submission.getSubjectFocus(),
                submission.getAdditionalNotes(),
                normalizeStatus(submission.getStatus()),
                submission.getRejectionReason(),
                submission.getCreatedAt()
        );
    }
}
