package ca.sheridancollege.shtirthb.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import ca.sheridancollege.shtirthb.dto.ContentPageAdminResponse;
import ca.sheridancollege.shtirthb.dto.ContentPagePublicResponse;
import ca.sheridancollege.shtirthb.dto.ContentPageRequest;
import ca.sheridancollege.shtirthb.service.ContentPageService;

@ExtendWith(MockitoExtension.class)
class ContentPageControllerTest {

    @Mock ContentPageService service;
    @Mock Authentication authentication;
    @InjectMocks ContentPageController controller;

    /*
     * Checks: A published page is returned with status 200.
     * How:    The fake service returns a page for the key "about". The controller returns the same
     *   object.
     */
    @Test void returnsThePublishedPageWithOkStatus() {
        ContentPagePublicResponse expected = mock(ContentPagePublicResponse.class);
        when(service.getPublished("about")).thenReturn(Optional.of(expected));

        var response = controller.getPublished("about");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    /*
     * Checks: A page that was never published gives 204 No Content.
     * How:    The fake service returns Optional.empty for "services". The status is 204 and the body
     *   is null.
     */
    @Test void returnsNoContentWhenThePageWasNeverPublished() {
        when(service.getPublished("services")).thenReturn(Optional.empty());

        var response = controller.getPublished("services");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
    }

    /*
     * Checks: The admin can list every editable page.
     * How:    The fake service returns a list, and the controller answers 200 with it.
     */
    @Test void returnsEveryEditablePageForTheAdministrator() {
        List<ContentPageAdminResponse> pages = List.of(mock(ContentPageAdminResponse.class));
        when(service.getAdminPages()).thenReturn(pages);

        var response = controller.getAdminPages();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(pages, response.getBody());
    }

    /*
     * Checks: The admin can open one editable page.
     * How:    The fake service returns the page "privacy". The controller returns it with 200.
     */
    @Test void returnsOneEditablePage() {
        ContentPageAdminResponse expected = mock(ContentPageAdminResponse.class);
        when(service.getAdminPage("privacy")).thenReturn(expected);

        var response = controller.getAdminPage("privacy");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    /*
     * Checks: Saving a page uses the signed-in admin's email.
     * How:    The service save is verified to be called with the page key, the request and
     *   casey@example.com.
     */
    @Test void savesThePageAsTheSignedInAdministrator() {
        ContentPageRequest request = mock(ContentPageRequest.class);
        ContentPageAdminResponse expected = mock(ContentPageAdminResponse.class);
        when(authentication.getName()).thenReturn("casey@example.com");
        when(service.save("about", request, "casey@example.com")).thenReturn(expected);

        var response = controller.savePage("about", request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(service).save("about", request, "casey@example.com");
    }

    /*
     * Checks: A save error from the service reaches the caller.
     * How:    The service throws IllegalArgumentException with "Title is required.". The controller
     *   throws the same message.
     */
    @Test void saveErrorsFromTheServiceAreNotHidden() {
        ContentPageRequest request = mock(ContentPageRequest.class);
        when(authentication.getName()).thenReturn("casey@example.com");
        when(service.save("about", request, "casey@example.com"))
                .thenThrow(new IllegalArgumentException("Title is required."));

        var thrown = assertThrows(IllegalArgumentException.class,
                () -> controller.savePage("about", request, authentication));

        assertEquals("Title is required.", thrown.getMessage());
    }
}