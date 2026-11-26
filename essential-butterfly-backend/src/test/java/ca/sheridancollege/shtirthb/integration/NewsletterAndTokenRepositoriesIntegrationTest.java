package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.NewsletterCampaign;
import ca.sheridancollege.shtirthb.model.NewsletterDelivery;
import ca.sheridancollege.shtirthb.model.NewsletterSubscriber;
import ca.sheridancollege.shtirthb.model.PasswordResetToken;
import ca.sheridancollege.shtirthb.repository.NewsletterCampaignRepository;
import ca.sheridancollege.shtirthb.repository.NewsletterDeliveryRepository;
import ca.sheridancollege.shtirthb.repository.NewsletterSubscriberRepository;
import ca.sheridancollege.shtirthb.repository.PasswordResetTokenRepository;

class NewsletterAndTokenRepositoriesIntegrationTest extends RepositoryIntegrationTestBase {

    @Autowired
    private NewsletterSubscriberRepository subscribers;

    @Autowired
    private NewsletterCampaignRepository campaigns;

    @Autowired
    private NewsletterDeliveryRepository deliveries;

    @Autowired
    private PasswordResetTokenRepository resetTokens;

    @PersistenceContext
    private EntityManager entityManager;

    // Timestamps decide the order, so a short pause keeps them different.
    private void pause() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ClientUser subscribersOwner(String email) {
        // Persist an account owner for the password-reset token foreign key.
        ClientUser owner = new ClientUser("Token Owner", email, "encoded-password");
        return entityManager.merge(owner);
    }

    // ---------- subscribers ----------

    /*
     * Checks: A subscriber is found by email, ignoring capital letters.
     * How:    We save foo@mail.com and search FOO@MAIL.COM (found) and another email (not found).
     */
    @Test
    void findsASubscriberByEmailIgnoringCase() {
        // Store the email in lowercase to satisfy the database constraint.
        subscribers.save(new NewsletterSubscriber("foo@mail.com"));

        // Search using uppercase to verify case-insensitive lookup.
        assertTrue(subscribers.findByEmailIgnoreCase("FOO@MAIL.COM").isPresent());

        assertTrue(subscribers.findByEmailIgnoreCase("other@mail.com").isEmpty());
}

    /*
     * Checks: A subscriber is found by the unsubscribe token.
     * How:    We use the saved token to find the same email. A fake token finds nothing.
     */
    @Test
    void findsASubscriberByUnsubscribeToken() {
        NewsletterSubscriber saved = subscribers.save(new NewsletterSubscriber("token@mail.com"));

        NewsletterSubscriber found =
                subscribers.findByUnsubscribeToken(saved.getUnsubscribeToken()).orElseThrow();

        assertEquals("token@mail.com", found.getEmail());
        assertTrue(subscribers.findByUnsubscribeToken("not-a-real-token").isEmpty());
    }

    /*
     * Checks: The database refuses two subscribers with the same email.
     * How:    The second save throws DataIntegrityViolationException.
     */
    @Test
    void databaseRejectsTwoSubscribersWithTheSameEmail() {
        subscribers.save(new NewsletterSubscriber("same@mail.com"));

        assertThrows(DataIntegrityViolationException.class, () ->
                subscribers.saveAndFlush(new NewsletterSubscriber("same@mail.com")));
    }

    /*
     * Checks: The active list and count do not include people who unsubscribed.
     * How:    We save three subscribers and unsubscribe one. The active count rises by 2, and the
     *   active list shows last and first, newest first.
     */
    @Test
    void activeListAndCountIgnoreUnsubscribedPeople() {
        long before = subscribers.countByActiveTrue();
        subscribers.save(new NewsletterSubscriber("first@mail.com"));
        pause();
        NewsletterSubscriber leaving = subscribers.save(new NewsletterSubscriber("leaving@mail.com"));
        pause();
        subscribers.save(new NewsletterSubscriber("last@mail.com"));
        leaving.unsubscribe();
        subscribers.saveAndFlush(leaving);

        assertEquals(before + 2, subscribers.countByActiveTrue());

        List<String> emails = subscribers.findAllByActiveTrueOrderByCreatedAtDesc().stream()
                .map(NewsletterSubscriber::getEmail)
                .toList();
        assertEquals(List.of("last@mail.com", "first@mail.com"), emails);
    }

