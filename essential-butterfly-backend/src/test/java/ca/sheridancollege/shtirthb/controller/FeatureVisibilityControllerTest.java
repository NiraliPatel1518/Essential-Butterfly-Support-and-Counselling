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
import ca.sheridancollege.shtirthb.dto.FeatureVisibilityRequest;
import ca.sheridancollege.shtirthb.dto.FeatureVisibilityResponse;
import ca.sheridancollege.shtirthb.service.FeatureVisibilityService;

@ExtendWith(MockitoExtension.class)
class FeatureVisibilityControllerTest {
    @Mock FeatureVisibilityService service;
    @InjectMocks FeatureVisibilityController controller;

    /*
     * Checks: The public feature list is returned with 200.
     * How:    The fake service getFeatures is called and the status is 200.
     */
    @Test void returnsPublicFeatureList() {
        when(service.getFeatures()).thenReturn(List.of());
        assertEquals(HttpStatus.OK, controller.getPublicFeatures().getStatusCode());
        verify(service).getFeatures();
    }
    /*
     * Checks: The admin feature list is returned with 200.
     * How:    The same service call is used and the status is 200.
     */
    @Test void returnsAdminFeatureList() {
        when(service.getFeatures()).thenReturn(List.of());
        assertEquals(HttpStatus.OK, controller.getAdminFeatures().getStatusCode());
    }
    /*
     * Checks: The admin can update the feature switches.
     * How:    The list of requests is passed to service.updateFeatures. The status is 200 and the call
     *   is verified.
     */
    @Test void updatesFeatureVisibility() {
        List<FeatureVisibilityRequest> requests = List.of(mock(FeatureVisibilityRequest.class));
        when(service.updateFeatures(requests)).thenReturn(List.of());
        assertEquals(HttpStatus.OK, controller.updateFeatures(requests).getStatusCode());
        verify(service).updateFeatures(requests);
    }
}
