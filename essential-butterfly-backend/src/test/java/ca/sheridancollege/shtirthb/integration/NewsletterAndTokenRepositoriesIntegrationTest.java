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

    // ---------- subscribers ----------

    @Test
    void findsASubscriberByEmailIgnoringCase() {
        subscribers.save(new NewsletterSubscriber("Foo@Mail.com"));

        assertTrue(subscribers.findByEmailIgnoreCase("foo@MAIL.COM").isPresent());
        assertTrue(subscribers.findByEmailIgnoreCase("other@mail.com").isEmpty());
    }

    @Test
    void findsASubscriberByUnsubscribeToken() {
        NewsletterSubscriber saved = subscribers.save(new NewsletterSubscriber("token@mail.com"));

        NewsletterSubscriber found =
                subscribers.findByUnsubscribeToken(saved.getUnsubscribeToken()).orElseThrow();

        assertEquals("token@mail.com", found.getEmail());
        assertTrue(subscribers.findByUnsubscribeToken("not-a-real-token").isEmpty());
    }

    @Test
    void databaseRejectsTwoSubscribersWithTheSameEmail() {
        subscribers.save(new NewsletterSubscriber("same@mail.com"));

        assertThrows(DataIntegrityViolationException.class, () ->
                subscribers.saveAndFlush(new NewsletterSubscriber("same@mail.com")));
    }

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

    @Test
    void savesSendResultsAndRetryResults() {
        NewsletterCampaign campaign = campaigns.save(new NewsletterCampaign("Results", "Body"));
        campaign.recordResults(3, 2, 1);
        campaigns.saveAndFlush(campaign);
        entityManager.clear();

        NewsletterCampaign afterSend = campaigns.findById(campaign.getId()).orElseThrow();
        assertEquals(3, afterSend.getRecipientCount());
        assertEquals(2, afterSend.getSuccessCount());
        assertEquals(1, afterSend.getFailedCount());
        assertEquals("PARTIAL", afterSend.getStatus());

        afterSend.recordRetryResults(3, 0);
        campaigns.saveAndFlush(afterSend);
        entityManager.clear();

        NewsletterCampaign afterRetry = campaigns.findById(campaign.getId()).orElseThrow();
        assertEquals(0, afterRetry.getFailedCount());
        assertEquals("SENT", afterRetry.getStatus());
    }

    // ---------- deliveries ----------

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

    @Test
    void findsAResetTokenAndRemembersWhenItWasUsed() {
        PasswordResetToken saved = resetTokens.save(new PasswordResetToken(
                "token-abc", "CLIENT", 42L, LocalDateTime.now().plusMinutes(30)));
        saved.setUsed(true);
        resetTokens.saveAndFlush(saved);
        entityManager.clear();

        PasswordResetToken found = resetTokens.findByToken("token-abc").orElseThrow();

        assertEquals("CLIENT", found.getUserType());
        assertEquals(42L, found.getUserId());
        assertTrue(found.isUsed());
        assertTrue(resetTokens.findByToken("unknown-token").isEmpty());
    }

    @Test
    void databaseRejectsTwoResetTokensWithTheSameValue() {
        resetTokens.save(new PasswordResetToken(
                "dup-token", "ADMIN", 1L, LocalDateTime.now().plusMinutes(30)));

        assertThrows(DataIntegrityViolationException.class, () ->
                resetTokens.saveAndFlush(new PasswordResetToken(
                        "dup-token", "CLIENT", 2L, LocalDateTime.now().plusMinutes(30))));
    }
}