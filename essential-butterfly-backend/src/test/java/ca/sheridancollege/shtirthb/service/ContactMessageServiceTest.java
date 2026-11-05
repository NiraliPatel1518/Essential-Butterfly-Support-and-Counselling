package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ca.sheridancollege.shtirthb.dto.ContactMessageRequest;
import ca.sheridancollege.shtirthb.model.ContactMessage;
import ca.sheridancollege.shtirthb.repository.ContactMessageRepository;

/**
 * UC-02: Submit Contact Message
 *
 * This file has 2 tests:
 *   Test 1 - Message is saved with cleaned-up values and status NEW
 *   Test 2 - Message from a public visitor is saved without a user account
 *
 * The database is mocked, so no real database is needed.
 */
@ExtendWith(MockitoExtension.class)
class ContactMessageServiceTest {

    @Mock
    private ContactMessageRepository contactMessageRepository;

    @InjectMocks
    private ContactMessageService contactMessageService;

    private ContactMessageRequest request(String name, String email,
                                          String subject, String message) {
        ContactMessageRequest r = new ContactMessageRequest();
        r.setFullName(name);
        r.setEmail(email);
        r.setSubject(subject);
        r.setMessage(message);
        return r;
    }

    /*
     * TEST 1: Message is saved correctly
     * Checks that:
     *   - Extra spaces are removed from all fields
     *   - The email is saved in lowercase
     *   - The status is "NEW" and the created date is set
     */
    @Test
    void submitMessage_savesTrimmedValues_withLowercaseEmail_andStatusNew() {
        contactMessageService.submitMessage(request(
                "  Jane Doe  ", "  Jane@Mail.COM ", "  Question  ", "  Hello there  "));

        ArgumentCaptor<ContactMessage> captor = ArgumentCaptor.forClass(ContactMessage.class);
        verify(contactMessageRepository).save(captor.capture());

        ContactMessage saved = captor.getValue();
        assertEquals("Jane Doe", saved.getFullName());
        assertEquals("jane@mail.com", saved.getEmail());
        assertEquals("Question", saved.getSubject());
        assertEquals("Hello there", saved.getMessage());
        assertEquals("NEW", saved.getStatus());
        assertNotNull(saved.getCreatedAt());
    }

    /*
     * TEST 2: Public visitor (not logged in)
     * Checks that:
     *   - The message is saved without a linked user account
     */
    @Test
    void submitMessage_fromPublicVisitor_hasNoLinkedAccount() {
        contactMessageService.submitMessage(request(
                "Sam", "sam@mail.com", "Hi", "Message"));

        ArgumentCaptor<ContactMessage> captor = ArgumentCaptor.forClass(ContactMessage.class);
        verify(contactMessageRepository).save(captor.capture());

        assertNull(captor.getValue().getClientUser());
    }
}
