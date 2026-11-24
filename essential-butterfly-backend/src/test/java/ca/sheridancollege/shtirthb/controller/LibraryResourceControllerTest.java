package ca.sheridancollege.shtirthb.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.mock.web.MockMultipartFile;

import ca.sheridancollege.shtirthb.dto.LibraryResourceAdminResponse;
import ca.sheridancollege.shtirthb.dto.LibraryResourcePublicResponse;
import ca.sheridancollege.shtirthb.dto.LibraryResourceRequest;
import ca.sheridancollege.shtirthb.dto.ResourceDownload;
import ca.sheridancollege.shtirthb.dto.UploadRulesResponse;
import ca.sheridancollege.shtirthb.service.LibraryResourceService;

@ExtendWith(MockitoExtension.class)
class LibraryResourceControllerTest {

    @Mock LibraryResourceService service;
    @Mock Authentication authentication;
    @InjectMocks LibraryResourceController controller;

    // ------------------------------------------------------------- public

    /*
     * Checks: The public list of resources is returned with 200.
     * How:    The fake service returns a list, and the controller returns the same list.
     */
    @Test void returnsThePublishedResources() {
        List<LibraryResourcePublicResponse> resources = List.of(mock(LibraryResourcePublicResponse.class));
        when(service.getPublicResources()).thenReturn(resources);

        var response = controller.getPublicResources();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(resources, response.getBody());
    }

    /*
     * Checks: A download is sent with the right content type, file bytes and file name.
     * How:    The fake service returns guide.pdf. The answer has content type application/pdf, the
     *   same bytes, and a Content-Disposition header that contains guide.pdf.
     */
    @Test void downloadSendsTheFileWithItsTypeAndName() {
        byte[] bytes = {1, 2, 3};
        when(service.getPublicDownload(7L))
                .thenReturn(new ResourceDownload("guide.pdf", "application/pdf", bytes, false));

        var response = controller.download(7L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertArrayEquals(bytes, response.getBody());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("guide.pdf"));
    }

    /*
     * Checks: A document such as a PDF is offered as a download (attachment).
     * How:    The Content-Disposition header must start with the word attachment.
     */
    @Test void aDocumentIsOfferedAsAnAttachment() {
        when(service.getPublicDownload(7L))
                .thenReturn(new ResourceDownload("guide.pdf", "application/pdf", new byte[] {1}, false));

        var response = controller.download(7L);

        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment"));
    }

