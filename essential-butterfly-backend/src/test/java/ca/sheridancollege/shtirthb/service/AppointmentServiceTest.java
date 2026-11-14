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

    @Test
    void acceptsSpacesAroundTheStartTime() {
        when(schedule.requireOpenSlot(START))
                .thenReturn(new AppointmentScheduleService.OpenSlot(START, END));

        service.request(CLIENT_EMAIL, new AppointmentBookingRequest("  2026-11-16T10:00:00  "));

        verify(schedule).requireOpenSlot(START);
    }

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

    @Test
    void refusesAnUnknownClientAccount() {
        when(clients.findByEmail("nobody@mail.com")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.request("nobody@mail.com",
                        new AppointmentBookingRequest("2026-11-16T10:00:00")));
    }

    @Test
    void refusesAClientWhoHasNotVerifiedTheirEmail() {
        client.setEmailVerified(false);

        assertThrows(AppExceptions.EmailNotVerifiedException.class,
                () -> service.request(CLIENT_EMAIL,
                        new AppointmentBookingRequest("2026-11-16T10:00:00")));

        verify(appointments, never()).saveAndFlush(any(Appointment.class));
    }

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

    @Test
    void finishesPastAppointmentsBeforeListing() {
        when(appointments.findAllForClient(7L)).thenReturn(List.of());

        service.mine(CLIENT_EMAIL);

        verify(appointments).completeFinished(NOW);
    }

    @Test
    void doesNotLookForFeedbackWhenThereAreNoBookings() {
        when(appointments.findAllForClient(7L)).thenReturn(List.of());

        assertTrue(service.mine(CLIENT_EMAIL).isEmpty());

        verify(feedbacks, never()).findAllByAppointmentIdIn(any());
    }

    @Test
    void showsNoFeedbackWhenNoneWasWritten() {
        Appointment one = requested();
        when(appointments.findAllForClient(7L)).thenReturn(List.of(one));

        ClientAppointmentResponse response = service.mine(CLIENT_EMAIL).get(0);

        assertNull(response.feedback());
        assertNull(response.feedbackUpdatedAt());
    }

    @Test
    void refusesToListForAnUnknownClient() {
        when(clients.findByEmail("nobody@mail.com")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.mine("nobody@mail.com"));
    }

    // ============================================================ admin list

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

    @Test
    void marksTheTimeAsStartedOnceItHasBegun() {
        Appointment began = started();
        when(appointments.search("", true, "%%")).thenReturn(List.of(began));

        AdminAppointmentResponse row = service.adminList("", "").get(0);

        assertTrue(row.started());
        assertFalse(row.canCancel());
    }

    @Test
    void asksForFeedbackOnACompletedAppointmentWithNone() {
        Appointment done = completed();
        when(appointments.search("", true, "%%")).thenReturn(List.of(done));

        assertTrue(service.adminList(null, null).get(0).feedbackDue());
    }

    @Test
    void doesNotAskForFeedbackThatAlreadyExists() {
        Appointment appointment = completed();
        when(appointments.search("", true, "%%")).thenReturn(List.of(appointment));
        when(feedbacks.findAllByAppointmentIdIn(List.of(3L))).thenReturn(List.of(
                new AppointmentFeedback(3L, "Done", null, NOW)));

        assertFalse(service.adminList(null, null).get(0).feedbackDue());
    }

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

    @Test
    void searchesByStatusIgnoringCapitalsAndSpaces() {
        service.adminList(null, "  confirmed ");

        verify(appointments).search("CONFIRMED", true, "%%");
    }

    @Test
    void refusesAStatusThatDoesNotExist() {
        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.adminList(null, "PENDING"));

        assertEquals("Choose a valid appointment status.", error.getMessage());
    }

    @Test
    void acceptsEveryRealStatus() {
        for (String status : new String[] {
                "REQUESTED", "CONFIRMED", "DECLINED", "CANCELLED", "COMPLETED"}) {
            service.adminList(null, status);
            verify(appointments).search(status, true, "%%");
        }
    }

    @Test
    void searchesWordsInLowerCase() {
        service.adminList("  AVA  ", null);

        verify(appointments).search("", false, "%ava%");
    }

    @Test
    void matchesPercentUnderscoreAndBackslashLiterally() {
        service.adminList("50%_a\\b", null);

        verify(appointments).search("", false, "%50\\%\\_a\\\\b%");
    }

    @Test
    void refusesVeryLongSearchText() {
        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.adminList("x".repeat(81), null));

        assertEquals("Search text is too long.", error.getMessage());
        service.adminList("x".repeat(80), null);
    }

    @Test
    void finishesPastAppointmentsBeforeSearching() {
        service.adminList(null, null);

        verify(appointments).completeFinished(NOW);
    }

    // ================================================================ confirm

    @Test
    void acceptsARequestedAppointment() {
        Appointment appointment = requested();

        AdminAppointmentResponse response = service.confirm(1L, ADMIN);

        assertEquals("CONFIRMED", response.status());
        assertEquals("CONFIRMED", appointment.getStatus());
        verify(auditTrail).actAs(ADMIN);
        verify(appointments).saveAndFlush(appointment);
    }

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

    @Test
    void cannotAcceptATimeThatHasAlreadyStarted() {
        stored(5L, NOW.minusMinutes(5), NOW.plusMinutes(55));

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.confirm(5L, ADMIN));

        assertEquals("This time has already started. Decline the request instead.",
                error.getMessage());
    }

    @Test
    void cannotAcceptAtTheExactStartTime() {
        stored(5L, NOW, NOW.plusHours(1));

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.confirm(5L, ADMIN));
    }

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

    @Test
    void declinesARequestWithAReason() {
        Appointment appointment = requested();

        AdminAppointmentResponse response = service.decline(1L, "  Fully booked  ", ADMIN);

        assertEquals("DECLINED", response.status());
        assertEquals("Fully booked", response.statusReason());
        assertEquals("Fully booked", appointment.getStatusReason());
        verify(auditTrail).actAs(ADMIN);
    }

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

    @Test
    void aReasonOf500CharactersIsFineAnd501IsRefused() {
        requested();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.decline(1L, "x".repeat(501), ADMIN));

        assertEquals("Please keep this under 500 characters.", error.getMessage());
        assertEquals("DECLINED", service.decline(1L, "x".repeat(500), ADMIN).status());
    }

    @Test
    void onlyARequestedAppointmentCanBeDeclined() {
        confirmed();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.decline(1L, "Busy", ADMIN));

        assertEquals("Only a requested appointment can be declined.", error.getMessage());
    }

    // ================================================================= cancel

    @Test
    void cancelsARequestedAppointment() {
        requested();

        AppointmentService.CancelResult result = service.cancel(1L, "  Sorry  ", ADMIN);

        assertEquals("CANCELLED", result.appointment().status());
        assertEquals("Sorry", result.appointment().statusReason());
        assertNull(result.removedEventId());
        verify(auditTrail).actAs(ADMIN);
    }

    @Test
    void cancellingNeedsNoReason() {
        requested();

        AppointmentService.CancelResult result = service.cancel(1L, null, ADMIN);

        assertEquals("CANCELLED", result.appointment().status());
        assertNull(result.appointment().statusReason());
    }

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

    @Test
    void cannotCancelOnceTheAppointmentHasStarted() {
        started();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.cancel(2L, null, ADMIN));

        assertEquals("An appointment cannot be cancelled once it has started.", error.getMessage());
        verify(appointments, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void aReasonLongerThan500CharactersIsRefusedWhenCancelling() {
        requested();

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.cancel(1L, "x".repeat(501), ADMIN));
    }

    // ============================================================ meeting link

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

    @Test
    void recordsThatTheInvitationWasSent() {
        Appointment appointment = confirmed();
        appointment.saveMeetingLink("https://meet.example.org/a", NOW);

        AdminAppointmentResponse response = service.markInvitationSent(1L, ADMIN);

        assertEquals(NOW.toString(), response.invitationSentAt());
        verify(auditTrail).actAs(ADMIN);
    }

    @Test
    void recordsTheCalendarEvent() {
        Appointment appointment = confirmed();

        AdminAppointmentResponse response = service.recordCalendarEvent(1L, "event-5", ADMIN);

        assertTrue(response.calendarEventAdded());
        assertEquals("event-5", appointment.getGoogleEventId());
        verify(auditTrail).actAs(ADMIN);
    }

    @Test
    void showsTheCurrentAdminViewOfOneAppointment() {
        confirmed();

        AdminAppointmentResponse response = service.adminView(1L);

        assertEquals(1L, response.id());
        assertEquals("CONFIRMED", response.status());
    }

    // ================================================================ feedback

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

    @Test
    void feedbackCanBeWrittenForACompletedAppointment() {
        completed();

        AdminAppointmentResponse response =
                service.saveFeedback(3L, feedback("Well done", null), ADMIN);

        assertEquals("Well done", response.feedback());
    }

    @Test
    void feedbackAloneOrANoteAloneIsEnough() {
        started();

        assertEquals("Only feedback",
                service.saveFeedback(2L, feedback("Only feedback", null), ADMIN).feedback());
        assertEquals("Only note",
                service.saveFeedback(2L, feedback(null, "Only note"), ADMIN).privateNotes());
    }

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

    @Test
    void refusesFeedbackLongerThan4000Characters() {
        started();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.saveFeedback(2L, feedback("x".repeat(4001), null), ADMIN));

        assertEquals("Please keep this under 4000 characters.", error.getMessage());
        service.saveFeedback(2L, feedback("x".repeat(4000), null), ADMIN);
    }

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

    @Test
    void finishesPastAppointmentsBeforeSavingFeedback() {
        started();

        service.saveFeedback(2L, feedback("Words", null), ADMIN);

        verify(appointments).completeFinished(NOW);
    }

    @Test
    void checksTheTextBeforeLookingForTheAppointment() {
        assertThrows(AppExceptions.ValidationException.class,
                () -> service.saveFeedback(404L, feedback("   ", null), ADMIN));

        verify(appointments, never()).findById(404L);
        verify(appointments, never()).completeFinished(any());
    }
}