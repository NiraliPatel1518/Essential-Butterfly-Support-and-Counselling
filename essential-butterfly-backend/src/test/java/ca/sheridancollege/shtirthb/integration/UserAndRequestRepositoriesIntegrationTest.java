package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.ContactMessage;
import ca.sheridancollege.shtirthb.model.IntakeSubmission;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.ContactMessageRepository;
import ca.sheridancollege.shtirthb.repository.IntakeSubmissionRepository;

class UserAndRequestRepositoriesIntegrationTest extends RepositoryIntegrationTestBase {

    @Autowired
    private AdminUserRepository adminUsers;

    @Autowired
    private ClientUserRepository clients;

    @Autowired
    private ContactMessageRepository contactMessages;

    @Autowired
    private IntakeSubmissionRepository intakes;

    private ClientUser client(String email) {
        return clients.save(new ClientUser("Client " + email, email, "hash"));
    }

    private ContactMessage message(String subject, String status, LocalDateTime createdAt) {
        ContactMessage message = new ContactMessage("Sam", "sam@mail.com", subject, "Hello");
        message.setStatus(status);
        message.setCreatedAt(createdAt);
        return contactMessages.save(message);
    }

    private IntakeSubmission intake(ClientUser owner, String name, String status, LocalDateTime createdAt) {
        IntakeSubmission intake = new IntakeSubmission(
                owner, name, "Parent", "contact", "Email",
                "overview", "needs", "goals", "focus", "notes");
    
        intake.setStatus(status);
    
        if ("REJECTED".equals(status)) {
            intake.setRejectionReason("The request was not approved.");
        }
    
        intake.setCreatedAt(createdAt);
    
        return intakes.save(intake);
    }

    /*
     * Checks: An admin can be found by email.
     * How:    findByEmail and existsByEmail return true for the saved email and false for an unknown
     *   one.
     */
    @Test
    void findsAnAdminByEmail() {
        adminUsers.save(new AdminUser("Admin", "new-admin@mail.com", "hash"));

        assertTrue(adminUsers.findByEmail("new-admin@mail.com").isPresent());
        assertTrue(adminUsers.existsByEmail("new-admin@mail.com"));
        assertFalse(adminUsers.existsByEmail("nobody@mail.com"));
        assertTrue(adminUsers.findByEmail("nobody@mail.com").isEmpty());
    }

    /*
     * Checks: A client can be found by email.
     * How:    findByEmail and existsByEmail return true for the saved email and false for an unknown
     *   one.
     */
    @Test
    void findsAClientByEmail() {
        client("lee@mail.com");

        assertTrue(clients.findByEmail("lee@mail.com").isPresent());
        assertTrue(clients.existsByEmail("lee@mail.com"));
        assertFalse(clients.existsByEmail("nobody@mail.com"));
    }

    /*
     * Checks: The database refuses two clients with the same email.
     * How:    The second saveAndFlush throws DataIntegrityViolationException.
     */
    @Test
    void databaseRejectsTwoClientsWithTheSameEmail() {
        client("same@mail.com");

        assertThrows(DataIntegrityViolationException.class, () ->
                clients.saveAndFlush(new ClientUser("Other", "same@mail.com", "hash")));
    }

