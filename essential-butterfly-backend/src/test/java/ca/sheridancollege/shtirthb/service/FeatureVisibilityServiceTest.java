package ca.sheridancollege.shtirthb.service;

import ca.sheridancollege.shtirthb.exception.AppExceptions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ca.sheridancollege.shtirthb.dto.FeatureVisibilityRequest;
import ca.sheridancollege.shtirthb.model.FeatureVisibility;
import ca.sheridancollege.shtirthb.repository.FeatureVisibilityRepository;

@ExtendWith(MockitoExtension.class)
class FeatureVisibilityServiceTest {
    @Mock FeatureVisibilityRepository repository;
    FeatureVisibilityService service;
    @BeforeEach void setUp() { service = new FeatureVisibilityService(repository); }

    /*
     * Checks: When no features exist, the five default features are created.
     * How:    Unit test with a mock repository that finds nothing. getFeatures() must call save() five
     *   times.
     */
    @Test void getFeaturesSeedsAllFiveFeatureDefaults() {
        when(repository.findByFeatureKey(anyString())).thenReturn(Optional.empty());
        when(repository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of());
        assertTrue(service.getFeatures().isEmpty());
        verify(repository, times(5)).save(any(FeatureVisibility.class));
    }

    /*
     * Checks: An empty or incomplete feature list is refused.
     * How:    An empty list and a list with only NEWSLETTER both throw ValidationException, and the
     *   repository is never used.
     */
    @Test void updateRejectsMissingOrIncompleteFeatureSet() {
        assertThrows(AppExceptions.ValidationException.class, () -> service.updateFeatures(List.of()));
        List<FeatureVisibilityRequest> invalid = List.of(request("NEWSLETTER", true));
        assertThrows(AppExceptions.ValidationException.class, () -> service.updateFeatures(invalid));
        verifyNoInteractions(repository);
    }

    /*
     * Checks: An unknown feature key is refused.
     * How:    The list has OTHER instead of MEMORY_WALL. It throws ValidationException and the
     *   repository is never used.
     */
    @Test void updateRejectsUnknownFeatureKey() {
        List<FeatureVisibilityRequest> requests = List.of(
            request("NEWSLETTER", true), request("TESTIMONIALS", true),
            request("UPDATES", true), request("APPOINTMENTS", true),
            request("OTHER", true));
        assertThrows(AppExceptions.ValidationException.class, () -> service.updateFeatures(requests));
        verifyNoInteractions(repository);
    }

    /*
     * Checks: A feature with no on/off value is refused.
     * How:    MEMORY_WALL has enabled = null. It throws ValidationException and the repository is
     *   never used.
     */
    @Test void updateRejectsNullVisibility() {
        List<FeatureVisibilityRequest> requests = List.of(
            request("NEWSLETTER", true), request("TESTIMONIALS", true),
            request("UPDATES", true), request("APPOINTMENTS", true),
            request("MEMORY_WALL", null));
        assertThrows(AppExceptions.ValidationException.class, () -> service.updateFeatures(requests));
        verifyNoInteractions(repository);
    }

    private FeatureVisibilityRequest request(String key, Boolean enabled) {
        FeatureVisibilityRequest r = new FeatureVisibilityRequest(); r.setFeatureKey(key); r.setEnabled(enabled); return r;
    }
}