package ca.sheridancollege.shtirthb.service;

import org.springframework.stereotype.Service;

import ca.sheridancollege.shtirthb.dto.IntakeSubmissionRequest;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.IntakeSubmission;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.IntakeSubmissionRepository;

@Service
public class IntakeSubmissionService {

    private final IntakeSubmissionRepository intakeSubmissionRepository;
    private final ClientUserRepository clientUserRepository;

    public IntakeSubmissionService(
            IntakeSubmissionRepository intakeSubmissionRepository,
            ClientUserRepository clientUserRepository) {

        this.intakeSubmissionRepository = intakeSubmissionRepository;
        this.clientUserRepository = clientUserRepository;
    }

    public void submitIntake(
            IntakeSubmissionRequest request,
            String userEmail) {

        ClientUser clientUser = clientUserRepository
                .findByEmail(userEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException("User account not found"));

        boolean alreadySubmitted =
                intakeSubmissionRepository
                        .existsByClientUserAndStatus(clientUser, "NEW");

        if (alreadySubmitted) {
            throw new IllegalArgumentException(
                    "You already have an intake request under review. Please wait for it to be reviewed before submitting another request."
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
}