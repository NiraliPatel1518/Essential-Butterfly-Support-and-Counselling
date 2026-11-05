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

    @Test void returnsPublicFeatureList() {
        when(service.getFeatures()).thenReturn(List.of());
        assertEquals(HttpStatus.OK, controller.getPublicFeatures().getStatusCode());
        verify(service).getFeatures();
    }
    @Test void returnsAdminFeatureList() {
        when(service.getFeatures()).thenReturn(List.of());
        assertEquals(HttpStatus.OK, controller.getAdminFeatures().getStatusCode());
    }
    @Test void updatesFeatureVisibility() {
        List<FeatureVisibilityRequest> requests = List.of(mock(FeatureVisibilityRequest.class));
        when(service.updateFeatures(requests)).thenReturn(List.of());
        assertEquals(HttpStatus.OK, controller.updateFeatures(requests).getStatusCode());
        verify(service).updateFeatures(requests);
    }
}
