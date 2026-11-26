package ca.sheridancollege.shtirthb.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import ca.sheridancollege.shtirthb.dto.MemoryWallAdminOverview;
import ca.sheridancollege.shtirthb.dto.MemoryWallDescriptionRequest;
import ca.sheridancollege.shtirthb.dto.MemoryWallImage;
import ca.sheridancollege.shtirthb.dto.MemoryWallMonth;
import ca.sheridancollege.shtirthb.dto.MemoryWallPhotoAdminResponse;
import ca.sheridancollege.shtirthb.dto.MemoryWallSelectionRequest;
import ca.sheridancollege.shtirthb.service.MemoryWallService;

@ExtendWith(MockitoExtension.class)
class MemoryWallControllerTest {

    @Mock MemoryWallService service;
    @Mock Authentication authentication;
    @InjectMocks MemoryWallController controller;

    // ------------------------------------------------------------- public

    /*
     * Checks: The public memory wall is returned grouped by month.
     * How:    The fake service returns one month (November 2026). The controller returns the same
     *   list.
     */
    @Test void returnsThePublicWallGroupedByMonth() {
        List<MemoryWallMonth> months = List.of(new MemoryWallMonth("2026-11", "November 2026", List.of()));
        when(service.getPublicWall()).thenReturn(months);

        var response = controller.getWall();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(months, response.getBody());
    }

    /*
     * Checks: A public photo is sent with its content type and bytes.
     * How:    The fake service returns image/png bytes. The answer has the same type and the same
     *   bytes.
     */
    @Test void publicImageIsSentWithItsTypeAndTheBytes() {
        byte[] bytes = {9, 8, 7};
        when(service.getPublicImage(3L)).thenReturn(new MemoryWallImage("image/png", bytes));

        var response = controller.getPublicImage(3L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("image/png", response.getHeaders().getContentType().toString());
        assertArrayEquals(bytes, response.getBody());
    }

    /*
     * Checks: A public photo may be cached for a short time and is never type-sniffed.
     * How:    The Cache-Control header contains public and max-age=60, and X-Content-Type-Options is
     *   nosniff.
     */
    @Test void publicImageMayBeKeptBrieflyAndIsNeverSniffed() {
        when(service.getPublicImage(3L)).thenReturn(new MemoryWallImage("image/png", new byte[] {1}));

        var headers = controller.getPublicImage(3L).getHeaders();

        assertTrue(headers.getCacheControl().contains("max-age=60"));
        assertTrue(headers.getCacheControl().contains("public"));
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
    }

    /*
     * Checks: An error from the service for a missing photo reaches the caller.
     * How:    The service throws IllegalStateException and the controller throws it as well.
     */
    @Test void publicImageErrorsFromTheServiceAreNotHidden() {
        when(service.getPublicImage(404L)).thenThrow(new IllegalStateException("not found"));

        assertThrows(IllegalStateException.class, () -> controller.getPublicImage(404L));
    }

    // -------------------------------------------------------------- admin

    /*
     * Checks: The admin overview of the memory wall is returned.
     * How:    The fake service returns an overview, and the controller answers 200 with it.
     */
    @Test void returnsTheAdminOverview() {
        MemoryWallAdminOverview overview = mock(MemoryWallAdminOverview.class);
        when(service.getOverview()).thenReturn(overview);

        var response = controller.getOverview();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(overview, response.getBody());
    }

    /*
     * Checks: An admin photo view is never stored by the browser.
     * How:    Cache-Control must be no-store, X-Content-Type-Options nosniff, and the content type and
     *   bytes must match.
     */
    @Test void adminImageIsNeverStoredByTheBrowser() {
        byte[] bytes = {1, 2};
        when(service.getAdminImage(4L)).thenReturn(new MemoryWallImage("image/jpeg", bytes));

        var response = controller.getAdminImage(4L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("image/jpeg", response.getHeaders().getContentType().toString());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertArrayEquals(bytes, response.getBody());
    }

    /*
     * Checks: Uploading a photo passes the file, description and admin email to the service, and
     *   answers 201.
     * How:    We verify service.upload was called with the same file, "A happy day" and
     *   casey@example.com.
     */
    @Test void uploadSendsTheFileAndDescriptionAsTheAdministratorAndReturnsCreated() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1});
        MemoryWallPhotoAdminResponse expected = mock(MemoryWallPhotoAdminResponse.class);
        when(authentication.getName()).thenReturn("casey@example.com");
        when(service.upload(file, "A happy day", "casey@example.com")).thenReturn(expected);

        var response = controller.uploadPhoto(file, "A happy day", authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(service).upload(file, "A happy day", "casey@example.com");
    }

    /*
     * Checks: A missing file is refused by the service, not by the controller.
     * How:    The service throws IllegalArgumentException with "Please choose a photo.". The
     *   controller throws the same message.
     */
    @Test void uploadWithoutAFileIsLeftToTheServiceToRefuse() {
        when(authentication.getName()).thenReturn("casey@example.com");
        when(service.upload(isNull(), any(), eq("casey@example.com")))
                .thenThrow(new IllegalArgumentException("Please choose a photo."));

        var thrown = assertThrows(IllegalArgumentException.class,
                () -> controller.uploadPhoto(null, "x", authentication));

        assertEquals("Please choose a photo.", thrown.getMessage());
    }

    /*
     * Checks: Changing a photo description uses the photo id and answers 200.
     * How:    The service is stubbed for id 6. The controller returns its result.
     */
    @Test void changeDescriptionUsesTheIdAndReturnsOk() {
        MemoryWallDescriptionRequest request = new MemoryWallDescriptionRequest("New words");
        MemoryWallPhotoAdminResponse expected = mock(MemoryWallPhotoAdminResponse.class);
        when(authentication.getName()).thenReturn("casey@example.com");
        when(service.changeDescription(6L, request, "casey@example.com")).thenReturn(expected);

        var response = controller.changeDescription(6L, request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    /*
     * Checks: Saving which photos are shown returns the new overview.
     * How:    We send a list of two ids. The service saveSelection is verified and its result
     *   returned.
     */
    @Test void saveSelectionReturnsTheNewOverview() {
        MemoryWallSelectionRequest request = new MemoryWallSelectionRequest(List.of(1L, 2L));
        MemoryWallAdminOverview expected = mock(MemoryWallAdminOverview.class);
        when(authentication.getName()).thenReturn("casey@example.com");
        when(service.saveSelection(request, "casey@example.com")).thenReturn(expected);

        var response = controller.saveSelection(request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(service).saveSelection(request, "casey@example.com");
    }

    /*
     * Checks: Deleting a photo answers 204 No Content.
     * How:    The body is null, and service.delete is called with id 6 and the admin's email.
     */
    @Test void deleteReturnsNoContent() {
        when(authentication.getName()).thenReturn("casey@example.com");

        var response = controller.deletePhoto(6L, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
        verify(service).delete(6L, "casey@example.com");
    }
}