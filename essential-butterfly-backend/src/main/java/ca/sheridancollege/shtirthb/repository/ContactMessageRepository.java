
package ca.sheridancollege.shtirthb.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.ContactMessage;

public interface ContactMessageRepository
        extends JpaRepository<ContactMessage, Long> {

    // Admin: view contact messages, newest first.
    Page<ContactMessage> findAllByOrderByCreatedAtDesc(
            Pageable pageable);

    // Admin: filter messages by NEW or VIEWED.
    Page<ContactMessage> findByStatusOrderByCreatedAtDesc(
            String status,
            Pageable pageable);

    // Dashboard: count all contact messages.
    long count();

    // Dashboard: count messages that have not been viewed.
    long countByStatus(String status);
}
