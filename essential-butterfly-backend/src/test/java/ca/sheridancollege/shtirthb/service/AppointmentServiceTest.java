package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import ca.sheridancollege.shtirthb.dto.AdminAppointmentResponse;
import ca.sheridancollege.shtirthb.dto.AppointmentBookingRequest;
import ca.sheridancollege.shtirthb.dto.AppointmentFeedbackRequest;
import ca.sheridancollege.shtirthb.dto.ClientAppointmentResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.Appointment;
import ca.sheridancollege.shtirthb.model.AppointmentFeedback;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.AppointmentFeedbackRepository;
import ca.sheridancollege.shtirthb.repository.AppointmentRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

/*
 * Unit tests for AppointmentService.
 *
 * This is the booking and decision logic. It checks that:
 *   - A client can only book an open slot, with a verified email, when the feature is on.
 *   - The administrator can accept or decline a request, and cancel until it starts.
 *   - Only a confirmed appointment can get a meeting link.
 *   - Feedback and private notes can only be written once the appointment has started.
 *   - A client never sees private notes.
 *   - Search text is matched literally, never as a pattern.
 *
 * "Now" is fixed to Monday 16 November 2026, 09:00. The appointment used in most tests
 * starts at 10:00 the same day, so it has not started yet.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentServiceTest {

    private static final String ADMIN = "admin@mail.com";
    private static final String CLIENT_EMAIL = "ava@mail.com";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 9, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 16, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 16, 11, 0);

    @Mock private AppointmentRepository appointments;
    @Mock private AppointmentFeedbackRepository feedbacks;
    @Mock private ClientUserRepository clients;
    @Mock private AppointmentScheduleService schedule;
    @Mock private AuditTrailService auditTrail;
    @Mock private AppointmentClock clock;

    private ClientUser client;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        client = new ClientUser("Ava Client", CLIENT_EMAIL, "password");
        ReflectionTestUtils.setField(client, "id", 7L);

        when(clock.now()).thenReturn(NOW);
        when(clients.findByEmail(CLIENT_EMAIL)).thenReturn(Optional.of(client));
        when(schedule.isFeatureEnabled()).thenReturn(true);
        when(appointments.saveAndFlush(any(Appointment.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(feedbacks.saveAndFlush(any(AppointmentFeedback.class)))
                .thenAnswer(call -> call.getArgument(0));

        service = new AppointmentService(
                appointments, feedbacks, clients, schedule, auditTrail, clock);
    }

    // ------------------------------------------------------------- helpers

    private Appointment stored(long id, LocalDateTime start, LocalDateTime end) {
        Appointment appointment =
                new Appointment(client, start, end, NOW.minusDays(1));
        ReflectionTestUtils.setField(appointment, "id", id);
        when(appointments.findById(id)).thenReturn(Optional.of(appointment));
        return appointment;
    }

    private Appointment requested() {
        return stored(1L, START, END);
    }

    private Appointment confirmed() {
        Appointment appointment = requested();
        appointment.confirm(NOW);
        return appointment;
    }

    // A confirmed appointment that started an hour ago.
    private Appointment started() {
        Appointment appointment = stored(2L, NOW.minusHours(1), NOW.plusHours(1));
        appointment.confirm(NOW.minusDays(1));
        return appointment;
    }

    private Appointment completed() {
        Appointment appointment = stored(3L, NOW.minusDays(1), NOW.minusDays(1).plusHours(1));
        ReflectionTestUtils.setField(appointment, "status", Appointment.COMPLETED);
        return appointment;
    }

    private static AppointmentFeedbackRequest feedback(String feedback, String notes) {
        return new AppointmentFeedbackRequest(feedback, notes);
    }

    // =============================================================== booking

    /*
     * Checks: A client can book an open slot.
     * How:    Mockito fakes, clock fixed at NOW. The schedule says the slot is open. The response has
     *   status REQUESTED, the right start and end times and the requested time.
     */
    @Test
    void booksAnOpenSlot() {
        when(schedule.requireOpenSlot(START))
                .thenReturn(new AppointmentScheduleService.OpenSlot(START, END));

        ClientAppointmentResponse response = service.request(
                CLIENT_EMAIL, new AppointmentBookingRequest("2026-11-16T10:00:00"));

        assertEquals("REQUESTED", response.status());
        assertEquals("2026-11-16T10:00", response.startsAt());
        assertEquals("2026-11-16T11:00", response.endsAt());
        assertEquals(NOW.toString(), response.requestedAt());
        assertNull(response.feedback());
        assertNull(response.meetingLink());
    }

    /*
     * Checks: The request is saved for the right client and the client is recorded.
     * How:    We capture the saved appointment and check its client. The audit trail is told
     *   actAsClient(7).
     */
    @Test
    void savesTheRequestForTheRightClientAndRecordsWho() {
        when(schedule.requireOpenSlot(START))
                .thenReturn(new AppointmentScheduleService.OpenSlot(START, END));

        service.request(CLIENT_EMAIL, new AppointmentBookingRequest("2026-11-16T10:00:00"));

        ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
        verify(appointments).saveAndFlush(saved.capture());
        assertEquals(client, saved.getValue().getClient());
        verify(auditTrail).actAsClient(7L);
    }

    /*
     * Checks: Booking is locked before the slot is checked.
     * How:    Mockito InOrder: lockForBooking, then requireOpenSlot, then saveAndFlush. This stops two
     *   clients booking the same slot.
     */
    @Test
    void locksBookingBeforeCheckingTheSlot() {
        when(schedule.requireOpenSlot(START))
                .thenReturn(new AppointmentScheduleService.OpenSlot(START, END));

        service.request(CLIENT_EMAIL, new AppointmentBookingRequest("2026-11-16T10:00:00"));

        InOrder order = inOrder(schedule, appointments);
        order.verify(schedule).lockForBooking();
        order.verify(schedule).requireOpenSlot(START);
        order.verify(appointments).saveAndFlush(any(Appointment.class));
    }

    /*
     * Checks: Spaces around the start time are accepted.
     * How:    The schedule is asked for the clean start time.
     */
    @Test
    void acceptsSpacesAroundTheStartTime() {
        when(schedule.requireOpenSlot(START))
                .thenReturn(new AppointmentScheduleService.OpenSlot(START, END));

        service.request(CLIENT_EMAIL, new AppointmentBookingRequest("  2026-11-16T10:00:00  "));

        verify(schedule).requireOpenSlot(START);
    }

    /*
     * Checks: A slot that is no longer open is refused.
     * How:    The schedule throws ConflictException with SLOT_UNAVAILABLE_MESSAGE and nothing is
     *   saved.
     */
    @Test
    void refusesASlotThatIsNoLongerOpen() {
        when(schedule.requireOpenSlot(START)).thenThrow(
                new AppExceptions.ConflictException(
                        AppointmentScheduleService.SLOT_UNAVAILABLE_MESSAGE));

        AppExceptions.ConflictException error = assertThrows(
                AppExceptions.ConflictException.class,
                () -> service.request(CLIENT_EMAIL,
                        new AppointmentBookingRequest("2026-11-16T10:00:00")));

        assertEquals(AppointmentScheduleService.SLOT_UNAVAILABLE_MESSAGE, error.getMessage());
        verify(appointments, never()).saveAndFlush(any(Appointment.class));
    }

    /*
     * Checks: A missing or unreadable start time is refused.
     * How:    Each bad value gives "Choose an appointment time from the list." and nothing is saved.
     */
    @Test
    void refusesAMissingOrUnreadableStartTime() {
        for (AppointmentBookingRequest request : new AppointmentBookingRequest[] {
                null,
                new AppointmentBookingRequest(null),
                new AppointmentBookingRequest(""),
                new AppointmentBookingRequest("tomorrow"),
                new AppointmentBookingRequest("2026-13-45T10:00:00")}) {

            AppExceptions.ValidationException error = assertThrows(
                    AppExceptions.ValidationException.class,
                    () -> service.request(CLIENT_EMAIL, request));

            assertEquals("Choose an appointment time from the list.", error.getMessage());
        }

        verify(appointments, never()).saveAndFlush(any(Appointment.class));
    }

    /*
     * Checks: An unknown client account is refused.
     * How:    It throws ResourceNotFoundException.
     */
    @Test
    void refusesAnUnknownClientAccount() {
        when(clients.findByEmail("nobody@mail.com")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.request("nobody@mail.com",
                        new AppointmentBookingRequest("2026-11-16T10:00:00")));
    }

    /*
     * Checks: A client with an unverified email cannot book.
     * How:    It throws EmailNotVerifiedException and nothing is saved.
     */
    @Test
    void refusesAClientWhoHasNotVerifiedTheirEmail() {
        client.setEmailVerified(false);

        assertThrows(AppExceptions.EmailNotVerifiedException.class,
                () -> service.request(CLIENT_EMAIL,
                        new AppointmentBookingRequest("2026-11-16T10:00:00")));

        verify(appointments, never()).saveAndFlush(any(Appointment.class));
    }

    /*
     * Checks: Booking is refused when the feature is off.
     * How:    The message is "Appointment booking is not available right now." and the booking is
     *   never locked.
     */
    @Test
    void refusesBookingWhenTheFeatureIsSwitchedOff() {
        when(schedule.isFeatureEnabled()).thenReturn(false);

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.request(CLIENT_EMAIL,
                        new AppointmentBookingRequest("2026-11-16T10:00:00")));

        assertEquals("Appointment booking is not available right now.", error.getMessage());
        verify(schedule, never()).lockForBooking();
    }

    // ============================================================ my bookings

    /*
     * Checks: The client's list shows their own bookings with the feedback they may see.
     * How:    One COMPLETED booking shows the feedback text and its update time.
     */
    @Test
    void listsTheClientsOwnBookingsWithTheFeedbackTheyMaySee() {
        Appointment appointment = completed();
        when(appointments.findAllForClient(7L)).thenReturn(List.of(appointment));
        when(feedbacks.findAllByAppointmentIdIn(List.of(3L))).thenReturn(List.of(
                new AppointmentFeedback(3L, "Great progress", "Private worry", NOW)));

        List<ClientAppointmentResponse> list = service.mine(CLIENT_EMAIL);

        assertEquals(1, list.size());
        assertEquals("COMPLETED", list.get(0).status());
        assertEquals("Great progress", list.get(0).feedback());
        assertEquals(NOW.toString(), list.get(0).feedbackUpdatedAt());
        assertFalse(list.get(0).toString().contains("Private worry"));
    }

    /*
     * Checks: Past appointments are marked finished before listing.
     * How:    completeFinished(NOW) is called.
     */
    @Test
    void finishesPastAppointmentsBeforeListing() {
        when(appointments.findAllForClient(7L)).thenReturn(List.of());

        service.mine(CLIENT_EMAIL);

        verify(appointments).completeFinished(NOW);
    }

    /*
     * Checks: No feedback lookup when there are no bookings.
     * How:    The list is empty and the feedback repository is never used.
     */
    @Test
    void doesNotLookForFeedbackWhenThereAreNoBookings() {
        when(appointments.findAllForClient(7L)).thenReturn(List.of());

        assertTrue(service.mine(CLIENT_EMAIL).isEmpty());

        verify(feedbacks, never()).findAllByAppointmentIdIn(any());
    }

    /*
     * Checks: No feedback is shown when none was written.
     * How:    The feedback and its update time are null.
     */
    @Test
    void showsNoFeedbackWhenNoneWasWritten() {
        Appointment one = requested();
        when(appointments.findAllForClient(7L)).thenReturn(List.of(one));

        ClientAppointmentResponse response = service.mine(CLIENT_EMAIL).get(0);

        assertNull(response.feedback());
        assertNull(response.feedbackUpdatedAt());
    }

    /*
     * Checks: Listing for an unknown client gives not found.
     * How:    It throws ResourceNotFoundException.
     */
    @Test
    void refusesToListForAnUnknownClient() {
        when(clients.findByEmail("nobody@mail.com")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.mine("nobody@mail.com"));
    }

    // ============================================================ admin list

    /*
     * Checks: The admin list shows client details and flags.
     * How:    A CONFIRMED row shows the client name and email and calendarEventAdded true.
     */
    @Test
    void listsAppointmentsWithClientDetailsAndFlags() {
        Appointment appointment = confirmed();
        appointment.saveGoogleEventId("event-1", NOW);
        when(appointments.search("", true, "%%")).thenReturn(List.of(appointment));

        AdminAppointmentResponse row = service.adminList(null, null).get(0);

        assertEquals("CONFIRMED", row.status());
        assertEquals("Ava Client", row.clientName());
        assertEquals(CLIENT_EMAIL, row.clientEmail());
        assertTrue(row.calendarEventAdded());
        assertFalse(row.started());
        assertTrue(row.canCancel());
        assertFalse(row.feedbackDue());
    }

    /*
     * Checks: A started time is marked as started and cannot be cancelled.
     * How:    started is true and canCancel is false.
     */
    @Test
    void marksTheTimeAsStartedOnceItHasBegun() {
        Appointment began = started();
        when(appointments.search("", true, "%%")).thenReturn(List.of(began));

        AdminAppointmentResponse row = service.adminList("", "").get(0);

        assertTrue(row.started());
        assertFalse(row.canCancel());
    }

    /*
     * Checks: A completed appointment with no feedback is marked feedbackDue.
     * How:    feedbackDue is true.
     */
    @Test
    void asksForFeedbackOnACompletedAppointmentWithNone() {
        Appointment done = completed();
        when(appointments.search("", true, "%%")).thenReturn(List.of(done));

        assertTrue(service.adminList(null, null).get(0).feedbackDue());
    }

    /*
     * Checks: Feedback that already exists is not asked for again.
     * How:    feedbackDue is false.
     */
    @Test
    void doesNotAskForFeedbackThatAlreadyExists() {
        Appointment appointment = completed();
        when(appointments.search("", true, "%%")).thenReturn(List.of(appointment));
        when(feedbacks.findAllByAppointmentIdIn(List.of(3L))).thenReturn(List.of(
                new AppointmentFeedback(3L, "Done", null, NOW)));

        assertFalse(service.adminList(null, null).get(0).feedbackDue());
    }

    /*
     * Checks: Feedback is still due when only a private note exists.
     * How:    feedbackDue is true and the private note "Only a note" is shown.
     */
    @Test
    void stillAsksForFeedbackWhenOnlyAPrivateNoteExists() {
        Appointment appointment = completed();
        when(appointments.search("", true, "%%")).thenReturn(List.of(appointment));
        when(feedbacks.findAllByAppointmentIdIn(List.of(3L))).thenReturn(List.of(
                new AppointmentFeedback(3L, null, "Only a note", NOW)));

        AdminAppointmentResponse row = service.adminList(null, null).get(0);

        assertTrue(row.feedbackDue());
        assertEquals("Only a note", row.privateNotes());
    }

    /*
     * Checks: A deleted client is shown as "Deleted account".
     * How:    The name is "Deleted account" and the email and phone are null.
     */
    @Test
    void showsDeletedAccountWhenTheClientIsGone() {
        Appointment orphan = new Appointment(null, START, END, NOW);
        ReflectionTestUtils.setField(orphan, "id", 9L);
        when(appointments.search("", true, "%%")).thenReturn(List.of(orphan));

        AdminAppointmentResponse row = service.adminList(null, null).get(0);

        assertEquals("Deleted account", row.clientName());
        assertNull(row.clientEmail());
        assertNull(row.clientPhone());
    }

    /*
     * Checks: A status search ignores capitals and spaces.
     * How:    The repository search is called with CONFIRMED.
     */
    @Test
    void searchesByStatusIgnoringCapitalsAndSpaces() {
        service.adminList(null, "  confirmed ");

        verify(appointments).search("CONFIRMED", true, "%%");
    }

    /*
     * Checks: A status that does not exist is refused.
     * How:    The message is "Choose a valid appointment status."
     */
    @Test
    void refusesAStatusThatDoesNotExist() {
        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.adminList(null, "PENDING"));

        assertEquals("Choose a valid appointment status.", error.getMessage());
    }

    /*
     * Checks: Every real status is accepted.
     * How:    A loop searches with each status name.
     */
    @Test
    void acceptsEveryRealStatus() {
        for (String status : new String[] {
                "REQUESTED", "CONFIRMED", "DECLINED", "CANCELLED", "COMPLETED"}) {
            service.adminList(null, status);
            verify(appointments).search(status, true, "%%");
        }
    }

    /*
     * Checks: Search words are changed to lower case.
     * How:    The search text becomes %ava%.
     */
    @Test
    void searchesWordsInLowerCase() {
        service.adminList("  AVA  ", null);

        verify(appointments).search("", false, "%ava%");
    }

    /*
     * Checks: %, _ and \ in the search are matched literally.
     * How:    They are escaped, so "50%_a\b" becomes %50\%\_a\\b%.
     */
    @Test
    void matchesPercentUnderscoreAndBackslashLiterally() {
        service.adminList("50%_a\\b", null);

        verify(appointments).search("", false, "%50\\%\\_a\\\\b%");
    }

    /*
     * Checks: Very long search text is refused.
     * How:    The message is "Search text is too long."
     */
    @Test
    void refusesVeryLongSearchText() {
        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.adminList("x".repeat(81), null));

        assertEquals("Search text is too long.", error.getMessage());
        service.adminList("x".repeat(80), null);
    }

    /*
     * Checks: Past appointments are finished before searching.
     * How:    completeFinished(NOW) is called.
     */
    @Test
    void finishesPastAppointmentsBeforeSearching() {
        service.adminList(null, null);

        verify(appointments).completeFinished(NOW);
    }

    // ================================================================ confirm

    /*
     * Checks: The admin can accept a requested appointment.
     * How:    The status becomes CONFIRMED. The admin is recorded and the appointment is saved.
     */
    @Test
    void acceptsARequestedAppointment() {
        Appointment appointment = requested();

        AdminAppointmentResponse response = service.confirm(1L, ADMIN);

        assertEquals("CONFIRMED", response.status());
        assertEquals("CONFIRMED", appointment.getStatus());
        verify(auditTrail).actAs(ADMIN);
        verify(appointments).saveAndFlush(appointment);
    }

    /*
     * Checks: Only a requested appointment can be accepted.
     * How:    Other statuses give "Only a requested appointment can be accepted." and nothing is
     *   saved.
     */
    @Test
    void onlyARequestedAppointmentCanBeAccepted() {
        for (String status : new String[] {"CONFIRMED", "DECLINED", "CANCELLED", "COMPLETED"}) {
            Appointment appointment = requested();
            ReflectionTestUtils.setField(appointment, "status", status);

            AppExceptions.BusinessRuleException error = assertThrows(
                    AppExceptions.BusinessRuleException.class,
                    () -> service.confirm(1L, ADMIN));

            assertEquals("Only a requested appointment can be accepted.", error.getMessage());
        }

        verify(appointments, never()).saveAndFlush(any(Appointment.class));
    }

    /*
     * Checks: A time that has already started cannot be accepted.
     * How:    The message is "This time has already started. Decline the request instead."
     */
    @Test
    void cannotAcceptATimeThatHasAlreadyStarted() {
        stored(5L, NOW.minusMinutes(5), NOW.plusMinutes(55));

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.confirm(5L, ADMIN));

        assertEquals("This time has already started. Decline the request instead.",
                error.getMessage());
    }

    /*
     * Checks: Accepting at the exact start time is refused.
     * How:    It throws BusinessRuleException.
     */
    @Test
    void cannotAcceptAtTheExactStartTime() {
        stored(5L, NOW, NOW.plusHours(1));

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.confirm(5L, ADMIN));
    }

    /*
     * Checks: An unknown appointment gives not found.
     * How:    Four admin actions each throw ResourceNotFoundException.
     */
    @Test
    void reportsAnUnknownAppointment() {
        when(appointments.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.confirm(99L, ADMIN));
        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.decline(99L, "Busy", ADMIN));
        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.cancel(99L, null, ADMIN));
        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.saveMeetingLink(99L, "https://meet.example.org/a", ADMIN));
        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.adminView(99L));
    }

    // ================================================================ decline

    /*
     * Checks: The admin can decline a request with a reason.
     * How:    The status becomes DECLINED with the reason "Fully booked", and the admin is recorded.
     */
    @Test
    void declinesARequestWithAReason() {
        Appointment appointment = requested();

        AdminAppointmentResponse response = service.decline(1L, "  Fully booked  ", ADMIN);

        assertEquals("DECLINED", response.status());
        assertEquals("Fully booked", response.statusReason());
        assertEquals("Fully booked", appointment.getStatusReason());
        verify(auditTrail).actAs(ADMIN);
    }

    /*
     * Checks: A reason is required to decline.
     * How:    Blank reasons give "Enter a short reason for declining this request."
     */
    @Test
    void aReasonIsRequiredToDecline() {
        requested();

        for (String reason : new String[] {null, "", "   "}) {
            AppExceptions.ValidationException error = assertThrows(
                    AppExceptions.ValidationException.class,
                    () -> service.decline(1L, reason, ADMIN));

            assertEquals("Enter a short reason for declining this request.", error.getMessage());
        }
    }

    /*
     * Checks: A reason of 500 characters is fine, and 501 is refused.
     * How:    501 gives "Please keep this under 500 characters." 500 gives DECLINED.
     */
    @Test
    void aReasonOf500CharactersIsFineAnd501IsRefused() {
        requested();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.decline(1L, "x".repeat(501), ADMIN));

        assertEquals("Please keep this under 500 characters.", error.getMessage());
        assertEquals("DECLINED", service.decline(1L, "x".repeat(500), ADMIN).status());
    }

    /*
     * Checks: Only a requested appointment can be declined.
     * How:    Other statuses give "Only a requested appointment can be declined."
     */
    @Test
    void onlyARequestedAppointmentCanBeDeclined() {
        confirmed();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.decline(1L, "Busy", ADMIN));

        assertEquals("Only a requested appointment can be declined.", error.getMessage());
    }

    // ================================================================= cancel

    /*
     * Checks: The admin can cancel a requested appointment.
     * How:    The status becomes CANCELLED with the reason, no calendar event needs removing, and the
     *   admin is recorded.
     */
    @Test
    void cancelsARequestedAppointment() {
        requested();

        AppointmentService.CancelResult result = service.cancel(1L, "  Sorry  ", ADMIN);

        assertEquals("CANCELLED", result.appointment().status());
        assertEquals("Sorry", result.appointment().statusReason());
        assertNull(result.removedEventId());
        verify(auditTrail).actAs(ADMIN);
    }

    /*
     * Checks: Cancelling needs no reason.
     * How:    The status is CANCELLED and the reason is null.
     */
    @Test
    void cancellingNeedsNoReason() {
        requested();

        AppointmentService.CancelResult result = service.cancel(1L, null, ADMIN);

        assertEquals("CANCELLED", result.appointment().status());
        assertNull(result.appointment().statusReason());
    }

    /*
     * Checks: Cancelling returns the calendar event that must be removed.
     * How:    The result has event-77. The appointment's event id and meeting link are cleared and
     *   calendarEventAdded is false.
     */
    @Test
    void cancellingReturnsTheCalendarEventThatMustBeRemoved() {
        Appointment appointment = confirmed();
        appointment.saveGoogleEventId("event-77", NOW);
        appointment.saveMeetingLink("https://meet.example.org/a", NOW);

        AppointmentService.CancelResult result = service.cancel(1L, null, ADMIN);

        assertEquals("event-77", result.removedEventId());
        assertNull(appointment.getGoogleEventId());
        assertNull(appointment.getMeetingLink());
        assertFalse(result.appointment().calendarEventAdded());
    }

    /*
     * Checks: A finished appointment cannot be cancelled.
     * How:    The message is "This appointment can no longer be cancelled."
     */
    @Test
    void cannotCancelAnAppointmentThatIsAlreadyFinished() {
        for (String status : new String[] {"DECLINED", "CANCELLED", "COMPLETED"}) {
            Appointment appointment = requested();
            ReflectionTestUtils.setField(appointment, "status", status);

            AppExceptions.BusinessRuleException error = assertThrows(
                    AppExceptions.BusinessRuleException.class,
                    () -> service.cancel(1L, null, ADMIN));

            assertEquals("This appointment can no longer be cancelled.", error.getMessage());
        }
    }

    /*
     * Checks: An appointment cannot be cancelled once it has started.
     * How:    The message says so and nothing is saved.
     */
    @Test
    void cannotCancelOnceTheAppointmentHasStarted() {
        started();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.cancel(2L, null, ADMIN));

        assertEquals("An appointment cannot be cancelled once it has started.", error.getMessage());
        verify(appointments, never()).saveAndFlush(any(Appointment.class));
    }

    /*
     * Checks: A cancel reason longer than 500 characters is refused.
     * How:    It throws ValidationException.
     */
    @Test
    void aReasonLongerThan500CharactersIsRefusedWhenCancelling() {
        requested();

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.cancel(1L, "x".repeat(501), ADMIN));
    }

    // ============================================================ meeting link

    /*
     * Checks: An https meeting link can be saved on a confirmed appointment.
     * How:    The response and the entity have the link, the invitation-sent time is null, and the
     *   admin is recorded.
     */
    @Test
    void savesAnHttpsMeetingLinkOnAConfirmedAppointment() {
        Appointment appointment = confirmed();

        AdminAppointmentResponse response =
                service.saveMeetingLink(1L, "  https://meet.example.org/abc  ", ADMIN);

        assertEquals("https://meet.example.org/abc", response.meetingLink());
        assertEquals("https://meet.example.org/abc", appointment.getMeetingLink());
        assertNull(response.invitationSentAt());
        verify(auditTrail).actAs(ADMIN);
    }

    /*
     * Checks: A new link must be emailed again.
     * How:    After a different link, the invitation-sent time is null.
     */
    @Test
    void aNewLinkMustBeEmailedAgain() {
        Appointment appointment = confirmed();
        appointment.saveMeetingLink("https://meet.example.org/old", NOW);
        appointment.markInvitationSent(NOW);

        AdminAppointmentResponse response =
                service.saveMeetingLink(1L, "https://meet.example.org/new", ADMIN);

        assertNull(response.invitationSentAt());
        assertNull(appointment.getMeetingLinkSentAt());
    }

    /*
     * Checks: Saving the same link again changes nothing.
     * How:    The invitation-sent time is kept and nothing is saved.
     */
    @Test
    void savingTheSameLinkAgainChangesNothing() {
        Appointment appointment = confirmed();
        appointment.saveMeetingLink("https://meet.example.org/same", NOW);
        appointment.markInvitationSent(NOW);

        AdminAppointmentResponse response =
                service.saveMeetingLink(1L, "https://meet.example.org/same", ADMIN);

        assertNotNull(response.invitationSentAt());
        verify(appointments, never()).saveAndFlush(any(Appointment.class));
    }

    /*
     * Checks: A link that is not https is refused.
     * How:    Each bad link throws ValidationException.
     */
    @Test
    void refusesALinkThatIsNotHttps() {
        confirmed();

        for (String link : new String[] {
                "http://meet.example.org/a", "ftp://meet.example.org/a",
                "javascript:alert(1)", "meet.example.org/a"}) {

            AppExceptions.ValidationException error = assertThrows(
                    AppExceptions.ValidationException.class,
                    () -> service.saveMeetingLink(1L, link, ADMIN));

            assertEquals(
                    "The meeting link must be a web address that starts with https://",
                    error.getMessage());
        }
    }

    /*
     * Checks: An empty or too-long link is refused.
     * How:    Each gives its own validation message.
     */
    @Test
    void refusesAnEmptyOrTooLongLink() {
        confirmed();

        for (String link : new String[] {null, "", "   ", "https://a.org/" + "x".repeat(2000)}) {
            AppExceptions.ValidationException error = assertThrows(
                    AppExceptions.ValidationException.class,
                    () -> service.saveMeetingLink(1L, link, ADMIN));

            assertEquals(
                    "Paste the meeting link (https://…), up to 2000 characters.",
                    error.getMessage());
        }
    }

    /*
     * Checks: A link that is not a valid web address is refused.
     * How:    The message is "The meeting link is not a valid web address."
     */
    @Test
    void refusesALinkThatIsNotAValidWebAddress() {
        confirmed();

        for (String link : new String[] {"https://exa mple.org/a", "https://"}) {
            AppExceptions.ValidationException error = assertThrows(
                    AppExceptions.ValidationException.class,
                    () -> service.saveMeetingLink(1L, link, ADMIN));

            assertEquals("The meeting link is not a valid web address.", error.getMessage());
        }
    }

    /*
     * Checks: A meeting link can only be added to a confirmed appointment.
     * How:    Other statuses give that exact message.
     */
    @Test
    void aMeetingLinkCanOnlyBeAddedToAConfirmedAppointment() {
        requested();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.saveMeetingLink(1L, "https://meet.example.org/a", ADMIN));

        assertEquals("A meeting link can only be added to a confirmed appointment.",
                error.getMessage());
    }

    // ===================================================== invitation, calendar

    /*
     * Checks: The service records that the invitation was sent.
     * How:    The invitation-sent time is NOW and the admin is recorded.
     */
    @Test
    void recordsThatTheInvitationWasSent() {
        Appointment appointment = confirmed();
        appointment.saveMeetingLink("https://meet.example.org/a", NOW);

        AdminAppointmentResponse response = service.markInvitationSent(1L, ADMIN);

        assertEquals(NOW.toString(), response.invitationSentAt());
        verify(auditTrail).actAs(ADMIN);
    }

    /*
     * Checks: The service records the calendar event.
     * How:    calendarEventAdded is true, the event id is event-5, and the admin is recorded.
     */
    @Test
    void recordsTheCalendarEvent() {
        Appointment appointment = confirmed();

        AdminAppointmentResponse response = service.recordCalendarEvent(1L, "event-5", ADMIN);

        assertTrue(response.calendarEventAdded());
        assertEquals("event-5", appointment.getGoogleEventId());
        verify(auditTrail).actAs(ADMIN);
    }

    /*
     * Checks: The admin can see one appointment.
     * How:    The response has id 1 and status CONFIRMED.
     */
    @Test
    void showsTheCurrentAdminViewOfOneAppointment() {
        confirmed();

        AdminAppointmentResponse response = service.adminView(1L);

        assertEquals(1L, response.id());
        assertEquals("CONFIRMED", response.status());
    }

    // ================================================================ feedback

    /*
     * Checks: Feedback and a note can be saved once the appointment has started.
     * How:    The response has both texts and the update time, and the admin is recorded.
     */
    @Test
    void savesFeedbackAndANoteOnceTheAppointmentHasStarted() {
        started();

        AdminAppointmentResponse response = service.saveFeedback(
                2L, feedback("  Good session  ", "  Follow up  "), ADMIN);

        assertEquals("Good session", response.feedback());
        assertEquals("Follow up", response.privateNotes());
        assertEquals(NOW.toString(), response.feedbackUpdatedAt());
        verify(auditTrail).actAs(ADMIN);
        verify(feedbacks).saveAndFlush(any(AppointmentFeedback.class));
    }

    /*
     * Checks: Feedback can be written for a completed appointment.
     * How:    The feedback text is saved.
     */
    @Test
    void feedbackCanBeWrittenForACompletedAppointment() {
        completed();

        AdminAppointmentResponse response =
                service.saveFeedback(3L, feedback("Well done", null), ADMIN);

        assertEquals("Well done", response.feedback());
    }

    /*
     * Checks: Feedback alone, or a note alone, is enough.
     * How:    Each one saves without the other.
     */
    @Test
    void feedbackAloneOrANoteAloneIsEnough() {
        started();

        assertEquals("Only feedback",
                service.saveFeedback(2L, feedback("Only feedback", null), ADMIN).feedback());
        assertEquals("Only note",
                service.saveFeedback(2L, feedback(null, "Only note"), ADMIN).privateNotes());
    }

    /*
     * Checks: A field left out keeps its old value.
     * How:    The feedback stays "First words", the note changes, and the update time is the old one.
     */
    @Test
    void anOmittedFieldIsKeptWhenEditingExistingFeedback() {
        started();
        AppointmentFeedback existing =
                new AppointmentFeedback(2L, "First words", "First note", NOW.minusHours(1));
        when(feedbacks.findById(2L)).thenReturn(Optional.of(existing));

        AdminAppointmentResponse response =
                service.saveFeedback(2L, feedback(null, "Changed note"), ADMIN);

        assertEquals("First words", response.feedback());
        assertEquals("Changed note", response.privateNotes());
        assertEquals(NOW.minusHours(1).toString(), response.feedbackUpdatedAt());
    }

    /*
     * Checks: Changed feedback gets a new update time.
     * How:    The text changes, the note stays, and the time becomes NOW.
     */
    @Test
    void changedFeedbackGetsANewUpdateTime() {
        started();
        AppointmentFeedback existing =
                new AppointmentFeedback(2L, "First words", "First note", NOW.minusHours(1));
        when(feedbacks.findById(2L)).thenReturn(Optional.of(existing));

        AdminAppointmentResponse response =
                service.saveFeedback(2L, feedback("New words", null), ADMIN);

        assertEquals("New words", response.feedback());
        assertEquals("First note", response.privateNotes());
        assertEquals(NOW.toString(), response.feedbackUpdatedAt());
    }

    /*
     * Checks: Saving nothing is refused.
     * How:    The message is "Write some feedback or a private note before saving."
     */
    @Test
    void refusesToSaveWhenThereIsNothingToSave() {
        for (AppointmentFeedbackRequest request : new AppointmentFeedbackRequest[] {
                null, feedback(null, null)}) {

            AppExceptions.ValidationException error = assertThrows(
                    AppExceptions.ValidationException.class,
                    () -> service.saveFeedback(2L, request, ADMIN));

            assertEquals("Write some feedback or a private note before saving.",
                    error.getMessage());
        }
    }

    /*
     * Checks: Blank feedback or a blank note is refused.
     * How:    Messages: "Write some feedback before saving." and "Write a private note before saving."
     */
    @Test
    void refusesBlankFeedbackOrABlankNote() {
        assertEquals("Write some feedback before saving.",
                assertThrows(AppExceptions.ValidationException.class,
                        () -> service.saveFeedback(2L, feedback("   ", null), ADMIN))
                        .getMessage());

        assertEquals("Write a private note before saving.",
                assertThrows(AppExceptions.ValidationException.class,
                        () -> service.saveFeedback(2L, feedback(null, "   "), ADMIN))
                        .getMessage());
    }

    /*
     * Checks: Feedback over 4000 characters is refused.
     * How:    The message is "Please keep this under 4000 characters."
     */
    @Test
    void refusesFeedbackLongerThan4000Characters() {
        started();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.saveFeedback(2L, feedback("x".repeat(4001), null), ADMIN));

        assertEquals("Please keep this under 4000 characters.", error.getMessage());
        service.saveFeedback(2L, feedback("x".repeat(4000), null), ADMIN);
    }

    /*
     * Checks: Feedback cannot be written before the appointment starts.
     * How:    The message says feedback can be written once a confirmed appointment has started, and
     *   nothing is saved.
     */
    @Test
    void feedbackCannotBeWrittenBeforeTheAppointmentStarts() {
        confirmed();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.saveFeedback(1L, feedback("Early", null), ADMIN));

        assertEquals("Feedback can be written once a confirmed appointment has started.",
                error.getMessage());
        verify(feedbacks, never()).saveAndFlush(any(AppointmentFeedback.class));
    }

    /*
     * Checks: Feedback cannot be written for requested, declined or cancelled appointments.
     * How:    Each throws BusinessRuleException and nothing is saved.
     */
    @Test
    void feedbackCannotBeWrittenForARequestedDeclinedOrCancelledAppointment() {
        for (String status : new String[] {"REQUESTED", "DECLINED", "CANCELLED"}) {
            Appointment appointment = started();
            ReflectionTestUtils.setField(appointment, "status", status);

            assertThrows(AppExceptions.BusinessRuleException.class,
                    () -> service.saveFeedback(2L, feedback("Words", null), ADMIN));
        }

        verify(feedbacks, never()).saveAndFlush(any(AppointmentFeedback.class));
    }

    /*
     * Checks: Past appointments are finished before saving feedback.
     * How:    completeFinished(NOW) is called.
     */
    @Test
    void finishesPastAppointmentsBeforeSavingFeedback() {
        started();

        service.saveFeedback(2L, feedback("Words", null), ADMIN);

        verify(appointments).completeFinished(NOW);
    }

    /*
     * Checks: The text is checked before the appointment is looked up.
     * How:    Bad text throws ValidationException, and findById and completeFinished are never called.
     */
    @Test
    void checksTheTextBeforeLookingForTheAppointment() {
        assertThrows(AppExceptions.ValidationException.class,
                () -> service.saveFeedback(404L, feedback("   ", null), ADMIN));

        verify(appointments, never()).findById(404L);
        verify(appointments, never()).completeFinished(any());
    }
}