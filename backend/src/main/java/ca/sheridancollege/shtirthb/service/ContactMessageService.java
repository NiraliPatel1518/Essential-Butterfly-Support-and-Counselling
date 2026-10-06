package ca.sheridancollege.shtirthb.service;

import org.springframework.stereotype.Service;

import ca.sheridancollege.shtirthb.dto.ContactMessageRequest;
import ca.sheridancollege.shtirthb.model.ContactMessage;
import ca.sheridancollege.shtirthb.repository.ContactMessageRepository;

@Service
public class ContactMessageService {

    private final ContactMessageRepository contactMessageRepository;

    public ContactMessageService(
            ContactMessageRepository contactMessageRepository
    ) {
        this.contactMessageRepository = contactMessageRepository;
    }

    public void submitMessage(ContactMessageRequest request) {

        ContactMessage contactMessage = new ContactMessage(
                request.getFullName().trim(),
                request.getEmail().trim().toLowerCase(),
                request.getSubject().trim(),
                request.getMessage().trim()
        );

        contactMessageRepository.save(contactMessage);
    }
}