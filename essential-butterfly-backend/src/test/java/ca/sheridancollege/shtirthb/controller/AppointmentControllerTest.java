package ca.sheridancollege.shtirthb.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import ca.sheridancollege.shtirthb.dto.AppointmentBookingRequest;
import ca.sheridancollege.shtirthb.dto.AppointmentSlotResponse;
import ca.sheridancollege.shtirthb.dto.ClientAppointmentResponse;
import ca.sheridancollege.shtirthb.service.AppointmentScheduleService;
import ca.sheridancollege.shtirthb.service.AppointmentService;

@ExtendWith(MockitoExtension.class)
class AppointmentControllerTest {

    @Mock AppointmentScheduleService schedule;
    @Mock AppointmentService appointments;
    @Mock Authentication authentication;
    @InjectMocks AppointmentController controller;

    @Test void returnsTheOpenSlotsWithOkStatus() {
        List<AppointmentSlotResponse> slots = List.of(
                new AppointmentSlotResponse("2026-11-16T10:00:00", "2026-11-16T11:00:00", 60));
        when(schedule.getOpenSlots()).thenReturn(slots);

        var response = controller.getOpenSlots();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(slots, response.getBody());
    }

    @Test void returnsAnEmptyListWhenNoSlotIsOpen() {
        when(schedule.getOpenSlots()).thenReturn(List.of());

        assertTrue(controller.getOpenSlots().getBody().isEmpty());
    }

    @Test void requestUsesTheSignedInClientAndReturnsCreated() {
        AppointmentBookingRequest request = new AppointmentBookingRequest("2026-11-16T10:00:00");
        ClientAppointmentResponse expected = mock(ClientAppointmentResponse.class);
        when(authentication.getName()).thenReturn("client@example.com");
        when(appointments.request("client@example.com", request)).thenReturn(expected);

        var response = controller.request(request, authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(appointments).request("client@example.com", request);
    }

    @Test void requestPassesServiceErrorsOn() {
        AppointmentBookingRequest request = new AppointmentBookingRequest("2026-11-16T10:00:00");
        when(authentication.getName()).thenReturn("client@example.com");
        when(appointments.request("client@example.com", request))
                .thenThrow(new IllegalStateException("That time is no longer available."));

        var thrown = assertThrows(IllegalStateException.class,
                () -> controller.request(request, authentication));

        assertEquals("That time is no longer available.", thrown.getMessage());
    }

    @Test void mineReturnsOnlyTheSignedInClientsBookings() {
        List<ClientAppointmentResponse> bookings = List.of(mock(ClientAppointmentResponse.class));
        when(authentication.getName()).thenReturn("client@example.com");
        when(appointments.mine("client@example.com")).thenReturn(bookings);

        var response = controller.mine(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(bookings, response.getBody());
        verify(appointments).mine("client@example.com");
        verifyNoInteractions(schedule);
    }
}