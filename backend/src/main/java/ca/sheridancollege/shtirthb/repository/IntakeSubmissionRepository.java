
package ca.sheridancollege.shtirthb.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.IntakeSubmission;

public interface IntakeSubmissionRepository
        extends JpaRepository<IntakeSubmission, Long> {

    // Check whether this client has an intake with a given status.
    boolean existsByClientUserAndStatus(
            ClientUser clientUser,
            String status);

    // Prevent duplicate submissions while an existing request is awaiting a decision.
    boolean existsByClientUserAndStatusIn(
            ClientUser clientUser,
            List<String> statuses);

    // Admin: retrieve all intake submissions, newest first.
    Page<IntakeSubmission> findAllByOrderByCreatedAtDesc(
            Pageable pageable);

    // Admin: retrieve submissions by status.
    Page<IntakeSubmission> findByStatusOrderByCreatedAtDesc(
            String status,
            Pageable pageable);

    // Client: retrieve only their own submissions.
    Page<IntakeSubmission> findByClientUserOrderByCreatedAtDesc(
            ClientUser clientUser,
            Pageable pageable);

    // Dashboard: count new submissions awaiting review.
    long countByStatus(String status);
}
