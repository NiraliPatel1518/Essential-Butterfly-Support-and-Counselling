package ca.sheridancollege.shtirthb.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.util.Map;
import ca.sheridancollege.shtirthb.service.NewsletterService;

@ExtendWith(MockitoExtension.class)
class NewsletterControllerTest {
    @Mock NewsletterService service;
    @InjectMocks NewsletterController controller;

    /*
     * Checks: Subscribing with an invalid email is refused.
     * How:    We send "not-an-email" with consent. The answer is 400, and the service is never used.
     */
    @Test void rejectsInvalidEmail() {
        var response = controller.subscribe(Map.of("email", "not-an-email", "consent", true));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(service);
    }
    /*
     * Checks: Subscribing without consent is refused.
     * How:    We send a good email with consent false. The answer is 400, and the service is never
     *   used.
     */
    @Test void requiresConsent() {
        var response = controller.subscribe(Map.of("email", "a@example.com", "consent", false));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(service);
    }
    /*
     * Checks: A valid email with consent is subscribed.
     * How:    We send the email with spaces around it. The service is called with the trimmed email,
     *   and the controller returns 200 with its answer.
     */
    @Test void subscribesWithValidEmailAndConsent() {
        Map<String,Object> expected = Map.of("success", true);
        when(service.subscribe("a@example.com", true)).thenReturn(expected);
        var response = controller.subscribe(Map.of("email", " a@example.com ", "consent", true));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }
    /*
     * Checks: An unsubscribe with a blank token is refused.
     * How:    A token made of a space gives 400, and the service is never used.
     */
    @Test void rejectsBlankUnsubscribeToken() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.unsubscribe(" ").getStatusCode());
        verifyNoInteractions(service);
    }
    /*
     * Checks: A valid token unsubscribes the person.
     * How:    The service is stubbed for token-123, and the controller returns its answer.
     */
    @Test void unsubscribesWithToken() {
        Map<String,Object> expected = Map.of("success", true);
        when(service.unsubscribe("token-123")).thenReturn(expected);
        assertSame(expected, controller.unsubscribe("token-123").getBody());
    }
    /*
     * Checks: The admin newsletter overview is returned.
     * How:    The fake service returns a map with activeSubscribers, and the controller returns it.
     */
    @Test void returnsAdminOverview() {
        Map<String,Object> expected = Map.of("activeSubscribers", 0);
        when(service.getAdminOverview()).thenReturn(expected);
        assertSame(expected, controller.overview().getBody());
    }
    /*
     * Checks: A draft with an empty subject is refused.
     * How:    The subject is empty. The answer is 400 and the service is never used.
     */
    @Test void rejectsBlankDraft() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.createDraft(Map.of("subject","", "content","x")).getStatusCode());
        verifyNoInteractions(service);
    }
    /*
     * Checks: A draft campaign is created and answered with 201.
     * How:    Subject and content with extra spaces are trimmed before they go to the service.
     */
    @Test void createsDraft() {
        Map<String,Object> expected = Map.of("status","DRAFT");
        when(service.createCampaign("Hello","Body")).thenReturn(expected);
        var response = controller.createDraft(Map.of("subject"," Hello ", "content"," Body "));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(expected, response.getBody());
    }
    /*
     * Checks: An existing draft can be updated.
     * How:    The service updateCampaign is stubbed for id 5, and the controller returns its answer.
     */
    @Test void updatesDraft() {
        Map<String,Object> expected = Map.of("status","DRAFT");
        when(service.updateCampaign(5L,"Hello","Body")).thenReturn(expected);
        assertSame(expected, controller.updateDraft(5L, Map.of("subject","Hello","content","Body")).getBody());
    }
    /*
     * Checks: The admin can preview a campaign.
     * How:    The service preview is called for id 5, and the status is 200.
     */
    @Test void previewsCampaign() {
        when(service.preview(5L)).thenReturn(Map.of("activeRecipientCount", 2));
        assertEquals(HttpStatus.OK, controller.preview(5L).getStatusCode());
    }
    /*
     * Checks: The admin can send a campaign.
     * How:    The service send is called for id 5, and the status is 200.
     */
    @Test void sendsCampaign() {
        when(service.send(5L)).thenReturn(Map.of("status","SENT"));
        assertEquals(HttpStatus.OK, controller.send(5L).getStatusCode());
    }
    /*
     * Checks: The admin can retry failed deliveries.
     * How:    The service retryFailed is called for id 5, and the status is 200.
     */
    @Test void retriesFailedDeliveries() {
        when(service.retryFailed(5L)).thenReturn(Map.of("failedCount",0));
        assertEquals(HttpStatus.OK, controller.retryFailed(5L).getStatusCode());
    }
    /*
     * Checks: The admin can see the delivery details of a campaign.
     * How:    The service getCampaignDetails is called for id 5, and the status is 200.
     */
    @Test void returnsDeliveryDetails() {
        when(service.getCampaignDetails(5L)).thenReturn(Map.of("deliveries", java.util.List.of()));
        assertEquals(HttpStatus.OK, controller.deliveries(5L).getStatusCode());
    }
}
