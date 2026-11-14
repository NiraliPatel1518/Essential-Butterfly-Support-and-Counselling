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

    @Test void returnsThePublishedPageWithOkStatus() {
        ContentPagePublicResponse expected = mock(ContentPagePublicResponse.class);
        when(service.getPublished("about")).thenReturn(Optional.of(expected));

        var response = controller.getPublished("about");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test void returnsNoContentWhenThePageWasNeverPublished() {
        when(service.getPublished("services")).thenReturn(Optional.empty());

        var response = controller.getPublished("services");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test void returnsEveryEditablePageForTheAdministrator() {
        List<ContentPageAdminResponse> pages = List.of(mock(ContentPageAdminResponse.class));
        when(service.getAdminPages()).thenReturn(pages);

        var response = controller.getAdminPages();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(pages, response.getBody());
    }

    @Test void returnsOneEditablePage() {
        ContentPageAdminResponse expected = mock(ContentPageAdminResponse.class);
        when(service.getAdminPage("privacy")).thenReturn(expected);

        var response = controller.getAdminPage("privacy");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

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