    /*
     * Checks: An image is shown inside the page (inline).
     * How:    For an image resource, the Content-Disposition header must start with inline.
     */
    @Test void anImageIsShownInline() {
        when(service.getPublicDownload(8L))
                .thenReturn(new ResourceDownload("photo.png", "image/png", new byte[] {1}, true));

        var response = controller.download(8L);

        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("inline"));
    }

    /*
     * Checks: Downloads tell the browser not to guess the file type.
     * How:    The header X-Content-Type-Options must be nosniff. This is a security rule.
     */
    @Test void downloadsTellTheBrowserNotToGuessTheFileType() {
        when(service.getPublicDownload(8L))
                .thenReturn(new ResourceDownload("photo.png", "image/png", new byte[] {1}, true));

        assertEquals("nosniff", controller.download(8L).getHeaders().getFirst("X-Content-Type-Options"));
    }

    /*
     * Checks: A download error from the service reaches the caller.
     * How:    The service throws IllegalStateException and the controller throws it too.
     */
    @Test void downloadErrorsFromTheServiceAreNotHidden() {
        when(service.getPublicDownload(99L)).thenThrow(new IllegalStateException("not found"));

        assertThrows(IllegalStateException.class, () -> controller.download(99L));
    }

    // -------------------------------------------------------------- admin

    /*
     * Checks: The admin can list every resource, including unpublished ones.
     * How:    The fake service returns a list and the controller answers 200 with it.
     */
    @Test void returnsEveryResourceForTheAdministrator() {
        List<LibraryResourceAdminResponse> resources = List.of(mock(LibraryResourceAdminResponse.class));
        when(service.getAdminResources()).thenReturn(resources);

        var response = controller.getAdminResources();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(resources, response.getBody());
    }

    /*
     * Checks: The upload rules (size and allowed file types) are returned.
     * How:    The fake service returns a rules object. The controller returns the same object with
     *   200.
     */
    @Test void returnsTheUploadRules() {
        UploadRulesResponse rules = new UploadRulesResponse(
                1000L, "1 KB", List.of("pdf", "png"), "PDF or PNG");
        when(service.getUploadRules()).thenReturn(rules);

        var response = controller.getUploadRules();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(rules, response.getBody());
    }

    /*
     * Checks: Creating a resource builds the request from the form fields and answers 201 Created.
     * How:    We call the controller with title, description, category, link, alt text and a file. A
     *   captor reads the request the service received, and each field must match.
     */
    @Test void createBuildsTheRequestFromTheFormAndReturnsCreated() {
        MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf", new byte[] {1});
        LibraryResourceAdminResponse expected = mock(LibraryResourceAdminResponse.class);
        when(authentication.getName()).thenReturn("casey@example.com");
        when(service.create(any(), any(), eq("casey@example.com"))).thenReturn(expected);

        var response = controller.createResource(
                "Title", "Description", "Guides", "https://example.org/a", "Alt",
                true, false, file, authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(expected, response.getBody());

        ArgumentCaptor<LibraryResourceRequest> captor = ArgumentCaptor.forClass(LibraryResourceRequest.class);
        verify(service).create(captor.capture(), same(file), eq("casey@example.com"));
        LibraryResourceRequest sent = captor.getValue();
        assertEquals("Title", sent.title());
        assertEquals("Description", sent.description());
        assertEquals("Guides", sent.category());
        assertEquals("https://example.org/a", sent.linkUrls());
        assertEquals("Alt", sent.altText());
        assertTrue(sent.publish());
        assertFalse(sent.removeFile());
    }

    /*
     * Checks: A resource can be created without a file.
     * How:    The file is null. The service is verified to be called with a null file and the admin's
     *   email, and the answer is 201.
     */
    @Test void createWorksWithoutAFile() {
        when(authentication.getName()).thenReturn("casey@example.com");
        when(service.create(any(), isNull(), eq("casey@example.com")))
                .thenReturn(mock(LibraryResourceAdminResponse.class));

        var response = controller.createResource(
                "Title", "Description", "Guides", "https://example.org/a", null,
                false, false, null, authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(service).create(any(), isNull(), eq("casey@example.com"));
    }

    /*
     * Checks: Updating a resource uses the id from the URL and answers 200.
     * How:    We update id 5. The captured request has no link, publish false and removeFile true.
     */
    @Test void updateUsesTheIdAndReturnsOk() {
        LibraryResourceAdminResponse expected = mock(LibraryResourceAdminResponse.class);
        when(authentication.getName()).thenReturn("casey@example.com");
        when(service.update(eq(5L), any(), isNull(), eq("casey@example.com"))).thenReturn(expected);

        var response = controller.updateResource(
                5L, "New", "Desc", "Guides", null, null,
                false, true, null, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());

        ArgumentCaptor<LibraryResourceRequest> captor = ArgumentCaptor.forClass(LibraryResourceRequest.class);
        verify(service).update(eq(5L), captor.capture(), isNull(), eq("casey@example.com"));
        assertNull(captor.getValue().linkUrls());
        assertFalse(captor.getValue().publish());
        assertTrue(captor.getValue().removeFile());
    }

    /*
     * Checks: Removing a resource answers 204 No Content.
     * How:    The body is null, and service.remove is called with id 5 and the admin's email.
     */
    @Test void removeReturnsNoContent() {
        when(authentication.getName()).thenReturn("casey@example.com");

        var response = controller.removeResource(5L, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
        verify(service).remove(5L, "casey@example.com");
    }
}