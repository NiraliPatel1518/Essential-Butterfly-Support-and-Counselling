package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.ContactMessage;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.ContactMessageRepository;
import ca.sheridancollege.shtirthb.service.JwtService;

/*
 * Integration tests for the contact form journey:
 *   a visitor sends a message (no login needed) -> the admin sees it in the
 *   inbox -> opening it marks it as viewed.
 *
 * Real application, real PostgreSQL (Testcontainers), real security rules.
 * Every test is rolled back at the end.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///contact_flow_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890"
})
@AutoConfigureMockMvc
@Transactional
class ContactFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ContactMessageRepository messages;
    @Autowired ClientUserRepository clients;
    @Autowired JwtService jwt;

    // ---------------------------------------------------------------- helpers

    private String admin() {
        return "Bearer " + jwt.generateToken("admin@test.com", "ADMIN");
    }

    private String clientToken() {
        clients.save(new ClientUser("Jane Lee", "jane@mail.com", "hash"));
        return "Bearer " + jwt.generateToken("jane@mail.com", "CLIENT");
    }

    private ResultActions send(String name, String email, String subject, String message)
            throws Exception {
        return mvc.perform(post("/api/contact")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                        "{\"fullName\":\"%s\",\"email\":\"%s\",\"subject\":\"%s\",\"message\":\"%s\"}",
                        name, email, subject, message)));
    }

    private void sendValid(String subject) throws Exception {
        send("Sam Visitor", "sam@mail.com", subject, "I would like to book a call.")
                .andExpect(status().isOk());
    }

    /** Messages sent in the same millisecond have the same time, so wait a little. */
    private void pauseSoTimesDiffer() throws InterruptedException {
        Thread.sleep(25);
    }

    private ContactMessage bySubject(String subject) {
        return messages.findAll().stream()
                .filter(m -> m.getSubject().equals(subject))
                .findFirst().orElseThrow();
    }

    private ResultActions inbox() throws Exception {
        return mvc.perform(get("/api/admin/contact-messages")
                .header(HttpHeaders.AUTHORIZATION, admin()));
    }

    // ------------------------------------------------------ visitor sends a message

    @Test
    void visitor_canSendAMessageWithoutLoggingIn() throws Exception {
        send("Sam Visitor", "sam@mail.com", "Booking", "I would like to book a call.")
                .andExpect(status().isOk())
                .andExpect(content().string("Your message has been submitted successfully"));

        ContactMessage saved = bySubject("Booking");
        assertEquals("Sam Visitor", saved.getFullName());
        assertEquals("NEW", saved.getStatus());
    }

    @Test
    void theMessageIsTrimmedAndTheEmailIsStoredInLowerCase() throws Exception {
        send("  Sam Visitor  ", "  Sam@Mail.COM", "  Booking  ", "  Hello there.  ")
                .andExpect(status().isBadRequest());

        // The email pattern does not allow spaces, so spaces around the email
        // are refused. Spaces around the other fields are removed.
        send("  Sam Visitor  ", "Sam@Mail.COM", "  Booking  ", "  Hello there.  ")
                .andExpect(status().isOk());

        ContactMessage saved = bySubject("Booking");
        assertEquals("Sam Visitor", saved.getFullName());
        assertEquals("sam@mail.com", saved.getEmail());
        assertEquals("Hello there.", saved.getMessage());
    }

    @Test
    void everyFieldIsRequired() throws Exception {
        send("", "sam@mail.com", "Booking", "Hello.").andExpect(status().isBadRequest());
        send("Sam", "", "Booking", "Hello.").andExpect(status().isBadRequest());
        send("Sam", "sam@mail.com", "", "Hello.").andExpect(status().isBadRequest());
        send("Sam", "sam@mail.com", "Booking", "").andExpect(status().isBadRequest());

        assertEquals(0, messages.count());
    }

    @Test
    void anInvalidEmail_isRejected() throws Exception {
        send("Sam", "not-an-email", "Booking", "Hello.").andExpect(status().isBadRequest());

        assertEquals(0, messages.count());
    }

    // ------------------------------------------------------------- admin protection

    @Test
    void theInbox_isOnlyForAdmins() throws Exception {
        sendValid("Booking");

        mvc.perform(get("/api/admin/contact-messages"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/contact-messages")
                        .header(HttpHeaders.AUTHORIZATION, clientToken()))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- admin inbox

    @Test
    void admin_seesTheMessageInTheInbox() throws Exception {
        sendValid("Booking");

        inbox().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subject").value("Booking"))
                .andExpect(jsonPath("$[0].email").value("sam@mail.com"))
                .andExpect(jsonPath("$[0].status").value("NEW"));
    }

    @Test
    void newestMessageIsFirst() throws Exception {
        sendValid("First message");
        pauseSoTimesDiffer();
        sendValid("Second message");

        inbox().andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].subject").value("Second message"))
                .andExpect(jsonPath("$[1].subject").value("First message"));
    }

    @Test
    void lookingAtTheInbox_doesNotMarkMessagesAsViewed() throws Exception {
        sendValid("Booking");

        inbox().andExpect(status().isOk());

        assertEquals("NEW", bySubject("Booking").getStatus());
    }

    @Test
    void openingAMessage_marksItAsViewed() throws Exception {
        sendValid("Booking");
        long id = bySubject("Booking").getId();

        mvc.perform(get("/api/admin/contact-messages/" + id)
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Booking"))
                .andExpect(jsonPath("$.message").value("I would like to book a call."))
                .andExpect(jsonPath("$.status").value("VIEWED"));

        assertEquals("VIEWED", bySubject("Booking").getStatus());
        inbox().andExpect(jsonPath("$[0].status").value("VIEWED"));
    }

    @Test
    void openingAMessageTwice_keepsItViewed() throws Exception {
        sendValid("Booking");
        long id = bySubject("Booking").getId();

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/admin/contact-messages/" + id)
                            .header(HttpHeaders.AUTHORIZATION, admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("VIEWED"));
        }
    }

    @Test
    void openingAMissingMessage_isNotFound() throws Exception {
        mvc.perform(get("/api/admin/contact-messages/999999")
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNotFound());
    }
}