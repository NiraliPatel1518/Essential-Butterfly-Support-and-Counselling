package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ca.sheridancollege.shtirthb.dto.AdminAppointmentResponse;
import ca.sheridancollege.shtirthb.dto.AppointmentActionResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentWorkflowServiceTest {

    @Mock AppointmentService appointments;
    @Mock GoogleCalendarService calendar;
    @Mock AppointmentMailService mail;
    @InjectMocks AppointmentWorkflowService workflow;

    private static final String ADMIN = "casey@example.com";
    private static final String START = "2026-11-16T10:00:00";
    private static final String END = "2026-11-16T11:00:00";

    private static AdminAppointmentResponse appointment(
            String status, String meetingLink, boolean calendarEventAdded) {
        return new AdminAppointmentResponse(
                7L, status, START, END, "Ann Client", "ann@example.com", "+14165550100",
                null, meetingLink, null, calendarEventAdded, null, null, null,
                "2026-11-10T09:00:00", false, true, false);
    }

    // ------------------------------------------------------------ confirm

    /*
     * Checks: Confirming an appointment adds a calendar event and records its id.
     * How:    Mockito fakes. The appointment service confirms, the calendar says it is configured and
     *   creates event-1, and the event id is recorded. The result has the recorded appointment
     *   and no warning.
     */
    @Test void confirmAddsACalendarEventAndRecordsIt() {
        AdminAppointmentResponse confirmed = appointment("CONFIRMED", null, false);
        AdminAppointmentResponse recorded = appointment("CONFIRMED", null, true);
        when(appointments.confirm(7L, ADMIN)).thenReturn(confirmed);
        when(calendar.isConfigured()).thenReturn(true);
        when(calendar.createEvent(anyString(), anyString(), any(), any()))
                .thenReturn(new GoogleCalendarService.CalendarResult("event-1", null));
        when(appointments.recordCalendarEvent(7L, "event-1", ADMIN)).thenReturn(recorded);

        AppointmentActionResponse result = workflow.confirm(7L, ADMIN);

        assertSame(recorded, result.appointment());
        assertNull(result.warning());
    }

    /*
     * Checks: The calendar event names the client and uses the clinic times.
     * How:    We capture the arguments of createEvent. The description must be "Client: Ann
     *   Client\nEmail: ...\nPhone: ...".
     */
    @Test void theCalendarEventNamesTheClientAndUsesTheClinicTimes() {
        when(appointments.confirm(7L, ADMIN)).thenReturn(appointment("CONFIRMED", null, false));
        when(calendar.isConfigured()).thenReturn(true);
        when(calendar.createEvent(anyString(), anyString(), any(), any()))
                .thenReturn(new GoogleCalendarService.CalendarResult("event-1", null));

        workflow.confirm(7L, ADMIN);

        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(calendar).createEvent(
                eq("Appointment: Ann Client"),
                description.capture(),
                eq(LocalDateTime.parse(START)),
                eq(LocalDateTime.parse(END)));
        assertEquals("Client: Ann Client\nEmail: ann@example.com\nPhone: +14165550100", description.getValue());
    }

    /*
     * Checks: Missing contact details are left out of the event description.
     * How:    With no email and no phone, the description is only "Client: Ann Client".
     */
    @Test void theCalendarDescriptionLeavesOutMissingContactDetails() {
        AdminAppointmentResponse noContact = new AdminAppointmentResponse(
                7L, "CONFIRMED", START, END, "Ann Client", null, null,
                null, null, null, false, null, null, null,
                "2026-11-10T09:00:00", false, true, false);
        when(appointments.confirm(7L, ADMIN)).thenReturn(noContact);
        when(calendar.isConfigured()).thenReturn(true);
        when(calendar.createEvent(anyString(), anyString(), any(), any()))
                .thenReturn(new GoogleCalendarService.CalendarResult("event-1", null));

        workflow.confirm(7L, ADMIN);

        verify(calendar).createEvent(anyString(), eq("Client: Ann Client"), any(), any());
    }

    /*
     * Checks: The confirmation stays saved and the admin gets a warning if the calendar is not
     *   connected.
     * How:    The calendar is not configured. The result has the confirmed appointment and the
     *   CALENDAR_NOT_CONNECTED warning, and no event is created or recorded.
     */
    @Test void confirmStaysSavedAndWarnsWhenTheCalendarIsNotConnected() {
        AdminAppointmentResponse confirmed = appointment("CONFIRMED", null, false);
        when(appointments.confirm(7L, ADMIN)).thenReturn(confirmed);
        when(calendar.isConfigured()).thenReturn(false);

        AppointmentActionResponse result = workflow.confirm(7L, ADMIN);

        assertSame(confirmed, result.appointment());
        assertEquals(AppointmentWorkflowService.CALENDAR_NOT_CONNECTED, result.warning());
        verify(calendar, never()).createEvent(any(), any(), any(), any());
        verify(appointments, never()).recordCalendarEvent(any(), any(), any());
    }

    /*
     * Checks: The confirmation stays saved and the warning explains when Google refuses.
     * How:    Google returns "Quota exceeded.". The warning is the failure text plus "Google said:
     *   Quota exceeded." and no event is recorded.
     */
    @Test void confirmStaysSavedAndExplainsWhenGoogleSaysNo() {
        AdminAppointmentResponse confirmed = appointment("CONFIRMED", null, false);
        when(appointments.confirm(7L, ADMIN)).thenReturn(confirmed);
        when(calendar.isConfigured()).thenReturn(true);
        when(calendar.createEvent(anyString(), anyString(), any(), any()))
                .thenReturn(new GoogleCalendarService.CalendarResult(null, "Quota exceeded."));

        AppointmentActionResponse result = workflow.confirm(7L, ADMIN);

        assertSame(confirmed, result.appointment());
        assertEquals(AppointmentWorkflowService.CALENDAR_FAILED + "Google said: Quota exceeded.", result.warning());
        verify(appointments, never()).recordCalendarEvent(any(), any(), any());
    }

    /*
     * Checks: Errors from the appointment service are not hidden.
     * How:    confirm throws BusinessRuleException. It is passed up, and the calendar and mail are
     *   never used.
     */
    @Test void confirmErrorsFromTheAppointmentServiceAreNotHidden() {
        when(appointments.confirm(7L, ADMIN))
                .thenThrow(new AppExceptions.BusinessRuleException("Already confirmed."));

        assertThrows(AppExceptions.BusinessRuleException.class, () -> workflow.confirm(7L, ADMIN));
        verifyNoInteractions(calendar, mail);
    }

    // ----------------------------------------------------- addToCalendar

    /*
     * Checks: The admin can retry adding a confirmed appointment to the calendar.
     * How:    The appointment is CONFIRMED with no event. A new event (event-2) is created and
     *   recorded, with no warning.
     */
    @Test void addToCalendarRetriesForAConfirmedAppointmentWithoutAnEvent() {
        when(appointments.adminView(7L)).thenReturn(appointment("CONFIRMED", null, false));
        AdminAppointmentResponse recorded = appointment("CONFIRMED", null, true);
        when(calendar.isConfigured()).thenReturn(true);
        when(calendar.createEvent(anyString(), anyString(), any(), any()))
                .thenReturn(new GoogleCalendarService.CalendarResult("event-2", null));
        when(appointments.recordCalendarEvent(7L, "event-2", ADMIN)).thenReturn(recorded);

        AppointmentActionResponse result = workflow.addToCalendar(7L, ADMIN);

        assertSame(recorded, result.appointment());
        assertNull(result.warning());
    }

    /*
     * Checks: Only a confirmed appointment can be added to the calendar.
     * How:    A REQUESTED appointment throws BusinessRuleException with the exact message, and the
     *   calendar is not used.
     */
    @Test void addToCalendarRefusesAnAppointmentThatIsNotConfirmed() {
        when(appointments.adminView(7L)).thenReturn(appointment("REQUESTED", null, false));

        var thrown = assertThrows(AppExceptions.BusinessRuleException.class,
                () -> workflow.addToCalendar(7L, ADMIN));

        assertEquals("Only a confirmed appointment can be added to the calendar.", thrown.getMessage());
        verifyNoInteractions(calendar);
    }

    /*
     * Checks: Nothing happens if the event already exists.
     * How:    The appointment already has an event. The result is the same appointment, with no
     *   warning and no calendar call.
     */
    @Test void addToCalendarDoesNothingWhenTheEventIsAlreadyThere() {
        AdminAppointmentResponse current = appointment("CONFIRMED", null, true);
        when(appointments.adminView(7L)).thenReturn(current);

        AppointmentActionResponse result = workflow.addToCalendar(7L, ADMIN);

        assertSame(current, result.appointment());
        assertNull(result.warning());
        verifyNoInteractions(calendar);
    }

    // ------------------------------------------------------------ decline

    /*
     * Checks: Declining saves the reason and never uses the calendar or mail.
     * How:    decline() is called with the reason. The calendar and mail are never used.
     */
    @Test void declineSavesTheReasonAndNeverTouchesTheCalendarOrMail() {
        AdminAppointmentResponse declined = appointment("DECLINED", null, false);
        when(appointments.decline(7L, "Not available", ADMIN)).thenReturn(declined);

        AppointmentActionResponse result = workflow.decline(7L, "Not available", ADMIN);

        assertSame(declined, result.appointment());
        assertNull(result.warning());
        verifyNoInteractions(calendar, mail);
    }

    // ------------------------------------------------------------- cancel

    /*
     * Checks: Cancelling removes the calendar event.
     * How:    The cancel result has an event id. deleteEvent is called with it and returns true, so
     *   there is no warning.
     */
    @Test void cancelRemovesTheCalendarEvent() {
        AdminAppointmentResponse cancelled = appointment("CANCELLED", null, false);
        when(appointments.cancel(7L, "Emergency", ADMIN))
                .thenReturn(new AppointmentService.CancelResult(cancelled, "event-1"));
        when(calendar.deleteEvent("event-1")).thenReturn(true);

        AppointmentActionResponse result = workflow.cancel(7L, "Emergency", ADMIN);

        assertSame(cancelled, result.appointment());
        assertNull(result.warning());
        verify(calendar).deleteEvent("event-1");
    }

    /*
     * Checks: Cancelling warns when the event could not be removed.
     * How:    deleteEvent returns false. The warning is CALENDAR_REMOVE_FAILED.
     */
    @Test void cancelWarnsWhenTheCalendarEventCouldNotBeRemoved() {
        AdminAppointmentResponse cancelled = appointment("CANCELLED", null, false);
        when(appointments.cancel(7L, "Emergency", ADMIN))
                .thenReturn(new AppointmentService.CancelResult(cancelled, "event-1"));
        when(calendar.deleteEvent("event-1")).thenReturn(false);

        AppointmentActionResponse result = workflow.cancel(7L, "Emergency", ADMIN);

        assertSame(cancelled, result.appointment());
        assertEquals(AppointmentWorkflowService.CALENDAR_REMOVE_FAILED, result.warning());
    }

    /*
     * Checks: No calendar call when there was no event.
     * How:    The event id is null. There is no warning and the calendar is not used.
     */
    @Test void cancelDoesNotCallTheCalendarWhenThereWasNoEvent() {
        AdminAppointmentResponse cancelled = appointment("CANCELLED", null, false);
        when(appointments.cancel(7L, "Emergency", ADMIN))
                .thenReturn(new AppointmentService.CancelResult(cancelled, null));

        AppointmentActionResponse result = workflow.cancel(7L, "Emergency", ADMIN);

        assertNull(result.warning());
        verifyNoInteractions(calendar);
    }

    /*
     * Checks: A blank event id counts as no event.
     * How:    An id of spaces gives no warning and no calendar call.
     */
    @Test void cancelTreatsABlankEventIdAsNoEvent() {
        AdminAppointmentResponse cancelled = appointment("CANCELLED", null, false);
        when(appointments.cancel(7L, "Emergency", ADMIN))
                .thenReturn(new AppointmentService.CancelResult(cancelled, "   "));

        AppointmentActionResponse result = workflow.cancel(7L, "Emergency", ADMIN);

        assertNull(result.warning());
        verifyNoInteractions(calendar);
    }

    // ---------------------------------------------------- sendInvitation

    /*
     * Checks: Sending the invitation saves the link, emails it and marks it as sent.
     * How:    The mail service returns true. The appointment is marked sent, there is no warning, and
     *   the mail was sent with the link.
     */
    @Test void sendInvitationSavesTheLinkEmailsItAndMarksItSent() {
        AdminAppointmentResponse saved = appointment("CONFIRMED", "https://meet.example.org/abc", false);
        AdminAppointmentResponse marked = appointment("CONFIRMED", "https://meet.example.org/abc", false);
        when(appointments.saveMeetingLink(7L, "https://meet.example.org/abc", ADMIN)).thenReturn(saved);
        when(mail.sendInvitation(anyString(), anyString(), any(), any(), anyString())).thenReturn(true);
        when(appointments.markInvitationSent(7L, ADMIN)).thenReturn(marked);

        AppointmentActionResponse result = workflow.sendInvitation(7L, "https://meet.example.org/abc", ADMIN);

        assertSame(marked, result.appointment());
        assertNull(result.warning());
        verify(mail).sendInvitation(
                "Ann Client", "ann@example.com",
                LocalDateTime.parse(START), LocalDateTime.parse(END),
                "https://meet.example.org/abc");
    }

    /*
     * Checks: The link stays saved and the admin is told when the email fails.
     * How:    The mail service returns false. The result has the saved appointment and the
     *   EMAIL_FAILED warning, and the invitation is not marked as sent.
     */
    @Test void theLinkStaysSavedAndTheAdministratorIsToldWhenTheEmailFails() {
        AdminAppointmentResponse saved = appointment("CONFIRMED", "https://meet.example.org/abc", false);
        when(appointments.saveMeetingLink(7L, "https://meet.example.org/abc", ADMIN)).thenReturn(saved);
        when(mail.sendInvitation(anyString(), anyString(), any(), any(), anyString())).thenReturn(false);

        AppointmentActionResponse result = workflow.sendInvitation(7L, "https://meet.example.org/abc", ADMIN);

        assertSame(saved, result.appointment());
        assertEquals(AppointmentWorkflowService.EMAIL_FAILED, result.warning());
        verify(appointments, never()).markInvitationSent(any(), any());
    }

    // --------------------------------------------------- resendInvitation

    /*
     * Checks: Resending uses the link that is already saved.
     * How:    The appointment already has a link. It is saved again and mailed, with no warning.
     */
    @Test void resendUsesTheLinkThatIsAlreadySaved() {
        AdminAppointmentResponse current = appointment("CONFIRMED", "https://meet.example.org/abc", false);
        when(appointments.adminView(7L)).thenReturn(current);
        when(appointments.saveMeetingLink(7L, "https://meet.example.org/abc", ADMIN)).thenReturn(current);
        when(mail.sendInvitation(anyString(), anyString(), any(), any(), anyString())).thenReturn(true);
        when(appointments.markInvitationSent(7L, ADMIN)).thenReturn(current);

        AppointmentActionResponse result = workflow.resendInvitation(7L, ADMIN);

        assertNull(result.warning());
        verify(appointments).saveMeetingLink(7L, "https://meet.example.org/abc", ADMIN);
        verify(mail).sendInvitation(anyString(), anyString(), any(), any(), eq("https://meet.example.org/abc"));
    }

    /*
     * Checks: Resending needs a link first.
     * How:    With no link, it throws BusinessRuleException ("Add a meeting link first.") and the mail
     *   is not used.
     */
    @Test void resendNeedsALinkFirst() {
        when(appointments.adminView(7L)).thenReturn(appointment("CONFIRMED", null, false));

        var thrown = assertThrows(AppExceptions.BusinessRuleException.class,
                () -> workflow.resendInvitation(7L, ADMIN));

        assertEquals("Add a meeting link first.", thrown.getMessage());
        verifyNoInteractions(mail);
    }
}