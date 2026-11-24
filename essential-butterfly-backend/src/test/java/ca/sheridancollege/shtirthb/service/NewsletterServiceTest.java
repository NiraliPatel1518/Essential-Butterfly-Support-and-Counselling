package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.exception.ExceptionMapping;
import ca.sheridancollege.shtirthb.model.FeatureVisibility;
import ca.sheridancollege.shtirthb.model.NewsletterSubscriber;
import ca.sheridancollege.shtirthb.repository.*;

@ExtendWith(MockitoExtension.class)
class NewsletterServiceTest {
    @Mock NewsletterSubscriberRepository subscribers;
    @Mock NewsletterCampaignRepository campaigns;
    @Mock NewsletterDeliveryRepository deliveries;
    @Mock FeatureVisibilityRepository features;
    @Mock NewsletterMailService mail;
    NewsletterService service;
    @BeforeEach void setUp() { service = new NewsletterService(subscribers, campaigns, deliveries, features, mail); }

    /*
     * Checks: Subscribing cleans the email and saves the consent.
     * How:    We pass " Jane@Mail.com ". The service saves a subscriber with jane@mail.com, active
     *   true and consent true.
     */
    @Test void subscribeNormalizesEmailAndPersistsConsent() {
        when(features.findByFeatureKey("NEWSLETTER")).thenReturn(Optional.of(new FeatureVisibility("NEWSLETTER", "Newsletter Signup", true, 1)));
        when(subscribers.findByEmailIgnoreCase("jane@mail.com")).thenReturn(Optional.empty());
        var result = service.subscribe(" Jane@Mail.com ", true);
        assertEquals(true, result.get("success"));
        verify(subscribers).save(argThat(s -> s.getEmail().equals("jane@mail.com") && s.isActive() && s.isConsentGiven()));
    }

    /*
     * Checks: Subscribing is refused when the newsletter feature is off.
     * How:    The feature is disabled. The service throws an exception and nothing is saved.
     */
    @Test void subscribeRejectsWhenFeatureDisabled() {
        when(features.findByFeatureKey("NEWSLETTER")).thenReturn(Optional.of(new FeatureVisibility("NEWSLETTER", "Newsletter Signup", false, 1)));
        AppExceptions.ResourceNotFoundException ex = assertThrows(AppExceptions.ResourceNotFoundException.class, () -> service.subscribe("jane@mail.com", true));
        assertEquals(HttpStatus.NOT_FOUND, ExceptionMapping.getStatus(ex));
        verifyNoInteractions(subscribers);
    }

    /*
     * Checks: An invalid email or missing consent is refused.
     * How:    "bad-address" and consent false both throw ValidationException. save() is never called.
     */
    @Test void subscribeRejectsInvalidEmailAndMissingConsent() {
        when(features.findByFeatureKey("NEWSLETTER")).thenReturn(Optional.of(new FeatureVisibility("NEWSLETTER", "Newsletter Signup", true, 1)));
        assertThrows(AppExceptions.ValidationException.class, () -> service.subscribe("bad-address", true));
        assertThrows(AppExceptions.ValidationException.class, () -> service.subscribe("jane@mail.com", false));
        verify(subscribers, never()).save(any());
    }

    /*
     * Checks: Subscribing twice does not create a second subscriber.
     * How:    The repository already has an active subscriber for the email. The service does not save
     *   a new one.
     */
    @Test void duplicateActiveSubscriptionDoesNotCreateSecondSubscriber() {
        when(features.findByFeatureKey("NEWSLETTER")).thenReturn(Optional.of(new FeatureVisibility("NEWSLETTER", "Newsletter Signup", true, 1)));
        when(subscribers.findByEmailIgnoreCase("jane@mail.com")).thenReturn(Optional.of(new NewsletterSubscriber("jane@mail.com")));
        service.subscribe("jane@mail.com", true);
        verify(subscribers, never()).save(any());
    }

    /*
     * Checks: Unsubscribing turns the subscriber off.
     * How:    We find the subscriber by token and call unsubscribe(). isActive() must be false.
     */
    @Test void unsubscribeDeactivatesSubscriber() {
        NewsletterSubscriber subscriber = new NewsletterSubscriber("jane@mail.com");
        when(subscribers.findByUnsubscribeToken("unsubscribe-token")).thenReturn(Optional.of(subscriber));
        service.unsubscribe("unsubscribe-token");
        assertFalse(subscriber.isActive());
    }

    /*
     * Checks: An unknown unsubscribe token gives not found.
     * How:    The repository returns empty. The service throws ResourceNotFoundException.
     */
    @Test void unsubscribeRejectsUnknownToken() {
        when(subscribers.findByUnsubscribeToken("bad")).thenReturn(Optional.empty());
        AppExceptions.ResourceNotFoundException ex = assertThrows(AppExceptions.ResourceNotFoundException.class, () -> service.unsubscribe("bad"));
        assertEquals(HttpStatus.NOT_FOUND, ExceptionMapping.getStatus(ex));
    }

    /*
     * Checks: A campaign with a blank subject or content is refused.
     * How:    Each case throws ValidationException and the campaign repository is never used.
     */
    @Test void createCampaignRejectsBlankSubjectOrContent() {
        assertThrows(AppExceptions.ValidationException.class, () -> service.createCampaign(" ", "Body"));
        assertThrows(AppExceptions.ValidationException.class, () -> service.createCampaign("Subject", " "));
        verifyNoInteractions(campaigns);
    }
}