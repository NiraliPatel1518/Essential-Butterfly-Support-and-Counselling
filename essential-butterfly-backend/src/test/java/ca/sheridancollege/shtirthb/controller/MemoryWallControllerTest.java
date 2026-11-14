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

    @Test void returnsThePublicWallGroupedByMonth() {
        List<MemoryWallMonth> months = List.of(new MemoryWallMonth("2026-11", "November 2026", List.of()));
        when(service.getPublicWall()).thenReturn(months);

        var response = controller.getWall();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(months, response.getBody());
    }

    @Test void publicImageIsSentWithItsTypeAndTheBytes() {
        byte[] bytes = {9, 8, 7};
        when(service.getPublicImage(3L)).thenReturn(new MemoryWallImage("image/png", bytes));

        var response = controller.getPublicImage(3L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("image/png", response.getHeaders().getContentType().toString());
        assertArrayEquals(bytes, response.getBody());
    }

    @Test void publicImageMayBeKeptBrieflyAndIsNeverSniffed() {
        when(service.getPublicImage(3L)).thenReturn(new MemoryWallImage("image/png", new byte[] {1}));

        var headers = controller.getPublicImage(3L).getHeaders();

        assertTrue(headers.getCacheControl().contains("max-age=60"));
        assertTrue(headers.getCacheControl().contains("public"));
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
    }

    @Test void publicImageErrorsFromTheServiceAreNotHidden() {
        when(service.getPublicImage(404L)).thenThrow(new IllegalStateException("not found"));

        assertThrows(IllegalStateException.class, () -> controller.getPublicImage(404L));
    }

    // -------------------------------------------------------------- admin

    @Test void returnsTheAdminOverview() {
        MemoryWallAdminOverview overview = mock(MemoryWallAdminOverview.class);
        when(service.getOverview()).thenReturn(overview);

        var response = controller.getOverview();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(overview, response.getBody());
    }

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

    @Test void uploadWithoutAFileIsLeftToTheServiceToRefuse() {
        when(authentication.getName()).thenReturn("casey@example.com");
        when(service.upload(isNull(), any(), eq("casey@example.com")))
                .thenThrow(new IllegalArgumentException("Please choose a photo."));

        var thrown = assertThrows(IllegalArgumentException.class,
                () -> controller.uploadPhoto(null, "x", authentication));

        assertEquals("Please choose a photo.", thrown.getMessage());
    }

    @Test void changeDescriptionUsesTheIdAndReturnsOk() {
        MemoryWallDescriptionRequest request = new MemoryWallDescriptionRequest("New words");
        MemoryWallPhotoAdminResponse expected = mock(MemoryWallPhotoAdminResponse.class);
        when(authentication.getName()).thenReturn("casey@example.com");
        when(service.changeDescription(6L, request, "casey@example.com")).thenReturn(expected);

        var response = controller.changeDescription(6L, request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

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

    @Test void deleteReturnsNoContent() {
        when(authentication.getName()).thenReturn("casey@example.com");

        var response = controller.deletePhoto(6L, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
        verify(service).delete(6L, "casey@example.com");
    }
}