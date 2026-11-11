
package ca.sheridancollege.shtirthb.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import ca.sheridancollege.shtirthb.dto.ContactMessageRequest;
import ca.sheridancollege.shtirthb.dto.ContactMessageResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.ContactMessage;
import ca.sheridancollege.shtirthb.repository.ContactMessageRepository;

@Service
public class ContactMessageService {

    private final ContactMessageRepository contactMessageRepository;

    public ContactMessageService(
            ContactMessageRepository contactMessageRepository) {

        this.contactMessageRepository = contactMessageRepository;
    }

    // Client: submit a contact message.
    public void submitMessage(ContactMessageRequest request) {

        ContactMessage contactMessage = new ContactMessage(
                request.getFullName().trim(),
                request.getEmail().trim().toLowerCase(),
                request.getSubject().trim(),
                request.getMessage().trim()
        );

        contactMessageRepository.save(contactMessage);
    }

    // Admin: retrieve all messages without changing their status.
    @Transactional(readOnly = true)
    public List<ContactMessageResponse> getAllMessages() {

        return contactMessageRepository
                .findAllByOrderByCreatedAtDesc(Pageable.unpaged())
                .getContent()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // Admin: open one message and mark it as VIEWED.
    @Transactional
    public ContactMessageResponse getMessageById(Long id) {

        ContactMessage contactMessage = contactMessageRepository
                .findById(id)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Contact message"
                        )
                );

        if ("NEW".equals(contactMessage.getStatus())) {
            contactMessage.setStatus("VIEWED");
            contactMessageRepository.save(contactMessage);
        }

        return mapToResponse(contactMessage);
    }

    private ContactMessageResponse mapToResponse(
            ContactMessage message) {

        return new ContactMessageResponse(
                message.getId(),
                message.getFullName(),
                message.getEmail(),
                message.getSubject(),
                message.getMessage(),
                message.getStatus(),
                message.getCreatedAt()
        );
    }
}
