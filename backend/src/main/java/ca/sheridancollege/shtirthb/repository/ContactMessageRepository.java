package ca.sheridancollege.shtirthb.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.ContactMessage;

public interface ContactMessageRepository
        extends JpaRepository<ContactMessage, Long> {
}