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

    /*
     * Checks: The open slots are returned with status 200.
     * How:    The fake schedule service returns one slot. The controller returns the same list.
     */
    @Test void returnsTheOpenSlotsWithOkStatus() {
        List<AppointmentSlotResponse> slots = List.of(
                new AppointmentSlotResponse("2026-11-16T10:00:00", "2026-11-16T11:00:00", 60));
        when(schedule.getOpenSlots()).thenReturn(slots);

        var response = controller.getOpenSlots();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(slots, response.getBody());
    }

    /*
     * Checks: An empty list is returned when no slot is open.
     * How:    The fake service returns an empty list. The controller's body is empty.
     */
    @Test void returnsAnEmptyListWhenNoSlotIsOpen() {
        when(schedule.getOpenSlots()).thenReturn(List.of());

        assertTrue(controller.getOpenSlots().getBody().isEmpty());
    }

    /*
     * Checks: Booking uses the signed-in client's email and answers 201 Created.
     * How:    The fake Authentication gives client@example.com. The service request is verified to be
     *   called with that email and the booking request.
     */
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

    /*
     * Checks: A service error, such as a time that is no longer available, is passed on.
     * How:    The service throws IllegalStateException. The controller throws it with the same
     *   message.
     */
    @Test void requestPassesServiceErrorsOn() {
        AppointmentBookingRequest request = new AppointmentBookingRequest("2026-11-16T10:00:00");
        when(authentication.getName()).thenReturn("client@example.com");
        when(appointments.request("client@example.com", request))
                .thenThrow(new IllegalStateException("That time is no longer available."));

        var thrown = assertThrows(IllegalStateException.class,
                () -> controller.request(request, authentication));

        assertEquals("That time is no longer available.", thrown.getMessage());
    }

    /*
     * Checks: My Bookings returns only the signed-in client's bookings.
     * How:    The service is asked for client@example.com only, and the schedule service is never used
     *   (verifyNoInteractions).
     */
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