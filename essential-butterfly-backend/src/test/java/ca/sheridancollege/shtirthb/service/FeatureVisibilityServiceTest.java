package ca.sheridancollege.shtirthb.service;

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

    @Test void getFeaturesSeedsAllThreeSprintFeatureDefaults() {
        when(repository.findByFeatureKey(anyString())).thenReturn(Optional.empty());
        when(repository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of());
        assertTrue(service.getFeatures().isEmpty());
        verify(repository, times(3)).save(any(FeatureVisibility.class));
    }

    @Test void updateRejectsMissingOrIncompleteFeatureSet() {
        assertThrows(IllegalArgumentException.class, () -> service.updateFeatures(List.of()));
        List<FeatureVisibilityRequest> invalid = List.of(request("NEWSLETTER", true));
        assertThrows(IllegalArgumentException.class, () -> service.updateFeatures(invalid));
        verifyNoInteractions(repository);
    }

    @Test void updateRejectsUnknownFeatureKey() {
        List<FeatureVisibilityRequest> requests = List.of(
            request("NEWSLETTER", true), request("TESTIMONIALS", true), request("OTHER", true));
        assertThrows(IllegalArgumentException.class, () -> service.updateFeatures(requests));
        verifyNoInteractions(repository);
    }

    @Test void updateRejectsNullVisibility() {
        List<FeatureVisibilityRequest> requests = List.of(
            request("NEWSLETTER", true), request("TESTIMONIALS", true), request("UPDATES", null));
        assertThrows(IllegalArgumentException.class, () -> service.updateFeatures(requests));
        verifyNoInteractions(repository);
    }

    private FeatureVisibilityRequest request(String key, Boolean enabled) {
        FeatureVisibilityRequest r = new FeatureVisibilityRequest(); r.setFeatureKey(key); r.setEnabled(enabled); return r;
    }
}
