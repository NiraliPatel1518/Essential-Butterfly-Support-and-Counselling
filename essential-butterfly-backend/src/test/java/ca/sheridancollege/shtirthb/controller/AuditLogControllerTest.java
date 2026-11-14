package ca.sheridancollege.shtirthb.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import ca.sheridancollege.shtirthb.dto.AuditLogResponse;
import ca.sheridancollege.shtirthb.service.AuditTrailService;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock AuditTrailService auditTrail;
    @InjectMocks AuditLogController controller;

    @Test void returnsRecentActivityWithOkStatus() {
        List<AuditLogResponse> entries = List.of(
                new AuditLogResponse(1L, "2026-11-10 14:30:00", "CONTENT_PUBLISHED",
                        "website_content_pages", 5L, "ADMIN:1", "{}"));
        when(auditTrail.recentContentActivity(50)).thenReturn(entries);

        var response = controller.getRecentActivity(50);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(entries, response.getBody());
    }

    @Test void passesTheRequestedLimitToTheService() {
        when(auditTrail.recentContentActivity(10)).thenReturn(List.of());

        controller.getRecentActivity(10);

        verify(auditTrail).recentContentActivity(10);
    }

    @Test void returnsAnEmptyListWhenNothingWasRecorded() {
        when(auditTrail.recentContentActivity(50)).thenReturn(List.of());

        assertTrue(controller.getRecentActivity(50).getBody().isEmpty());
    }
}