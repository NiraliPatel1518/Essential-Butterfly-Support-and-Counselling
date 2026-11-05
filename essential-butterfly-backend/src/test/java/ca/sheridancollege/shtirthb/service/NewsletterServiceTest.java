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
import org.springframework.web.server.ResponseStatusException;
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

    @Test void subscribeNormalizesEmailAndPersistsConsent() {
        when(features.findByFeatureKey("NEWSLETTER")).thenReturn(Optional.of(new FeatureVisibility("NEWSLETTER", "Newsletter Signup", true, 1)));
        when(subscribers.findByEmailIgnoreCase("jane@mail.com")).thenReturn(Optional.empty());
        var result = service.subscribe(" Jane@Mail.com ", true);
        assertEquals(true, result.get("success"));
        verify(subscribers).save(argThat(s -> s.getEmail().equals("jane@mail.com") && s.isActive() && s.isConsentGiven()));
    }

    @Test void subscribeRejectsWhenFeatureDisabled() {
        when(features.findByFeatureKey("NEWSLETTER")).thenReturn(Optional.of(new FeatureVisibility("NEWSLETTER", "Newsletter Signup", false, 1)));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.subscribe("jane@mail.com", true));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(subscribers);
    }

    @Test void subscribeRejectsInvalidEmailAndMissingConsent() {
        when(features.findByFeatureKey("NEWSLETTER")).thenReturn(Optional.of(new FeatureVisibility("NEWSLETTER", "Newsletter Signup", true, 1)));
        assertThrows(ResponseStatusException.class, () -> service.subscribe("bad-address", true));
        assertThrows(ResponseStatusException.class, () -> service.subscribe("jane@mail.com", false));
        verify(subscribers, never()).save(any());
    }

    @Test void duplicateActiveSubscriptionDoesNotCreateSecondSubscriber() {
        when(features.findByFeatureKey("NEWSLETTER")).thenReturn(Optional.of(new FeatureVisibility("NEWSLETTER", "Newsletter Signup", true, 1)));
        when(subscribers.findByEmailIgnoreCase("jane@mail.com")).thenReturn(Optional.of(new NewsletterSubscriber("jane@mail.com")));
        service.subscribe("jane@mail.com", true);
        verify(subscribers, never()).save(any());
    }

    @Test void unsubscribeDeactivatesSubscriber() {
        NewsletterSubscriber subscriber = new NewsletterSubscriber("jane@mail.com");
        when(subscribers.findByUnsubscribeToken("unsubscribe-token")).thenReturn(Optional.of(subscriber));
        service.unsubscribe("unsubscribe-token");
        assertFalse(subscriber.isActive());
    }

    @Test void unsubscribeRejectsUnknownToken() {
        when(subscribers.findByUnsubscribeToken("bad")).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.unsubscribe("bad"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test void createCampaignRejectsBlankSubjectOrContent() {
        assertThrows(ResponseStatusException.class, () -> service.createCampaign(" ", "Body"));
        assertThrows(ResponseStatusException.class, () -> service.createCampaign("Subject", " "));
        verifyNoInteractions(campaigns);
    }
}