    /*
     * Checks: Contact messages are listed newest first, in pages.
     * How:    We save three messages. Page 0 with size 2 gives new and mid, page 1 gives old, and
     *   there are 2 pages.
     */
    @Test
    void listsContactMessagesNewestFirstWithPaging() {
        LocalDateTime now = LocalDateTime.now();
        message("old", "NEW", now.minusHours(3));
        message("mid", "NEW", now.minusHours(2));
        message("new", "NEW", now.minusHours(1));

        Page<ContactMessage> first = contactMessages.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 2));
        Page<ContactMessage> second = contactMessages.findAllByOrderByCreatedAtDesc(PageRequest.of(1, 2));

        assertEquals(List.of("new", "mid"),
                first.getContent().stream().map(ContactMessage::getSubject).toList());
        assertEquals(List.of("old"),
                second.getContent().stream().map(ContactMessage::getSubject).toList());
        assertEquals(2, first.getTotalPages());
    }

    /*
     * Checks: Contact messages can be filtered and counted by status.
     * How:    We save two NEW and one VIEWED. The counts go up by 2 and 1, and the VIEWED page
     *   contains c but not a.
     */
    @Test
    void filtersAndCountsContactMessagesByStatus() {
        long newBefore = contactMessages.countByStatus("NEW");
        long viewedBefore = contactMessages.countByStatus("VIEWED");
        long allBefore = contactMessages.count();
        LocalDateTime now = LocalDateTime.now();
        message("a", "NEW", now.minusHours(2));
        message("b", "NEW", now.minusHours(1));
        message("c", "VIEWED", now);

        assertEquals(newBefore + 2, contactMessages.countByStatus("NEW"));
        assertEquals(viewedBefore + 1, contactMessages.countByStatus("VIEWED"));
        assertEquals(allBefore + 3, contactMessages.count());

        Page<ContactMessage> viewed = contactMessages
                .findByStatusOrderByCreatedAtDesc("VIEWED", PageRequest.of(0, 10));
        assertTrue(viewed.getContent().stream().anyMatch(m -> m.getSubject().equals("c")));
        assertTrue(viewed.getContent().stream().noneMatch(m -> m.getSubject().equals("a")));
    }

    /*
     * Checks: The repository can tell whether a client has an intake with a given status.
     * How:    We save a SUBMITTED intake for the owner. The existence queries are true for the owner
     *   and SUBMITTED, and false for ACCEPTED, REJECTED, or for another client.
     */
    @Test
    void checksWhetherAClientHasAnIntakeWithAGivenStatus() {
        ClientUser owner = client("owner@mail.com");
        ClientUser other = client("other@mail.com");
        intake(owner, "Riley", "SUBMITTED", LocalDateTime.now());

        assertTrue(intakes.existsByClientUserAndStatus(owner, "SUBMITTED"));
        assertFalse(intakes.existsByClientUserAndStatus(owner, "ACCEPTED"));
        assertFalse(intakes.existsByClientUserAndStatus(other, "SUBMITTED"));

        assertTrue(intakes.existsByClientUserAndStatusIn(owner, List.of("SUBMITTED", "ACCEPTED")));
        assertFalse(intakes.existsByClientUserAndStatusIn(owner, List.of("REJECTED")));
        assertFalse(intakes.existsByClientUserAndStatusIn(other, List.of("SUBMITTED", "ACCEPTED")));
    }

    /*
     * Checks: A client only sees their own intakes, newest first.
     * How:    Ann has two intakes and Bob has one. The query for Ann returns Ann newer, then Ann
     *   older.
     */
    @Test
    void aClientOnlySeesTheirOwnIntakesNewestFirst() {
        ClientUser ann = client("ann@mail.com");
        ClientUser bob = client("bob@mail.com");
        LocalDateTime now = LocalDateTime.now();
        intake(ann, "Ann older", "REJECTED", now.minusDays(2));
        intake(ann, "Ann newer", "SUBMITTED", now.minusDays(1));
        intake(bob, "Bob only", "SUBMITTED", now);

        Page<IntakeSubmission> result =
                intakes.findByClientUserOrderByCreatedAtDesc(ann, PageRequest.of(0, 10));

        assertEquals(List.of("Ann newer", "Ann older"),
                result.getContent().stream().map(IntakeSubmission::getClientFullName).toList());
    }

    /*
     * Checks: The admin can list and count intakes by status.
     * How:    We save two SUBMITTED and one ACCEPTED. The counts go up by 2 and 1, the total goes up
     *   by 3, and the SUBMITTED list shows Two then One.
     */
    @Test
    void adminCanListAndCountIntakesByStatus() {
        ClientUser owner = client("lister@mail.com");
        long submittedBefore = intakes.countByStatus("SUBMITTED");
        long acceptedBefore = intakes.countByStatus("ACCEPTED");
        long allBefore = intakes.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 1)).getTotalElements();
        LocalDateTime now = LocalDateTime.now();
        intake(owner, "One", "SUBMITTED", now.minusDays(2));
        intake(owner, "Two", "SUBMITTED", now.minusDays(1));
        intake(owner, "Three", "ACCEPTED", now);

        assertEquals(submittedBefore + 2, intakes.countByStatus("SUBMITTED"));
        assertEquals(acceptedBefore + 1, intakes.countByStatus("ACCEPTED"));
        assertEquals(allBefore + 3,
                intakes.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 1)).getTotalElements());

        Page<IntakeSubmission> submitted =
                intakes.findByStatusOrderByCreatedAtDesc("SUBMITTED", PageRequest.of(0, 50));
        List<String> names = submitted.getContent().stream()
                .map(IntakeSubmission::getClientFullName)
                .filter(name -> name.equals("One") || name.equals("Two") || name.equals("Three"))
                .toList();
        assertEquals(List.of("Two", "One"), names);
    }
}