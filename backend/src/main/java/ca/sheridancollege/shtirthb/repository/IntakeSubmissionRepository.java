package ca.sheridancollege.shtirthb.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.IntakeSubmission;

public interface IntakeSubmissionRepository extends JpaRepository<IntakeSubmission, Long> {

    boolean existsByClientUserAndStatus(
            ClientUser clientUser,
            String status
    );
}