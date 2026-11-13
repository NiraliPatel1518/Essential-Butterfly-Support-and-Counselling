package ca.sheridancollege.shtirthb.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.dto.IntakeAdminResponse;
import ca.sheridancollege.shtirthb.dto.IntakeClientStatusResponse;
import ca.sheridancollege.shtirthb.dto.IntakeSubmissionRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.IntakeSubmission;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.IntakeSubmissionRepository;

@Service
public class IntakeSubmissionService {

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
                        new AppExceptions.ResourceNotFoundException(
                                "User account"
                        )
                );

        // Server-side phone verification enforcement.
        if (!clientUser.isPhoneVerified()) {
            throw new AppExceptions.BusinessRuleException(
                    "Please verify your phone number before submitting an intake request."
            );
        }

        boolean hasPendingSubmission =
                intakeSubmissionRepository
                        .existsByClientUserAndStatusIn(
                                clientUser,
                                PENDING_STATUSES
                        );

        if (hasPendingSubmission) {
            throw new AppExceptions.ConflictException(
                    "You already have an intake request under review. "
                    + "Please wait until a decision has been made."
            );
        }

        IntakeSubmission submission = new IntakeSubmission(
                clientUser,
                request.getClientFullName(),
                request.getParentGuardianName(),
                contactInfoFor(clientUser, request),
                request.getPreferredContactMethod(),
                request.getOverview(),
                request.getSupportNeeds(),
                request.getRespiteGoals(),
                request.getSubjectFocus(),
                request.getAdditionalNotes()
        );

        intakeSubmissionRepository.save(submission);
    }

    /*
     * The contact detail always comes from the account, never from what the
     * browser sent: the signed-in email for "email", the verified phone
     * number for "phone" or "text".
     */
    private String contactInfoFor(
            ClientUser clientUser,
            IntakeSubmissionRequest request) {

        String method = request.getPreferredContactMethod() == null
                ? ""
                : request.getPreferredContactMethod()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        return switch (method) {
            case "email" -> clientUser.getEmail();
            case "phone", "text" -> clientUser.getPhoneNumber();
            default -> throw new AppExceptions.ValidationException(
                    "Please choose email, phone or text as your preferred contact method.");
        };
    }

    // Admin: retrieve all intake requests.
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

    // Admin: reject a pending request.
    @Transactional
    public IntakeAdminResponse rejectSubmission(
            Long id,
            String rejectionReason) {

        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new AppExceptions.ValidationException(
                    "A rejection reason is required."
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

    // Client: retrieve only the status and rejection reason.
    @Transactional(readOnly = true)
    public IntakeClientStatusResponse getClientIntakeStatus(
            String userEmail) {

        ClientUser clientUser = clientUserRepository
                .findByEmail(userEmail)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "User account"
                        )
                );

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

        String status = normalizeStatus(
                latestSubmission.getStatus()
        );

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
                        new AppExceptions.ResourceNotFoundException(
                                "Intake submission"
                        )
                );
    }

    private void validatePendingStatus(
            IntakeSubmission submission) {

        if (!PENDING_STATUSES.contains(submission.getStatus())) {
            throw new AppExceptions.ConflictException(
                    "This intake request has already been decided."
            );
        }
    }

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