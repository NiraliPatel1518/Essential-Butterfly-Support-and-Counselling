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

    @Test void rejectsInvalidEmail() {
        var response = controller.subscribe(Map.of("email", "not-an-email", "consent", true));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(service);
    }
    @Test void requiresConsent() {
        var response = controller.subscribe(Map.of("email", "a@example.com", "consent", false));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(service);
    }
    @Test void subscribesWithValidEmailAndConsent() {
        Map<String,Object> expected = Map.of("success", true);
        when(service.subscribe("a@example.com", true)).thenReturn(expected);
        var response = controller.subscribe(Map.of("email", " a@example.com ", "consent", true));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }
    @Test void rejectsBlankUnsubscribeToken() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.unsubscribe(" ").getStatusCode());
        verifyNoInteractions(service);
    }
    @Test void unsubscribesWithToken() {
        Map<String,Object> expected = Map.of("success", true);
        when(service.unsubscribe("token-123")).thenReturn(expected);
        assertSame(expected, controller.unsubscribe("token-123").getBody());
    }
    @Test void returnsAdminOverview() {
        Map<String,Object> expected = Map.of("activeSubscribers", 0);
        when(service.getAdminOverview()).thenReturn(expected);
        assertSame(expected, controller.overview().getBody());
    }
    @Test void rejectsBlankDraft() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.createDraft(Map.of("subject","", "content","x")).getStatusCode());
        verifyNoInteractions(service);
    }
    @Test void createsDraft() {
        Map<String,Object> expected = Map.of("status","DRAFT");
        when(service.createCampaign("Hello","Body")).thenReturn(expected);
        var response = controller.createDraft(Map.of("subject"," Hello ", "content"," Body "));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(expected, response.getBody());
    }
    @Test void updatesDraft() {
        Map<String,Object> expected = Map.of("status","DRAFT");
        when(service.updateCampaign(5L,"Hello","Body")).thenReturn(expected);
        assertSame(expected, controller.updateDraft(5L, Map.of("subject","Hello","content","Body")).getBody());
    }
    @Test void previewsCampaign() {
        when(service.preview(5L)).thenReturn(Map.of("activeRecipientCount", 2));
        assertEquals(HttpStatus.OK, controller.preview(5L).getStatusCode());
    }
    @Test void sendsCampaign() {
        when(service.send(5L)).thenReturn(Map.of("status","SENT"));
        assertEquals(HttpStatus.OK, controller.send(5L).getStatusCode());
    }
    @Test void retriesFailedDeliveries() {
        when(service.retryFailed(5L)).thenReturn(Map.of("failedCount",0));
        assertEquals(HttpStatus.OK, controller.retryFailed(5L).getStatusCode());
    }
    @Test void returnsDeliveryDetails() {
        when(service.getCampaignDetails(5L)).thenReturn(Map.of("deliveries", java.util.List.of()));
        assertEquals(HttpStatus.OK, controller.deliveries(5L).getStatusCode());
    }
}
