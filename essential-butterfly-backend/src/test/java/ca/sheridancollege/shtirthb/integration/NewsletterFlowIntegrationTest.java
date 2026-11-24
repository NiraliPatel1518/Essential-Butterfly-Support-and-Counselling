package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.NewsletterCampaign;
import ca.sheridancollege.shtirthb.model.NewsletterSubscriber;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.NewsletterCampaignRepository;
import ca.sheridancollege.shtirthb.repository.NewsletterSubscriberRepository;
import ca.sheridancollege.shtirthb.service.JwtService;
import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/*
 * Integration tests for the newsletter journey:
 *   visitor subscribes -> admin writes a draft -> admin previews and sends it
 *   -> failed deliveries are retried -> visitor unsubscribes with the link.
 *
 * Real application, real PostgreSQL (Testcontainers), real security rules.
 * The mail sender is replaced by a mock, so no real email is ever sent.
 * Every test is rolled back at the end.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///newsletter_flow_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890",
        "app.mail.from-address=noreply@butterfly.test"
})
@AutoConfigureMockMvc
@Transactional
class NewsletterFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired NewsletterSubscriberRepository subscribers;
    @Autowired NewsletterCampaignRepository campaigns;
    @Autowired ClientUserRepository clients;
    @Autowired JwtService jwt;

    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void setUp() throws Exception {
        when(mailSender.createMimeMessage())
                .thenAnswer(call -> new MimeMessage((Session) null));

        // The website asks for /api/features when it opens. This also creates the
        // default settings (all features on), which the newsletter depends on.
        mvc.perform(get("/api/features")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- helpers

    private String admin() {
        return "Bearer " + jwt.generateToken("admin@test.com", "ADMIN");
    }

    private String clientToken() {
        clients.save(new ClientUser("Jane Lee", "jane@mail.com", "hash"));
        return "Bearer " + jwt.generateToken("jane@mail.com", "CLIENT");
    }

    private ResultActions subscribe(String email, boolean consent) throws Exception {
        return mvc.perform(post("/api/newsletter/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\":\"%s\",\"consent\":%s}", email, consent)));
    }

    private ResultActions adminGet(String path) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, admin()));
    }

    private ResultActions adminPost(String path) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, admin()));
    }

    private String draftJson(String subject, String content) {
        return String.format("{\"subject\":\"%s\",\"content\":\"%s\"}", subject, content);
    }

    private ResultActions createDraft(String subject, String content) throws Exception {
        return mvc.perform(post("/api/admin/newsletter")
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(draftJson(subject, content)));
    }

    private long newDraft() throws Exception {
        createDraft("November news", "Hello everyone, here is our news.")
                .andExpect(status().isCreated());
        return campaigns.findAll().get(0).getId();
    }

    private NewsletterSubscriber addSubscriber(String email) {
        return subscribers.save(new NewsletterSubscriber(email));
    }

    private NewsletterSubscriber find(String email) {
        return subscribers.findByEmailIgnoreCase(email).orElseThrow();
    }

    private NewsletterCampaign campaign(long id) {
        return campaigns.findById(id).orElseThrow();
    }

    // ------------------------------------------------------- visitor subscribes

    /*
     * Checks: A visitor can subscribe to the newsletter with consent.
     * How:    The answer is 200 with success true. In the database the subscriber is active and
     *   consentGiven is true.
     */
    @Test
    void visitor_canSubscribeWithConsent() throws Exception {
        subscribe("fan@mail.com", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        NewsletterSubscriber saved = find("fan@mail.com");
        assertTrue(saved.isActive());
        assertTrue(saved.isConsentGiven());
    }

    /*
     * Checks: The email is trimmed and saved in lower case.
     * How:    We send " Fan@Mail.COM ". The saved email is fan@mail.com and there is only 1
     *   subscriber.
     */
    @Test
    void emailIsTrimmedAndStoredInLowerCase() throws Exception {
        subscribe("  Fan@Mail.COM  ", true).andExpect(status().isOk());

        assertEquals("fan@mail.com", find("fan@mail.com").getEmail());
        assertEquals(1, subscribers.count());
    }

    /*
     * Checks: An invalid email is refused.
     * How:    The answer is 400 and no subscriber is saved.
     */
    @Test
    void invalidEmail_isRejected() throws Exception {
        subscribe("not-an-email", true).andExpect(status().isBadRequest());

        assertEquals(0, subscribers.count());
    }

    /*
     * Checks: Without consent, nobody is subscribed.
     * How:    Consent false gives 400 and no subscriber is saved.
     */
    @Test
    void withoutConsent_visitorIsNotSubscribed() throws Exception {
        subscribe("fan@mail.com", false).andExpect(status().isBadRequest());

        assertEquals(0, subscribers.count());
    }

    /*
     * Checks: Subscribing twice keeps only one subscriber.
     * How:    Both requests give 200, and the table has 1 row.
     */
    @Test
    void subscribingTwice_keepsOnlyOneSubscriber() throws Exception {
        subscribe("fan@mail.com", true).andExpect(status().isOk());
        subscribe("fan@mail.com", true).andExpect(status().isOk());

        assertEquals(1, subscribers.count());
    }

    /*
     * Checks: When the admin turns the newsletter feature off, visitors cannot subscribe.
     * How:    The admin sends the feature list with NEWSLETTER set to false. A subscribe request then
     *   gives 404 and nothing is saved.
     */
    @Test
    void whenTheAdminTurnsTheNewsletterOff_visitorsCannotSubscribe() throws Exception {
        mvc.perform(put("/api/admin/features")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"featureKey\":\"NEWSLETTER\",\"enabled\":false},"
                                + "{\"featureKey\":\"TESTIMONIALS\",\"enabled\":true},"
                                + "{\"featureKey\":\"UPDATES\",\"enabled\":true},"
                                + "{\"featureKey\":\"APPOINTMENTS\",\"enabled\":true},"
                                + "{\"featureKey\":\"MEMORY_WALL\",\"enabled\":true}]"))
                .andExpect(status().isOk());

        subscribe("fan@mail.com", true).andExpect(status().isNotFound());

        assertEquals(0, subscribers.count());
    }

    // ----------------------------------------------------------- unsubscribing

    /*
     * Checks: The unsubscribe link turns the subscription off.
     * How:    We use the saved token on the unsubscribe URL. The answer has success true, and the
     *   subscriber is no longer active.
     */
    @Test
    void unsubscribeLink_turnsTheSubscriptionOff() throws Exception {
        subscribe("fan@mail.com", true).andExpect(status().isOk());
        String token = find("fan@mail.com").getUnsubscribeToken();

        mvc.perform(get("/api/newsletter/unsubscribe").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertFalse(find("fan@mail.com").isActive());
    }

    /*
     * Checks: An unknown unsubscribe token gives 404.
     * How:    We use "not-a-real-token".
     */
    @Test
    void unknownUnsubscribeLink_isNotFound() throws Exception {
        mvc.perform(get("/api/newsletter/unsubscribe").param("token", "not-a-real-token"))
                .andExpect(status().isNotFound());
    }

    /*
     * Checks: After unsubscribing, the visitor can subscribe again.
     * How:    The subscriber becomes active again, there is still 1 row, and the new unsubscribe token
     *   is different from the old one.
     */
    @Test
    void afterUnsubscribing_visitorCanSubscribeAgain() throws Exception {
        subscribe("fan@mail.com", true).andExpect(status().isOk());
        String oldToken = find("fan@mail.com").getUnsubscribeToken();
        mvc.perform(get("/api/newsletter/unsubscribe").param("token", oldToken))
                .andExpect(status().isOk());

        subscribe("fan@mail.com", true).andExpect(status().isOk());

        NewsletterSubscriber again = find("fan@mail.com");
        assertTrue(again.isActive());
        assertEquals(1, subscribers.count());
        // The old link must not work for the new subscription.
        assertFalse(oldToken.equals(again.getUnsubscribeToken()));
    }

    // --------------------------------------------------------- admin protection

    /*
     * Checks: The admin newsletter pages need an admin login.
     * How:    No token gives 401. A client token gives 403.
     */
    @Test
    void adminNewsletterPages_needAnAdminLogin() throws Exception {
        mvc.perform(get("/api/admin/newsletter")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/newsletter")
                        .header(HttpHeaders.AUTHORIZATION, clientToken()))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ admin drafts

    /*
     * Checks: The admin overview counts only active subscribers.
     * How:    We add three subscribers and unsubscribe one. The overview shows activeSubscribers 2.
     */
    @Test
    void adminOverview_countsOnlyActiveSubscribers() throws Exception {
        addSubscriber("one@mail.com");
        addSubscriber("two@mail.com");
        NewsletterSubscriber gone = addSubscriber("gone@mail.com");
        gone.unsubscribe();
        subscribers.save(gone);

        adminGet("/api/admin/newsletter")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSubscribers").value(2));
    }

    /*
     * Checks: The admin can create a draft.
     * How:    The answer is 201 with status DRAFT and the subject.
     */
    @Test
    void admin_canCreateADraft() throws Exception {
        createDraft("November news", "Hello everyone.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.subject").value("November news"));
    }

    /*
     * Checks: A draft without a subject or content is refused.
     * How:    An empty subject and an empty content each give 400, and no campaign is saved.
     */
    @Test
    void draftWithoutSubjectOrContent_isRejected() throws Exception {
        createDraft("", "Hello everyone.").andExpect(status().isBadRequest());
        createDraft("November news", "").andExpect(status().isBadRequest());

        assertEquals(0, campaigns.count());
    }

    /*
     * Checks: The admin can edit a draft.
     * How:    A PUT with a new subject and content gives 200. The database has the new content.
     */
    @Test
    void admin_canEditADraft() throws Exception {
        long id = newDraft();

        mvc.perform(put("/api/admin/newsletter/" + id)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftJson("New subject", "New content.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("New subject"));

        assertEquals("New content.", campaign(id).getContent());
    }

    /*
     * Checks: Editing a draft that does not exist gives 404.
     * How:    We use id 999999.
     */
    @Test
    void editingAMissingDraft_isNotFound() throws Exception {
        mvc.perform(put("/api/admin/newsletter/999999")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftJson("Subject", "Content.")))
                .andExpect(status().isNotFound());
    }

    /*
     * Checks: The preview shows the draft and how many people will get it.
     * How:    With two active subscribers, the preview shows the subject and activeRecipientCount 2.
     */
    @Test
    void preview_showsTheDraftAndHowManyPeopleWillGetIt() throws Exception {
        addSubscriber("one@mail.com");
        addSubscriber("two@mail.com");
        long id = newDraft();

        adminGet("/api/admin/newsletter/" + id + "/preview")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaign.subject").value("November news"))
                .andExpect(jsonPath("$.activeRecipientCount").value(2));
    }

    // ----------------------------------------------------------------- sending

    /*
     * Checks: Sending with no subscribers is refused.
     * How:    The answer is 409, the campaign stays DRAFT, and the fake mail sender is never called.
     */
    @Test
    void sendingWithoutSubscribers_isRefused() throws Exception {
        long id = newDraft();

        adminPost("/api/admin/newsletter/" + id + "/send")
                .andExpect(status().isConflict());

        assertEquals("DRAFT", campaign(id).getStatus());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    /*
     * Checks: Sending emails every active subscriber once.
     * How:    The answer shows SENT with 2 recipients, 2 successes and 0 failures. The fake mail
     *   sender is called twice, and the two recipients are one@mail.com and two@mail.com.
     */
    @Test
    void sending_emailsEveryActiveSubscriberOnce() throws Exception {
        addSubscriber("one@mail.com");
        addSubscriber("two@mail.com");
        long id = newDraft();

        adminPost("/api/admin/newsletter/" + id + "/send")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.recipientCount").value(2))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failedCount").value(0));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(2)).send(sent.capture());

        Set<String> recipients = new HashSet<>();
        for (MimeMessage message : sent.getAllValues()) {
            for (Address address : message.getAllRecipients()) {
                recipients.add(address.toString());
            }
        }
        assertEquals(Set.of("one@mail.com", "two@mail.com"), recipients);
    }

    /*
     * Checks: People who unsubscribed do not get the newsletter.
     * How:    Only stays@mail.com gets an email, and recipientCount is 1.
     */
    @Test
    void unsubscribedPeople_doNotGetTheNewsletter() throws Exception {
        addSubscriber("stays@mail.com");
        NewsletterSubscriber gone = addSubscriber("gone@mail.com");
        gone.unsubscribe();
        subscribers.save(gone);
        long id = newDraft();

        adminPost("/api/admin/newsletter/" + id + "/send")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientCount").value(1));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(sent.capture());
        assertEquals("stays@mail.com",
                sent.getValue().getAllRecipients()[0].toString());
    }

    /*
     * Checks: A newsletter can only be sent once.
     * How:    The second send gives 409, and the fake sender was called only once in total.
     */
    @Test
    void aNewsletterCanOnlyBeSentOnce() throws Exception {
        addSubscriber("one@mail.com");
        long id = newDraft();
        adminPost("/api/admin/newsletter/" + id + "/send").andExpect(status().isOk());

        adminPost("/api/admin/newsletter/" + id + "/send")
                .andExpect(status().isConflict());

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    /*
     * Checks: A sent newsletter cannot be edited.
     * How:    The edit gives 409, and the subject in the database is unchanged.
     */
    @Test
    void aSentNewsletter_cannotBeEdited() throws Exception {
        addSubscriber("one@mail.com");
        long id = newDraft();
        adminPost("/api/admin/newsletter/" + id + "/send").andExpect(status().isOk());

        mvc.perform(put("/api/admin/newsletter/" + id)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftJson("Changed", "Changed content.")))
                .andExpect(status().isConflict());

        assertEquals("November news", campaign(id).getSubject());
    }

    /*
     * Checks: The delivery results list each recipient.
     * How:    After sending to two people, the deliveries list has 2 items, both with status SENT.
     */
    @Test
    void deliveryResults_listEachRecipient() throws Exception {
        addSubscriber("one@mail.com");
        addSubscriber("two@mail.com");
        long id = newDraft();
        adminPost("/api/admin/newsletter/" + id + "/send").andExpect(status().isOk());

        adminGet("/api/admin/newsletter/" + id + "/deliveries")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveries.length()").value(2))
                .andExpect(jsonPath("$.deliveries[0].status").value("SENT"))
                .andExpect(jsonPath("$.deliveries[1].status").value("SENT"));
    }

    // ------------------------------------------------------------ failed mails

    /*
     * Checks: If one email fails, the newsletter is PARTIAL and the failure is recorded.
     * How:    The fake sender throws MailSendException for the first email and works for the next. The
     *   answer shows PARTIAL with 1 success and 1 failure, and 2 delivery rows.
     */
    @Test
    void ifOneEmailFails_theNewsletterIsPartialAndTheFailureIsRecorded() throws Exception {
        addSubscriber("one@mail.com");
        addSubscriber("two@mail.com");
        long id = newDraft();
        // The first email fails, the next one works.
        doThrow(new MailSendException("mail server down"))
                .doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        adminPost("/api/admin/newsletter/" + id + "/send")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(1));

        adminGet("/api/admin/newsletter/" + id + "/deliveries")
                .andExpect(jsonPath("$.deliveries.length()").value(2));
    }

    /*
     * Checks: If every email fails, the newsletter is marked FAILED.
     * How:    The fake sender always throws. The answer shows FAILED with failedCount 1.
     */
    @Test
    void whenEveryEmailFails_theNewsletterIsMarkedFailed() throws Exception {
        addSubscriber("one@mail.com");
        long id = newDraft();
        doThrow(new MailSendException("mail server down"))
                .when(mailSender).send(any(MimeMessage.class));

        adminPost("/api/admin/newsletter/" + id + "/send")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failedCount").value(1));
    }

    /*
     * Checks: A retry sends only to the people whose email failed.
     * How:    After one failure, the fake sender works again. The retry gives SENT with 2 successes
     *   and 0 failures, and the sender was called 3 times in total (2 first, 1 retry).
     */
    @Test
    void retry_sendsOnlyToThePeopleWhoFailed() throws Exception {
        addSubscriber("one@mail.com");
        addSubscriber("two@mail.com");
        long id = newDraft();
        doThrow(new MailSendException("mail server down"))
                .doNothing()
                .when(mailSender).send(any(MimeMessage.class));
        adminPost("/api/admin/newsletter/" + id + "/send").andExpect(status().isOk());

        // The mail server is back. Only the failed person gets another email.
        doNothing().when(mailSender).send(any(MimeMessage.class));

        adminPost("/api/admin/newsletter/" + id + "/retry-failed")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failedCount").value(0));

        // 2 emails in the first send + 1 retry.
        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    /*
     * Checks: A retry with nothing failed is refused.
     * How:    After a fully successful send, retry-failed gives 409.
     */
    @Test
    void retryWithNothingFailed_isRefused() throws Exception {
        addSubscriber("one@mail.com");
        long id = newDraft();
        adminPost("/api/admin/newsletter/" + id + "/send").andExpect(status().isOk());

        adminPost("/api/admin/newsletter/" + id + "/retry-failed")
                .andExpect(status().isConflict());
    }

    /*
     * Checks: A retry skips someone who unsubscribed after the failure.
     * How:    The only email fails, then the person unsubscribes. The retry still shows failedCount 1,
     *   and the sender was called only once in total.
     */
    @Test
    void retry_skipsSomeoneWhoUnsubscribedInTheMeantime() throws Exception {
        addSubscriber("one@mail.com");
        long id = newDraft();
        doThrow(new MailSendException("mail server down"))
                .when(mailSender).send(any(MimeMessage.class));
        adminPost("/api/admin/newsletter/" + id + "/send").andExpect(status().isOk());

        NewsletterSubscriber one = find("one@mail.com");
        one.unsubscribe();
        subscribers.save(one);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        adminPost("/api/admin/newsletter/" + id + "/retry-failed")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedCount").value(1));

        // Only the first (failed) attempt happened. No email after unsubscribing.
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }
}