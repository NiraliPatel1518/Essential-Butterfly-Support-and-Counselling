package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.exception.ExceptionMapping;
import ca.sheridancollege.shtirthb.dto.WebsiteUpdateRequest;
import ca.sheridancollege.shtirthb.model.FeatureVisibility;
import ca.sheridancollege.shtirthb.model.WebsiteUpdate;
import ca.sheridancollege.shtirthb.repository.*;

@ExtendWith(MockitoExtension.class)
class WebsiteUpdateServiceTest {
    @Mock WebsiteUpdateRepository updates;
    @Mock FeatureVisibilityRepository features;
    WebsiteUpdateService service;
    @BeforeEach void setUp() { service = new WebsiteUpdateService(updates, features); }

    /*
     * Checks: Public updates are empty when the UPDATES feature is off.
     * How:    The feature repository returns nothing. The public list is empty and the updates
     *   repository is never used.
     */
    @Test void publicUpdatesReturnEmptyWhenFeatureDisabled() {
        when(features.findByFeatureKey("UPDATES")).thenReturn(Optional.empty());
        assertTrue(service.getPublicUpdates().isEmpty());
        verifyNoInteractions(updates);
    }

    /*
     * Checks: Creating an update trims the text and saves it.
     * How:    We send " Launch " and " We have launched ". The service saves the update and returns a
     *   response.
     */
    @Test void createUpdateTrimsFieldsAndPersists() {
        WebsiteUpdate saved = new WebsiteUpdate("Launch", "We have launched", true);
        when(updates.save(any(WebsiteUpdate.class))).thenReturn(saved);
        var response = service.createUpdate(request(" Launch ", " We have launched ", true));
        assertEquals("Launch", response.title());
        assertEquals("We have launched", response.content());
        verify(updates).save(argThat(u -> u.getTitle().equals("Launch") && u.getContent().equals("We have launched")));
    }

    /*
     * Checks: A blank title or a missing published value is refused.
     * How:    Both cases throw ValidationException, and nothing is saved.
     */
    @Test void createUpdateRejectsMissingRequiredFields() {
        assertThrows(AppExceptions.ValidationException.class, () -> service.createUpdate(request(" ", "Body", true)));
        assertThrows(AppExceptions.ValidationException.class, () -> service.createUpdate(request("Title", "Body", null)));
        verifyNoInteractions(updates);
    }

    /*
     * Checks: More than five photos are refused.
     * How:    We set six photos. It throws ValidationException, and the status maps to 400.
     */
    @Test void createUpdateRejectsMoreThanFivePhotos() {
        WebsiteUpdateRequest r = request("Title", "Body", true); r.setPhotos(List.of("data:image/png;base64,QQ==", "data:image/png;base64,QQ==", "data:image/png;base64,QQ==", "data:image/png;base64,QQ==", "data:image/png;base64,QQ==", "data:image/png;base64,QQ=="));
        AppExceptions.ValidationException ex = assertThrows(AppExceptions.ValidationException.class, () -> service.createUpdate(r));
        assertEquals(HttpStatus.BAD_REQUEST, ExceptionMapping.getStatus(ex));
        verifyNoInteractions(updates);
    }

    /*
     * Checks: Deleting an unknown update gives not found.
     * How:    findById(99) returns empty. It throws ResourceNotFoundException and delete() is never
     *   called.
     */
    @Test void deleteUpdateRejectsUnknownId() {
        when(updates.findById(99L)).thenReturn(Optional.empty());
        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> service.deleteUpdate(99L));
        verify(updates, never()).delete(any(WebsiteUpdate.class));
    }

    private WebsiteUpdateRequest request(String title, String content, Boolean published) {
        WebsiteUpdateRequest r = new WebsiteUpdateRequest(); r.setTitle(title); r.setContent(content); r.setPublished(published); return r;
    }
}