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

    /*
     * Checks: The controller returns the list of availability rules.
     * How:    The fake schedule service returns a list. The controller answers 200 with the same list.
     */
    @Test void returnsTheAvailabilityRules() {
        List<AvailabilityRuleResponse> rules = List.of(mock(AvailabilityRuleResponse.class));
        when(schedule.getRules()).thenReturn(rules);

        var response = controller.getRules();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(rules, response.getBody());
    }

    /*
     * Checks: Adding an availability rule answers 201 Created.
     * How:    The controller passes the request and the admin's email to the service, and returns the
     *   new rule.
     */
    @Test void addRuleReturnsCreated() {
        AvailabilityRuleRequest request = new AvailabilityRuleRequest(1, null, "10:00", 60);
        AvailabilityRuleResponse expected = mock(AvailabilityRuleResponse.class);
        signedIn();
        when(schedule.addRule(request, ADMIN)).thenReturn(expected);

        var response = controller.addRule(request, authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    /*
     * Checks: Changing a rule uses the rule id from the URL and answers 200.
     * How:    The service is stubbed for id 4. The controller must return its answer.
     */
    @Test void changeRuleUsesTheIdAndReturnsOk() {
        AvailabilityRuleRequest request = new AvailabilityRuleRequest(2, null, "11:00", 30);
        AvailabilityRuleResponse expected = mock(AvailabilityRuleResponse.class);
        signedIn();
        when(schedule.changeRule(4L, request, ADMIN)).thenReturn(expected);

        var response = controller.changeRule(4L, request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    /*
     * Checks: Removing a rule answers 204 No Content.
     * How:    The service removeRule is verified to be called with id 4 and the admin's email.
     */
    @Test void removeRuleReturnsNoContent() {
        signedIn();

        var response = controller.removeRule(4L, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(schedule).removeRule(4L, ADMIN);
    }

    // ----------------------------------------------------------- settings

    /*
     * Checks: The controller returns the appointment settings.
     * How:    The fake service returns a settings object. The answer is 200 with the same object.
     */
    @Test void returnsTheSettings() {
        AppointmentSettingsResponse settings = mock(AppointmentSettingsResponse.class);
        when(schedule.getSettings()).thenReturn(settings);

        var response = controller.getSettings();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(settings, response.getBody());
    }

    /*
     * Checks: Changing the settings returns the new settings.
     * How:    The service gets the request and the admin's email, and the controller returns what it
     *   gives back.
     */
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

    /*
     * Checks: The list passes the search text and the status filter to the service.
     * How:    We call list with "ann" and "REQUESTED". The service is stubbed for exactly those two
     *   values.
     */
    @Test void listPassesTheSearchAndStatusFilters() {
        List<AdminAppointmentResponse> list = List.of(mock(AdminAppointmentResponse.class));
        when(appointments.adminList("ann", "REQUESTED")).thenReturn(list);

        var response = controller.list("ann", "REQUESTED");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(list, response.getBody());
    }

    /*
     * Checks: The list without filters asks the service for everything.
     * How:    We call list with null and null, and verify the service was called with null and null.
     */
    @Test void listWithoutFiltersAsksForEverything() {
        when(appointments.adminList(null, null)).thenReturn(List.of());

        controller.list(null, null);

        verify(appointments).adminList(null, null);
    }

    /*
     * Checks: Confirming uses the appointment id and the admin's email.
     * How:    The fake workflow service is stubbed for id 9 and returns an action result. The
     *   controller answers 200 with it.
     */
    @Test void confirmUsesTheIdAndTheAdministrator() {
        AppointmentActionResponse expected = mock(AppointmentActionResponse.class);
        signedIn();
        when(workflow.confirm(9L, ADMIN)).thenReturn(expected);

        var response = controller.confirm(9L, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    /*
     * Checks: Declining passes the reason to the workflow service.
     * How:    The reason "Not available" is sent, and verify checks the service got id 9, that reason
     *   and the admin email.
     */
    @Test void declinePassesTheReason() {
        AppointmentActionResponse expected = mock(AppointmentActionResponse.class);
        signedIn();
        when(workflow.decline(9L, "Not available", ADMIN)).thenReturn(expected);

        var response = controller.decline(9L, new AppointmentReasonRequest("Not available"), authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(workflow).decline(9L, "Not available", ADMIN);
    }

    /*
     * Checks: Cancelling passes the reason to the workflow service.
     * How:    The reason "Emergency" is sent, and verify checks the service got it.
     */
    @Test void cancelPassesTheReason() {
        AppointmentActionResponse expected = mock(AppointmentActionResponse.class);
        signedIn();
        when(workflow.cancel(9L, "Emergency", ADMIN)).thenReturn(expected);

        var response = controller.cancel(9L, new AppointmentReasonRequest("Emergency"), authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(workflow).cancel(9L, "Emergency", ADMIN);
    }

    /*
     * Checks: Adding to Google Calendar uses the appointment id and the admin's email.
     * How:    The fake workflow is stubbed for id 9. The controller answers 200 with its result.
     */
    @Test void addToCalendarUsesTheIdAndTheAdministrator() {
        AppointmentActionResponse expected = mock(AppointmentActionResponse.class);
        signedIn();
        when(workflow.addToCalendar(9L, ADMIN)).thenReturn(expected);

        var response = controller.addToCalendar(9L, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    /*
     * Checks: Sending an invitation passes the meeting link to the service.
     * How:    We send a link on id 9. The service must be called with that id, the link and the admin
     *   email.
     */
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

    /*
     * Checks: Resending an invitation uses the appointment id and the admin's email.
     * How:    The fake workflow is stubbed for id 9. The controller returns the result with 200.
     */
    @Test void resendInvitationUsesTheIdAndTheAdministrator() {
        AppointmentActionResponse expected = mock(AppointmentActionResponse.class);
        signedIn();
        when(workflow.resendInvitation(9L, ADMIN)).thenReturn(expected);

        var response = controller.resendInvitation(9L, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    /*
     * Checks: Saving feedback returns the updated appointment.
     * How:    We send feedback and a private note. The fake service returns the updated appointment,
     *   and the controller answers 200 with it.
     */
    @Test void saveFeedbackReturnsTheUpdatedAppointment() {
        AppointmentFeedbackRequest request = new AppointmentFeedbackRequest("Went well", "Private note");
        AdminAppointmentResponse expected = mock(AdminAppointmentResponse.class);
        signedIn();
        when(appointments.saveFeedback(9L, request, ADMIN)).thenReturn(expected);

        var response = controller.saveFeedback(9L, request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    /*
     * Checks: An error from the workflow service reaches the caller unchanged.
     * How:    The service throws IllegalStateException with "Already confirmed.". The controller
     *   throws the same message.
     */
    @Test void workflowErrorsAreNotHidden() {
        signedIn();
        when(workflow.confirm(9L, ADMIN)).thenThrow(new IllegalStateException("Already confirmed."));

        var thrown = assertThrows(IllegalStateException.class,
                () -> controller.confirm(9L, authentication));

        assertEquals("Already confirmed.", thrown.getMessage());
    }
}