    /*
     * Checks: A subscriber can subscribe again and gets a new token.
     * How:    We unsubscribe (active false), then resubscribe. The subscriber is active again and the
     *   token is different from the old one.
     */
    @Test
    void aSubscriberCanResubscribeWithANewToken() {
        NewsletterSubscriber subscriber = subscribers.save(new NewsletterSubscriber("back@mail.com"));
        String oldToken = subscriber.getUnsubscribeToken();
        subscriber.unsubscribe();
        subscribers.saveAndFlush(subscriber);
        assertFalse(subscribers.findByEmailIgnoreCase("back@mail.com").orElseThrow().isActive());

        subscriber.resubscribe();
        subscribers.saveAndFlush(subscriber);

        NewsletterSubscriber loaded = subscribers.findByEmailIgnoreCase("back@mail.com").orElseThrow();
        assertTrue(loaded.isActive());
        assertNotEquals(oldToken, loaded.getUnsubscribeToken());
    }

    // ---------- campaigns ----------

    /*
     * Checks: A campaign is saved as a DRAFT and edits are kept.
     * How:    We save, update the subject and content, clear the entity manager, and load it again.
     *   The status is DRAFT and the new text is there.
     */
    @Test
    void savesACampaignAsADraftAndKeepsEdits() {
        NewsletterCampaign saved = campaigns.save(new NewsletterCampaign("Subject", "Body"));
        saved.update("New subject", "New body");
        campaigns.saveAndFlush(saved);
        entityManager.clear();

        NewsletterCampaign loaded = campaigns.findById(saved.getId()).orElseThrow();

        assertEquals("DRAFT", loaded.getStatus());
        assertEquals("New subject", loaded.getSubject());
        assertEquals("New body", loaded.getContent());
    }

    /*
     * Checks: Send results and retry results are saved.
     * How:    recordOutcome(2 sent, 1 failed) gives PARTIAL with a sent time. Delivery rows count 2
     *   SENT and 1 FAILED. After a retry outcome (3 sent, 0 failed) the status is SENT.
     */
    @Test
    void savesSendResultsAndRetryResults() {
        NewsletterCampaign campaign = campaigns.save(new NewsletterCampaign("Results", "Body"));
        campaign.recordOutcome(2, 1);
        campaigns.saveAndFlush(campaign);
        entityManager.clear();

        NewsletterCampaign afterSend = campaigns.findById(campaign.getId()).orElseThrow();
        assertEquals("PARTIAL", afterSend.getStatus());
        assertTrue(afterSend.getSentAt() != null);

        // Delivery totals are stored in NewsletterDelivery rows, not on the campaign.
        NewsletterSubscriber one = subscribers.save(new NewsletterSubscriber("result1@mail.com"));
        NewsletterSubscriber two = subscribers.save(new NewsletterSubscriber("result2@mail.com"));
        NewsletterSubscriber three = subscribers.save(new NewsletterSubscriber("result3@mail.com"));
        NewsletterDelivery sentOne = new NewsletterDelivery(afterSend, one);
        sentOne.markSent();
        NewsletterDelivery sentTwo = new NewsletterDelivery(afterSend, two);
        sentTwo.markSent();
        NewsletterDelivery failed = new NewsletterDelivery(afterSend, three);
        failed.markFailed("Temporary failure");
        deliveries.saveAllAndFlush(List.of(sentOne, sentTwo, failed));

        assertEquals(2, deliveries.findAllByCampaignIdAndStatus(campaign.getId(), "SENT").size());
        assertEquals(1, deliveries.findAllByCampaignIdAndStatus(campaign.getId(), "FAILED").size());

        afterSend.recordOutcome(3, 0);
        campaigns.saveAndFlush(afterSend);
        entityManager.clear();
        assertEquals("SENT", campaigns.findById(campaign.getId()).orElseThrow().getStatus());
    }

