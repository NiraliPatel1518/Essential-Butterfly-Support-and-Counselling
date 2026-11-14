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

import ca.sheridancollege.shtirthb.dto.AdminAppointmentResponse;
import ca.sheridancollege.shtirthb.dto.AppointmentActionResponse;
import ca.sheridancollege.shtirthb.dto.AppointmentFeedbackRequest;
import ca.sheridancollege.shtirthb.dto.AppointmentReasonRequest;
import ca.sheridancollege.shtirthb.dto.AppointmentSettingsRequest;
import ca.sheridancollege.shtirthb.dto.AppointmentSettingsResponse;
import ca.sheridancollege.shtirthb.dto.AvailabilityRuleRequest;
import ca.sheridancollege.shtirthb.dto.AvailabilityRuleResponse;
import ca.sheridancollege.shtirthb.dto.MeetingInvitationRequest;
import ca.sheridancollege.shtirthb.service.AppointmentScheduleService;
import ca.sheridancollege.shtirthb.service.AppointmentService;
import ca.sheridancollege.shtirthb.service.AppointmentWorkflowService;

@ExtendWith(MockitoExtension.class)
class AdminAppointmentControllerTest {

    @Mock AppointmentScheduleService schedule;
    @Mock AppointmentService appointments;
    @Mock AppointmentWorkflowService workflow;
    @Mock Authentication authentication;
    @InjectMocks AdminAppointmentController controller;

    private static final String ADMIN = "casey@example.com";

    private void signedIn() {
        when(authentication.getName()).thenReturn(ADMIN);
    }

    // ------------------------------------------------- weekly availability

    @Test void returnsTheAvailabilityRules() {
        List<AvailabilityRuleResponse> rules = List.of(mock(AvailabilityRuleResponse.class));
        when(schedule.getRules()).thenReturn(rules);

        var response = controller.getRules();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(rules, response.getBody());
    }

    @Test void addRuleReturnsCreated() {
        AvailabilityRuleRequest request = new AvailabilityRuleRequest(1, null, "10:00", 60);
        AvailabilityRuleResponse expected = mock(AvailabilityRuleResponse.class);
        signedIn();
        when(schedule.addRule(request, ADMIN)).thenReturn(expected);

        var response = controller.addRule(request, authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test void changeRuleUsesTheIdAndReturnsOk() {
        AvailabilityRuleRequest request = new AvailabilityRuleRequest(2, null, "11:00", 30);
        AvailabilityRuleResponse expected = mock(AvailabilityRuleResponse.class);
        signedIn();
        when(schedule.changeRule(4L, request, ADMIN)).thenReturn(expected);

        var response = controller.changeRule(4L, request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test void removeRuleReturnsNoContent() {
        signedIn();

        var response = controller.removeRule(4L, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(schedule).removeRule(4L, ADMIN);
    }

    // ----------------------------------------------------------- settings

    @Test void returnsTheSettings() {
        AppointmentSettingsResponse settings = mock(AppointmentSettingsResponse.class);
        when(schedule.getSettings()).thenReturn(settings);

        var response = controller.getSettings();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(settings, response.getBody());
    }

    @Test void changeSettingsReturnsTheNewSettings() {
        AppointmentSettingsRequest request = new AppointmentSettingsRequest(4);
        AppointmentSettingsResponse expected = mock(AppointmentSettingsResponse.class);
        signedIn();
        when(schedule.changeSettings(request, ADMIN)).thenReturn(expected);

        var response = controller.changeSettings(request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    // ------------------------------------------------------- appointments

    @Test void listPassesTheSearchAndStatusFilters() {
        List<AdminAppointmentResponse> list = List.of(mock(AdminAppointmentResponse.class));
        when(appointments.adminList("ann", "REQUESTED")).thenReturn(list);

        var response = controller.list("ann", "REQUESTED");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(list, response.getBody());
    }

    @Test void listWithoutFiltersAsksForEverything() {
        when(appointments.adminList(null, null)).thenReturn(List.of());

        controller.list(null, null);

        verify(appointments).adminList(null, null);
    }

    @Test void confirmUsesTheIdAndTheAdministrator() {
        AppointmentActionResponse expected = mock(AppointmentActionResponse.class);
        signedIn();
        when(workflow.confirm(9L, ADMIN)).thenReturn(expected);

        var response = controller.confirm(9L, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test void declinePassesTheReason() {
        AppointmentActionResponse expected = mock(AppointmentActionResponse.class);
        signedIn();
        when(workflow.decline(9L, "Not available", ADMIN)).thenReturn(expected);

        var response = controller.decline(9L, new AppointmentReasonRequest("Not available"), authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(workflow).decline(9L, "Not available", ADMIN);
    }

    @Test void cancelPassesTheReason() {
        AppointmentActionResponse expected = mock(AppointmentActionResponse.class);
        signedIn();
        when(workflow.cancel(9L, "Emergency", ADMIN)).thenReturn(expected);

        var response = controller.cancel(9L, new AppointmentReasonRequest("Emergency"), authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(workflow).cancel(9L, "Emergency", ADMIN);
    }

    @Test void addToCalendarUsesTheIdAndTheAdministrator() {
        AppointmentActionResponse expected = mock(AppointmentActionResponse.class);
        signedIn();
        when(workflow.addToCalendar(9L, ADMIN)).thenReturn(expected);

        var response = controller.addToCalendar(9L, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test void sendInvitationPassesTheMeetingLink() {
        AppointmentActionResponse expected = mock(AppointmentActionResponse.class);
        signedIn();
        when(workflow.sendInvitation(9L, "https://meet.example.org/abc", ADMIN)).thenReturn(expected);

        var response = controller.sendInvitation(
                9L, new MeetingInvitationRequest("https://meet.example.org/abc"), authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(workflow).sendInvitation(9L, "https://meet.example.org/abc", ADMIN);
    }

    @Test void resendInvitationUsesTheIdAndTheAdministrator() {
        AppointmentActionResponse expected = mock(AppointmentActionResponse.class);
        signedIn();
        when(workflow.resendInvitation(9L, ADMIN)).thenReturn(expected);

        var response = controller.resendInvitation(9L, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test void saveFeedbackReturnsTheUpdatedAppointment() {
        AppointmentFeedbackRequest request = new AppointmentFeedbackRequest("Went well", "Private note");
        AdminAppointmentResponse expected = mock(AdminAppointmentResponse.class);
        signedIn();
        when(appointments.saveFeedback(9L, request, ADMIN)).thenReturn(expected);

        var response = controller.saveFeedback(9L, request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test void workflowErrorsAreNotHidden() {
        signedIn();
        when(workflow.confirm(9L, ADMIN)).thenThrow(new IllegalStateException("Already confirmed."));

        var thrown = assertThrows(IllegalStateException.class,
                () -> controller.confirm(9L, authentication));

        assertEquals("Already confirmed.", thrown.getMessage());
    }
}