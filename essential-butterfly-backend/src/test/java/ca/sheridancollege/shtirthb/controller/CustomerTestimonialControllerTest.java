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
import ca.sheridancollege.shtirthb.dto.TestimonialRequest;
import ca.sheridancollege.shtirthb.dto.TestimonialResponse;
import ca.sheridancollege.shtirthb.service.CustomerTestimonialService;

@ExtendWith(MockitoExtension.class)
class CustomerTestimonialControllerTest {
    @Mock CustomerTestimonialService service;
    @Mock Authentication authentication;
    @InjectMocks CustomerTestimonialController controller;

    @Test void submitsForAuthenticatedClient() {
        when(authentication.getName()).thenReturn("client@example.com");
        var request = mock(TestimonialRequest.class);
        var expected = mock(TestimonialResponse.class);
        when(service.submit("client@example.com", request)).thenReturn(expected);
        assertSame(expected, controller.submit(request, authentication));
        verify(service).submit("client@example.com", request);
    }
    @Test void listsCurrentClientsTestimonials() {
        when(authentication.getName()).thenReturn("client@example.com");
        when(service.mine("client@example.com")).thenReturn(List.of());
        assertTrue(controller.mine(authentication).isEmpty());
    }
    @Test void listsPublicTestimonials() {
        when(service.publicList()).thenReturn(List.of());
        assertTrue(controller.publicList().isEmpty());
    }
    @Test void listsFeaturedTestimonials() {
        when(service.featured()).thenReturn(List.of());
        assertTrue(controller.featured().isEmpty());
    }
}
