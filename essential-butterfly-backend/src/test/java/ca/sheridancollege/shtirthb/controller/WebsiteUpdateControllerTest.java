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

import java.util.List;
import ca.sheridancollege.shtirthb.dto.PublicUpdateSummaryResponse;
import ca.sheridancollege.shtirthb.dto.WebsiteUpdateRequest;
import ca.sheridancollege.shtirthb.dto.WebsiteUpdateResponse;
import ca.sheridancollege.shtirthb.service.WebsiteUpdateService;

@ExtendWith(MockitoExtension.class)
class WebsiteUpdateControllerTest {
    @Mock WebsiteUpdateService service;
    @InjectMocks WebsiteUpdateController controller;

    /*
     * Checks: The public list of website updates is returned.
     * How:    The fake service returns an empty list, and the status is 200.
     */
    @Test void returnsPublicUpdateSummaries() {
        when(service.getPublicUpdates()).thenReturn(List.of());
        assertEquals(HttpStatus.OK, controller.getPublicUpdates().getStatusCode());
    }
    /*
     * Checks: One public update can be opened by id.
     * How:    The fake service returns an update for id 8, and the controller returns it.
     */
    @Test void returnsPublicUpdateById() {
        WebsiteUpdateResponse expected = mock(WebsiteUpdateResponse.class);
        when(service.getPublicUpdate(8L)).thenReturn(expected);
        assertSame(expected, controller.getPublicUpdate(8L).getBody());
    }
    /*
     * Checks: The admin can list all updates.
     * How:    The fake service returns an empty list, and the status is 200.
     */
    @Test void returnsAdminUpdates() {
        when(service.getAllAdminUpdates()).thenReturn(List.of());
        assertEquals(HttpStatus.OK, controller.getAdminUpdates().getStatusCode());
    }
    /*
     * Checks: Creating an update answers 201 Created.
     * How:    The service createUpdate result is returned in the body.
     */
    @Test void createsUpdateWithCreatedStatus() {
        WebsiteUpdateRequest request = mock(WebsiteUpdateRequest.class);
        WebsiteUpdateResponse expected = mock(WebsiteUpdateResponse.class);
        when(service.createUpdate(request)).thenReturn(expected);
        var response = controller.createUpdate(request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(expected, response.getBody());
    }
    /*
     * Checks: An update can be edited by id.
     * How:    The service editUpdate is stubbed for id 8, and the controller returns its answer.
     */
    @Test void editsUpdate() {
        WebsiteUpdateRequest request = mock(WebsiteUpdateRequest.class);
        WebsiteUpdateResponse expected = mock(WebsiteUpdateResponse.class);
        when(service.editUpdate(8L, request)).thenReturn(expected);
        assertSame(expected, controller.editUpdate(8L, request).getBody());
    }
    /*
     * Checks: Deleting an update answers 204 No Content.
     * How:    service.deleteUpdate is verified to be called with id 8.
     */
    @Test void deletesUpdateWithNoContent() {
        var response = controller.deleteUpdate(8L);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(service).deleteUpdate(8L);
    }
}
