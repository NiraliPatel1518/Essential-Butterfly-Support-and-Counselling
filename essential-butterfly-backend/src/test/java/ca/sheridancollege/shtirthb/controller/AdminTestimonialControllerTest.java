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
import ca.sheridancollege.shtirthb.dto.TestimonialDecisionRequest;
import ca.sheridancollege.shtirthb.dto.TestimonialResponse;
import ca.sheridancollege.shtirthb.service.CustomerTestimonialService;

@ExtendWith(MockitoExtension.class)
class AdminTestimonialControllerTest {
    @Mock CustomerTestimonialService service;
    @InjectMocks AdminTestimonialController controller;

    @Test void listsAllTestimonials() {
        when(service.adminList()).thenReturn(List.of());
        assertTrue(controller.all().isEmpty());
        verify(service).adminList();
    }
    @Test void approvesTestimonial() {
        TestimonialResponse expected = mock(TestimonialResponse.class);
        when(service.review(4L, "APPROVED")).thenReturn(expected);
        assertSame(expected, controller.approve(4L));
    }
    @Test void rejectsTestimonial() {
        TestimonialResponse expected = mock(TestimonialResponse.class);
        when(service.review(4L, "REJECTED")).thenReturn(expected);
        assertSame(expected, controller.reject(4L));
    }
    @Test void updatesDisplaySettings() {
        var request = mock(TestimonialDecisionRequest.class);
        TestimonialResponse expected = mock(TestimonialResponse.class);
        when(service.settings(4L, request)).thenReturn(expected);
        assertSame(expected, controller.settings(4L, request));
    }
    @Test void removesTestimonial() {
        TestimonialResponse expected = mock(TestimonialResponse.class);
        when(service.remove(4L)).thenReturn(expected);
        assertSame(expected, controller.remove(4L));
    }
}