    // ---------- deliveries ----------

    /*
     * Checks: Deliveries can be found by campaign and by status.
     * How:    We save SENT, FAILED, PENDING deliveries in one campaign and one in another. The queries
     *   return the right counts for each campaign and status.
     */
    @Test
    void findsDeliveriesByCampaignAndStatus() {
        NewsletterCampaign campaign = campaigns.save(new NewsletterCampaign("Delivery", "Body"));
        NewsletterCampaign otherCampaign = campaigns.save(new NewsletterCampaign("Other", "Body"));
        NewsletterSubscriber one = subscribers.save(new NewsletterSubscriber("d1@mail.com"));
        NewsletterSubscriber two = subscribers.save(new NewsletterSubscriber("d2@mail.com"));
        NewsletterSubscriber three = subscribers.save(new NewsletterSubscriber("d3@mail.com"));

        NewsletterDelivery sent = new NewsletterDelivery(campaign, one);
        sent.markSent();
        NewsletterDelivery failed = new NewsletterDelivery(campaign, two);
        failed.markFailed("Mailbox full");
        NewsletterDelivery pending = new NewsletterDelivery(campaign, three);
        NewsletterDelivery inOtherCampaign = new NewsletterDelivery(otherCampaign, one);
        deliveries.saveAll(List.of(sent, failed, pending, inOtherCampaign));
        entityManager.flush();
        entityManager.clear();

        assertEquals(3, deliveries.findAllByCampaignId(campaign.getId()).size());
        assertEquals(1, deliveries.findAllByCampaignId(otherCampaign.getId()).size());

        List<NewsletterDelivery> failedOnes =
                deliveries.findAllByCampaignIdAndStatus(campaign.getId(), "FAILED");
        assertEquals(1, failedOnes.size());
        assertEquals("FAILED", failedOnes.get(0).getStatus());
        assertEquals(1, deliveries.findAllByCampaignIdAndStatus(campaign.getId(), "SENT").size());
        assertEquals(1, deliveries.findAllByCampaignIdAndStatus(campaign.getId(), "PENDING").size());
    }

    // ---------- password reset tokens ----------

    /*
     * Checks: A password reset token is found by its hash and keeps its used flag.
     * How:    We save a token for a client, mark it used, clear the entity manager and load it. The
     *   type is CLIENT, the owner is right, and used is true. A different hash finds nothing.
     */
    @Test
    void findsAResetTokenAndRemembersWhenItWasUsed() {
        ClientUser owner = subscribersOwner("token-owner@mail.com");
        String tokenHash = "a".repeat(64);
        PasswordResetToken saved = resetTokens.save(PasswordResetToken.forClient(
                tokenHash, owner, LocalDateTime.now().plusMinutes(30)));
        saved.setUsed(true);
        resetTokens.saveAndFlush(saved);
        entityManager.clear();

        PasswordResetToken found = resetTokens.findByTokenHash(tokenHash).orElseThrow();

        assertEquals("CLIENT", found.getUserType());
        assertEquals(owner.getId(), found.getClientUser().getId());
        assertTrue(found.isUsed());
        assertTrue(resetTokens.findByTokenHash("b".repeat(64)).isEmpty());
    }

    /*
     * Checks: The database refuses two reset tokens with the same hash.
     * How:    The second save of the same 64-character hash throws DataIntegrityViolationException.
     */
    @Test
    void databaseRejectsTwoResetTokensWithTheSameValue() {
        String duplicateHash = "c".repeat(64);
        ClientUser firstOwner = subscribersOwner("first-token-owner@mail.com");
        ClientUser secondOwner = subscribersOwner("second-token-owner@mail.com");
        resetTokens.saveAndFlush(PasswordResetToken.forClient(
                duplicateHash, firstOwner, LocalDateTime.now().plusMinutes(30)));

        assertThrows(DataIntegrityViolationException.class, () ->
                resetTokens.saveAndFlush(PasswordResetToken.forClient(
                        duplicateHash, secondOwner, LocalDateTime.now().plusMinutes(30))));
    }
}