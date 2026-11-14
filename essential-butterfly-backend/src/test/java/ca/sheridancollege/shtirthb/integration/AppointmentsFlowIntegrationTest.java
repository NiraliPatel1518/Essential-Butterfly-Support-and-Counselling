package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import ca.sheridancollege.shtirthb.model.Appointment;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.AppointmentRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.service.AppointmentClock;
import ca.sheridancollege.shtirthb.service.AppointmentMailService;
import ca.sheridancollege.shtirthb.service.GoogleCalendarService;
import ca.sheridancollege.shtirthb.service.JwtService;

/*
 * UC-11 end-to-end flow: Appointments.
 *
 * Runs the real application against a real PostgreSQL (Testcontainers) with
 * the real Flyway migrations. Google Calendar and the e-mail sender are
 * replaced with fakes, so no real calendar event or e-mail is ever created.
 *
 * It checks: availability rules and settings, the public list of open slots,
 * booking, double booking, accept / decline / cancel, the meeting link and
 * invitation, calendar follow-up warnings, feedback and private notes, what a
 * client may see, the feature switch and the access rules.
 *
 * Open slots depend on the real clock, so every test creates its own
 * one-date slot for tomorrow. Each test rolls back.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///appointments_flow_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890"
})
@AutoConfigureMockMvc
@Transactional
class AppointmentsFlowIntegrationTest {

    private static final String LINK = "https://meet.example.org/room-1";

    private static final String FEATURES =
            "[{\"featureKey\":\"NEWSLETTER\",\"enabled\":true},"
            + "{\"featureKey\":\"TESTIMONIALS\",\"enabled\":true},"
            + "{\"featureKey\":\"UPDATES\",\"enabled\":true},"
            + "{\"featureKey\":\"APPOINTMENTS\",\"enabled\":%s},"
            + "{\"featureKey\":\"MEMORY_WALL\",\"enabled\":true}]";

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private ClientUserRepository clients;
    @Autowired private AppointmentRepository appointments;
    @Autowired private AppointmentClock clock;

    @MockitoBean private GoogleCalendarService calendar;
    @MockitoBean private AppointmentMailService mail;

    private LocalDate tomorrow;

    @BeforeEach
    void setUp() throws Exception {
        tomorrow = clock.now().toLocalDate().plusDays(1);

        // Seeds the feature switches and lets every slot show.
        mvc.perform(get("/api/features")).andExpect(status().isOk());
        mvc.perform(put("/api/admin/appointments/settings")
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hideHoursBeforeStart\":0}"))
                .andExpect(status().isOk());

        when(calendar.isConfigured()).thenReturn(false);
        when(mail.sendInvitation(any(), any(), any(), any(), any())).thenReturn(true);
    }

    // ------------------------------------------------------------ helpers

    private String admin() {
        return "Bearer " + jwt.generateToken("admin@test.com", "ADMIN");
    }

    private String clientToken(String email) {
        return "Bearer " + jwt.generateToken(email, "CLIENT");
    }

    private ClientUser newClient(String name, String email) {
        return clients.save(new ClientUser(name, email, "password-hash"));
    }

    private String ava() {
        if (clients.findByEmail("ava@test.com").isEmpty()) {
            newClient("Ava Client", "ava@test.com");
        }
        return clientToken("ava@test.com");
    }

    private String ben() {
        if (clients.findByEmail("ben@test.com").isEmpty()) {
            newClient("Ben Other", "ben@test.com");
        }
        return clientToken("ben@test.com");
    }

    private String at(String time) {
        return tomorrow + "T" + time + ":00";
    }

    // How a slot appears in the public list (no seconds).
    private String slot(String time) {
        return tomorrow + "T" + time;
    }

    private ResultActions addRule(LocalDate date, String time, int minutes) throws Exception {
        return mvc.perform(post("/api/admin/appointments/availability")
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\",\"startTime\":\"" + time
                        + "\",\"slotMinutes\":" + minutes + "}"));
    }

    private long openSlot(String time) throws Exception {
        String json = addRule(tomorrow, time, 60)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions book(String token, String startsAt) throws Exception {
        return mvc.perform(post("/api/appointments")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startsAt\":\"" + startsAt + "\"}"));
    }

    private long bookOpenSlot(String time) throws Exception {
        openSlot(time);

        String json = book(ava(), at(time))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions adminPost(String path, String body) throws Exception {
        var request = post(path).header(HttpHeaders.AUTHORIZATION, admin());

        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        return mvc.perform(request);
    }

    private ResultActions adminPut(String path, String body) throws Exception {
        return mvc.perform(put(path)
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String openSlotsJson() throws Exception {
        return mvc.perform(get("/api/appointments/availability"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private long confirmed(String time) throws Exception {
        long id = bookOpenSlot(time);
        adminPost("/api/admin/appointments/" + id + "/confirm", null)
                .andExpect(status().isOk());
        return id;
    }

    // A confirmed appointment that started an hour ago (only possible by
    // writing it directly, because the booking rules refuse past times).
    private long startedAppointment() {
        newClient("Ava Client", "ava@test.com");
        ClientUser owner = clients.findByEmail("ava@test.com").orElseThrow();
        LocalDateTime now = clock.now();

        Appointment appointment = new Appointment(
                owner, now.minusHours(1), now.plusHours(1), now.minusDays(1));
        appointment.confirm(now.minusHours(2));

        return appointments.saveAndFlush(appointment).getId();
    }

    // ============================================================ public slots

    @Test
    @DisplayName("Visitors can see open slots without signing in")
    void visitorsSeeOpenSlots() throws Exception {
        openSlot("10:00");

        String json = openSlotsJson();

        assertTrue(json.contains(slot("10:00")), json);
        assertTrue(json.contains(slot("11:00")), json);
    }

    @Test
    @DisplayName("The open slot list never shows who booked")
    void slotListHidesClientDetails() throws Exception {
        openSlot("10:00");
        book(ava(), at("10:00")).andExpect(status().isCreated());

        String json = openSlotsJson();

        assertFalse(json.contains("Ava"), json);
        assertFalse(json.contains("ava@test.com"), json);
    }

    @Test
    @DisplayName("A booked slot disappears from the public list")
    void bookedSlotDisappears() throws Exception {
        openSlot("10:00");
        assertTrue(openSlotsJson().contains(slot("10:00")));

        book(ava(), at("10:00")).andExpect(status().isCreated());

        assertFalse(openSlotsJson().contains(slot("10:00")));
    }

    @Test
    @DisplayName("A slot outside the booking window is not offered")
    void farAwaySlotIsNotOffered() throws Exception {
        LocalDate far = tomorrow.plusDays(60);
        addRule(far, "10:00", 60).andExpect(status().isCreated());

        assertFalse(openSlotsJson().contains(far.toString()));
    }

    @Test
    @DisplayName("Switching the feature off empties the public list and blocks booking")
    void featureSwitchOffHidesEverything() throws Exception {
        openSlot("10:00");

        adminPut("/api/admin/features", String.format(FEATURES, "false"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/appointments/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        book(ava(), at("10:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Appointment booking is not available right now."));
    }

    // ================================================================ booking

    @Test
    @DisplayName("A client books an open slot and it starts as REQUESTED")
    void clientBooksASlot() throws Exception {
        openSlot("10:00");

        book(ava(), at("10:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.startsAt").value(at("10:00").substring(0, 16)))
                .andExpect(jsonPath("$.endsAt").value(at("11:00").substring(0, 16)));
    }

    @Test
    @DisplayName("Two clients cannot book the same slot")
    void secondClientCannotBookTheSameSlot() throws Exception {
        openSlot("10:00");
        book(ava(), at("10:00")).andExpect(status().isCreated());

        book(ben(), at("10:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This appointment time is no longer available. Please select another time."));
    }

    @Test
    @DisplayName("A time that is not an open slot is refused")
    void timeThatIsNotASlotIsRefused() throws Exception {
        openSlot("10:00");

        book(ava(), at("10:30")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("An unreadable start time is a validation error")
    void unreadableStartTime() throws Exception {
        book(ava(), "tomorrow")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose an appointment time from the list."));
    }

    @Test
    @DisplayName("Booking needs a signed-in client")
    void bookingNeedsSignIn() throws Exception {
        mvc.perform(post("/api/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startsAt\":\"" + at("10:00") + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An administrator cannot use the client booking endpoint")
    void adminCannotBookAsClient() throws Exception {
        book(admin(), at("10:00")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A client with an unverified email cannot book")
    void unverifiedEmailCannotBook() throws Exception {
        openSlot("10:00");
        ClientUser unverified = newClient("Una Verified", "una@test.com");
        unverified.setEmailVerified(false);
        clients.saveAndFlush(unverified);

        book(clientToken("una@test.com"), at("10:00"))
                .andExpect(status().is4xxClientError());

        assertTrue(openSlotsJson().contains(slot("10:00")));
    }

    // ============================================================ my bookings

    @Test
    @DisplayName("A client sees only their own bookings")
    void clientSeesOnlyOwnBookings() throws Exception {
        bookOpenSlot("10:00");

        mvc.perform(get("/api/appointments/mine")
                .header(HttpHeaders.AUTHORIZATION, ava()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("REQUESTED"));

        mvc.perform(get("/api/appointments/mine")
                .header(HttpHeaders.AUTHORIZATION, ben()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("My bookings needs a signed-in client")
    void myBookingsNeedsSignIn() throws Exception {
        mvc.perform(get("/api/appointments/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/mine")
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isForbidden());
    }

    // ============================================================= admin list

    @Test
    @DisplayName("The administrator sees every booking with client details")
    void adminListsBookings() throws Exception {
        bookOpenSlot("10:00");

        mvc.perform(get("/api/admin/appointments")
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$[0].clientName").value("Ava Client"))
                .andExpect(jsonPath("$[0].clientEmail").value("ava@test.com"))
                .andExpect(jsonPath("$[0].canCancel").value(true))
                .andExpect(jsonPath("$[0].started").value(false));
    }

    @Test
    @DisplayName("The list can be filtered by status and searched by client")
    void adminFiltersAndSearches() throws Exception {
        bookOpenSlot("10:00");

        mvc.perform(get("/api/admin/appointments").param("status", "requested")
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(get("/api/admin/appointments").param("status", "CONFIRMED")
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/admin/appointments").param("search", "AVA")
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(get("/api/admin/appointments").param("search", "nobody-here")
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("A search with % or _ is matched literally")
    void searchSymbolsAreLiteral() throws Exception {
        bookOpenSlot("10:00");

        mvc.perform(get("/api/admin/appointments").param("search", "%")
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/admin/appointments").param("search", "_va")
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("An unknown status filter is a validation error")
    void unknownStatusFilter() throws Exception {
        mvc.perform(get("/api/admin/appointments").param("status", "PENDING")
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a valid appointment status."));
    }

    @Test
    @DisplayName("Only an administrator can list appointments")
    void adminListIsProtected() throws Exception {
        mvc.perform(get("/api/admin/appointments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/appointments")
                .header(HttpHeaders.AUTHORIZATION, ava()))
                .andExpect(status().isForbidden());
    }

    // ================================================================ confirm

    @Test
    @DisplayName("Accepting a request confirms it and warns that Calendar is not connected")
    void confirmWithoutCalendar() throws Exception {
        long id = bookOpenSlot("10:00");

        adminPost("/api/admin/appointments/" + id + "/confirm", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.appointment.calendarEventAdded").value(false))
                .andExpect(jsonPath("$.warning").value(
                        org.hamcrest.Matchers.containsString("Google Calendar is not connected")));
    }

    @Test
    @DisplayName("Accepting a request adds a Calendar event when Calendar is connected")
    void confirmWithCalendar() throws Exception {
        when(calendar.isConfigured()).thenReturn(true);
        when(calendar.createEvent(any(), any(), any(), any()))
                .thenReturn(new GoogleCalendarService.CalendarResult("event-1", null));
        long id = bookOpenSlot("10:00");

        adminPost("/api/admin/appointments/" + id + "/confirm", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.appointment.calendarEventAdded").value(true))
                .andExpect(jsonPath("$.warning").doesNotExist());
    }

    @Test
    @DisplayName("A Calendar problem keeps the appointment confirmed and shows a warning")
    void confirmWhenCalendarFails() throws Exception {
        when(calendar.isConfigured()).thenReturn(true);
        when(calendar.createEvent(any(), any(), any(), any()))
                .thenReturn(new GoogleCalendarService.CalendarResult(null, "Quota exceeded"));
        long id = bookOpenSlot("10:00");

        adminPost("/api/admin/appointments/" + id + "/confirm", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.appointment.calendarEventAdded").value(false))
                .andExpect(jsonPath("$.warning").value(
                        org.hamcrest.Matchers.containsString("Quota exceeded")));
    }

    @Test
    @DisplayName("A confirmed appointment can be added to Calendar later")
    void addToCalendarLater() throws Exception {
        long id = confirmed("10:00");

        when(calendar.isConfigured()).thenReturn(true);
        when(calendar.createEvent(any(), any(), any(), any()))
                .thenReturn(new GoogleCalendarService.CalendarResult("event-2", null));

        adminPost("/api/admin/appointments/" + id + "/calendar", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.calendarEventAdded").value(true));
    }

    @Test
    @DisplayName("A request that is not confirmed cannot be added to Calendar")
    void cannotAddRequestedToCalendar() throws Exception {
        long id = bookOpenSlot("10:00");

        adminPost("/api/admin/appointments/" + id + "/calendar", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Only a confirmed appointment can be added to the calendar."));
    }

    @Test
    @DisplayName("An appointment cannot be accepted twice")
    void cannotConfirmTwice() throws Exception {
        long id = confirmed("10:00");

        adminPost("/api/admin/appointments/" + id + "/confirm", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Only a requested appointment can be accepted."));
    }

    @Test
    @DisplayName("Confirming an appointment that does not exist is a 404")
    void confirmUnknownAppointment() throws Exception {
        adminPost("/api/admin/appointments/999999/confirm", null)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A client cannot accept an appointment")
    void clientCannotConfirm() throws Exception {
        long id = bookOpenSlot("10:00");

        mvc.perform(post("/api/admin/appointments/" + id + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, ava()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/appointments/" + id + "/confirm"))
                .andExpect(status().isUnauthorized());
    }

    // ================================================================ decline

    @Test
    @DisplayName("Declining needs a reason")
    void declineNeedsAReason() throws Exception {
        long id = bookOpenSlot("10:00");

        adminPost("/api/admin/appointments/" + id + "/decline", "{\"reason\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Enter a short reason for declining this request."));
    }

    @Test
    @DisplayName("A declined request shows its reason to the client and frees the slot")
    void declineFreesTheSlot() throws Exception {
        long id = bookOpenSlot("10:00");

        adminPost("/api/admin/appointments/" + id + "/decline",
                "{\"reason\":\"Fully booked that day\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.status").value("DECLINED"))
                .andExpect(jsonPath("$.appointment.statusReason").value("Fully booked that day"));

        mvc.perform(get("/api/appointments/mine")
                .header(HttpHeaders.AUTHORIZATION, ava()))
                .andExpect(jsonPath("$[0].status").value("DECLINED"))
                .andExpect(jsonPath("$[0].statusReason").value("Fully booked that day"));

        assertTrue(openSlotsJson().contains(slot("10:00")));
    }

    @Test
    @DisplayName("A confirmed appointment cannot be declined")
    void cannotDeclineConfirmed() throws Exception {
        long id = confirmed("10:00");

        adminPost("/api/admin/appointments/" + id + "/decline", "{\"reason\":\"Sorry\"}")
                .andExpect(status().isConflict());
    }

    // ================================================================= cancel

    @Test
    @DisplayName("Cancelling a confirmed appointment removes its Calendar event and frees the slot")
    void cancelRemovesCalendarEvent() throws Exception {
        when(calendar.isConfigured()).thenReturn(true);
        when(calendar.createEvent(any(), any(), any(), any()))
                .thenReturn(new GoogleCalendarService.CalendarResult("event-9", null));
        when(calendar.deleteEvent("event-9")).thenReturn(true);
        long id = confirmed("10:00");

        adminPost("/api/admin/appointments/" + id + "/cancel", "{\"reason\":\"Therapist is ill\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.status").value("CANCELLED"))
                .andExpect(jsonPath("$.appointment.statusReason").value("Therapist is ill"))
                .andExpect(jsonPath("$.appointment.calendarEventAdded").value(false))
                .andExpect(jsonPath("$.warning").doesNotExist());

        verify(calendar).deleteEvent(eq("event-9"));
        assertTrue(openSlotsJson().contains(slot("10:00")));
    }

    @Test
    @DisplayName("If the Calendar event cannot be removed the cancel still works and warns")
    void cancelWarnsWhenCalendarRemovalFails() throws Exception {
        when(calendar.isConfigured()).thenReturn(true);
        when(calendar.createEvent(any(), any(), any(), any()))
                .thenReturn(new GoogleCalendarService.CalendarResult("event-8", null));
        when(calendar.deleteEvent("event-8")).thenReturn(false);
        long id = confirmed("10:00");

        adminPost("/api/admin/appointments/" + id + "/cancel", "{\"reason\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.status").value("CANCELLED"))
                .andExpect(jsonPath("$.warning").value(
                        org.hamcrest.Matchers.containsString("could not be removed")));
    }

    @Test
    @DisplayName("A requested appointment can be cancelled without a reason")
    void cancelRequestedWithoutReason() throws Exception {
        long id = bookOpenSlot("10:00");

        adminPost("/api/admin/appointments/" + id + "/cancel", "{\"reason\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("A cancelled or declined appointment cannot be cancelled again")
    void cannotCancelTwice() throws Exception {
        long id = bookOpenSlot("10:00");
        adminPost("/api/admin/appointments/" + id + "/cancel", "{\"reason\":null}")
                .andExpect(status().isOk());

        adminPost("/api/admin/appointments/" + id + "/cancel", "{\"reason\":null}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("This appointment can no longer be cancelled."));
    }

    @Test
    @DisplayName("An appointment that has started cannot be cancelled")
    void cannotCancelStarted() throws Exception {
        long id = startedAppointment();

        adminPost("/api/admin/appointments/" + id + "/cancel", "{\"reason\":null}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("An appointment cannot be cancelled once it has started."));
    }

    // ================================================== meeting link, invitation

    @Test
    @DisplayName("The invitation saves the link and e-mails it")
    void invitationSavesAndSendsTheLink() throws Exception {
        long id = confirmed("10:00");

        adminPut("/api/admin/appointments/" + id + "/invitation",
                "{\"meetingLink\":\"" + LINK + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.meetingLink").value(LINK))
                .andExpect(jsonPath("$.appointment.invitationSentAt").isNotEmpty())
                .andExpect(jsonPath("$.warning").doesNotExist());

        verify(mail).sendInvitation(eq("Ava Client"), eq("ava@test.com"),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(LINK));

        mvc.perform(get("/api/appointments/mine")
                .header(HttpHeaders.AUTHORIZATION, ava()))
                .andExpect(jsonPath("$[0].meetingLink").value(LINK));
    }

    @Test
    @DisplayName("If the e-mail fails the link stays saved and a warning is shown")
    void invitationKeepsTheLinkWhenEmailFails() throws Exception {
        when(mail.sendInvitation(any(), any(), any(), any(), any())).thenReturn(false);
        long id = confirmed("10:00");

        adminPut("/api/admin/appointments/" + id + "/invitation",
                "{\"meetingLink\":\"" + LINK + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.meetingLink").value(LINK))
                .andExpect(jsonPath("$.appointment.invitationSentAt").doesNotExist())
                .andExpect(jsonPath("$.warning").value(
                        org.hamcrest.Matchers.containsString("email could not be sent")));
    }

    @Test
    @DisplayName("The invitation can be sent again")
    void invitationCanBeResent() throws Exception {
        when(mail.sendInvitation(any(), any(), any(), any(), any())).thenReturn(false);
        long id = confirmed("10:00");
        adminPut("/api/admin/appointments/" + id + "/invitation",
                "{\"meetingLink\":\"" + LINK + "\"}").andExpect(status().isOk());

        when(mail.sendInvitation(any(), any(), any(), any(), any())).thenReturn(true);

        adminPost("/api/admin/appointments/" + id + "/invitation/resend", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.invitationSentAt").isNotEmpty())
                .andExpect(jsonPath("$.warning").doesNotExist());
    }

    @Test
    @DisplayName("Resending needs a saved link")
    void resendNeedsALink() throws Exception {
        long id = confirmed("10:00");

        adminPost("/api/admin/appointments/" + id + "/invitation/resend", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Add a meeting link first."));
    }

    @Test
    @DisplayName("A meeting link must start with https://")
    void linkMustBeHttps() throws Exception {
        long id = confirmed("10:00");

        for (String bad : new String[] {"http://meet.example.org/a", "javascript:alert(1)", ""}) {
            adminPut("/api/admin/appointments/" + id + "/invitation",
                    "{\"meetingLink\":\"" + bad + "\"}")
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("A link can only be added to a confirmed appointment")
    void linkNeedsAConfirmedAppointment() throws Exception {
        long id = bookOpenSlot("10:00");

        adminPut("/api/admin/appointments/" + id + "/invitation",
                "{\"meetingLink\":\"" + LINK + "\"}")
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A client cannot send an invitation")
    void clientCannotSendInvitation() throws Exception {
        long id = confirmed("10:00");

        mvc.perform(put("/api/admin/appointments/" + id + "/invitation")
                .header(HttpHeaders.AUTHORIZATION, ava())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"meetingLink\":\"" + LINK + "\"}"))
                .andExpect(status().isForbidden());
    }

    // ================================================================ feedback

    @Test
    @DisplayName("Feedback is shown to the client but the private note never is")
    void feedbackVisibleNoteHidden() throws Exception {
        long id = startedAppointment();

        adminPut("/api/admin/appointments/" + id + "/feedback",
                "{\"feedback\":\"Great first session\",\"privateNotes\":\"Secret worry about school\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").value("Great first session"))
                .andExpect(jsonPath("$.privateNotes").value("Secret worry about school"));

        String clientView = mvc.perform(get("/api/appointments/mine")
                .header(HttpHeaders.AUTHORIZATION, ava()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].feedback").value("Great first session"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(clientView.contains("Secret worry"), clientView);
        assertFalse(clientView.contains("privateNotes"), clientView);
    }

    @Test
    @DisplayName("Feedback and the private note can be saved one at a time")
    void feedbackAndNoteSavedSeparately() throws Exception {
        long id = startedAppointment();

        adminPut("/api/admin/appointments/" + id + "/feedback", "{\"feedback\":\"First words\"}")
                .andExpect(status().isOk());

        adminPut("/api/admin/appointments/" + id + "/feedback", "{\"privateNotes\":\"A note\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").value("First words"))
                .andExpect(jsonPath("$.privateNotes").value("A note"));
    }

    @Test
    @DisplayName("Feedback cannot be written before the appointment starts")
    void feedbackNeedsAStartedAppointment() throws Exception {
        long id = confirmed("10:00");

        adminPut("/api/admin/appointments/" + id + "/feedback", "{\"feedback\":\"Too early\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Feedback can be written once a confirmed appointment has started."));
    }

    @Test
    @DisplayName("Empty and too long feedback are refused")
    void feedbackValidation() throws Exception {
        long id = startedAppointment();

        adminPut("/api/admin/appointments/" + id + "/feedback", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Write some feedback or a private note before saving."));

        adminPut("/api/admin/appointments/" + id + "/feedback", "{\"feedback\":\"   \"}")
                .andExpect(status().isBadRequest());

        adminPut("/api/admin/appointments/" + id + "/feedback",
                "{\"feedback\":\"" + "x".repeat(4001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please keep this under 4000 characters."));
    }

    @Test
    @DisplayName("A client cannot write feedback")
    void clientCannotWriteFeedback() throws Exception {
        long id = startedAppointment();

        mvc.perform(put("/api/admin/appointments/" + id + "/feedback")
                .header(HttpHeaders.AUTHORIZATION, ava())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"Hi\"}"))
                .andExpect(status().isForbidden());
    }

    // ================================================== availability rules

    @Test
    @DisplayName("The administrator can add, list, change and remove a one-date slot")
    void ruleLifecycle() throws Exception {
        long id = openSlot("10:00");

        mvc.perform(get("/api/admin/appointments/availability")
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")].startTime").value("10:00"))
                .andExpect(jsonPath("$[?(@.id==" + id + ")].endTime").value("11:00"));

        adminPut("/api/admin/appointments/availability/" + id,
                "{\"date\":\"" + tomorrow + "\",\"startTime\":\"14:00\",\"slotMinutes\":90}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("14:00"))
                .andExpect(jsonPath("$.endTime").value("15:30"))
                .andExpect(jsonPath("$.slotMinutes").value(90));

        mvc.perform(delete("/api/admin/appointments/availability/" + id)
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNoContent());

        assertFalse(openSlotsJson().contains(slot("14:00")));
    }

    @Test
    @DisplayName("A weekly slot repeats and is offered inside the booking window")
    void weeklySlotIsOffered() throws Exception {
        int weekday = tomorrow.getDayOfWeek().getValue();

        adminPost("/api/admin/appointments/availability",
                "{\"weekday\":" + weekday + ",\"startTime\":\"16:00\",\"slotMinutes\":60}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weekday").value(weekday));

        assertTrue(openSlotsJson().contains(slot("16:00")));
    }

    @Test
    @DisplayName("Overlapping slots on the same day are refused")
    void overlappingSlotsAreRefused() throws Exception {
        openSlot("10:00");

        addRule(tomorrow, "10:30", 60)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("This time overlaps another slot on the same day."));
    }

    @Test
    @DisplayName("Bad slot input is refused with a clear message")
    void badSlotInput() throws Exception {
        addRule(tomorrow, "10am", 60).andExpect(status().isBadRequest());
        addRule(tomorrow, "10:00", 20).andExpect(status().isBadRequest());
        addRule(tomorrow, "10:00", 200).andExpect(status().isBadRequest());
        addRule(clock.now().toLocalDate().minusDays(1), "10:00", 60)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Choose a time that has not passed yet."));

        adminPost("/api/admin/appointments/availability",
                "{\"startTime\":\"10:00\",\"slotMinutes\":60}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Choose the day of the week or a date."));
    }

    @Test
    @DisplayName("Changing or removing a slot that does not exist is a 404")
    void unknownRule() throws Exception {
        adminPut("/api/admin/appointments/availability/999999",
                "{\"date\":\"" + tomorrow + "\",\"startTime\":\"10:00\",\"slotMinutes\":60}")
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/admin/appointments/availability/999999")
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Only an administrator can manage availability")
    void availabilityIsProtected() throws Exception {
        mvc.perform(get("/api/admin/appointments/availability"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/admin/appointments/availability")
                .header(HttpHeaders.AUTHORIZATION, ava())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + tomorrow + "\",\"startTime\":\"10:00\",\"slotMinutes\":60}"))
                .andExpect(status().isForbidden());
    }

    // ================================================================ settings

    @Test
    @DisplayName("The hide-before-start setting can be read and changed")
    void settingsRoundTrip() throws Exception {
        adminPut("/api/admin/appointments/settings", "{\"hideHoursBeforeStart\":24}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hideHoursBeforeStart").value(24));

        mvc.perform(get("/api/admin/appointments/settings")
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hideHoursBeforeStart").value(24))
                .andExpect(jsonPath("$.timeZone").value("America/Toronto"));
    }

    @Test
    @DisplayName("A slot that is closer than the hide setting is not offered or bookable")
    void hideSettingRemovesNearSlots() throws Exception {
        openSlot("10:00");
        assertTrue(openSlotsJson().contains(slot("10:00")));

        adminPut("/api/admin/appointments/settings", "{\"hideHoursBeforeStart\":168}")
                .andExpect(status().isOk());

        assertFalse(openSlotsJson().contains(slot("10:00")));
        book(ava(), at("10:00")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("The hide setting only accepts 0 to 168 hours")
    void settingsValidation() throws Exception {
        adminPut("/api/admin/appointments/settings", "{\"hideHoursBeforeStart\":169}")
                .andExpect(status().isBadRequest());
        adminPut("/api/admin/appointments/settings", "{\"hideHoursBeforeStart\":-1}")
                .andExpect(status().isBadRequest());
        adminPut("/api/admin/appointments/settings", "{\"hideHoursBeforeStart\":0}")
                .andExpect(status().isOk());
        adminPut("/api/admin/appointments/settings", "{\"hideHoursBeforeStart\":168}")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Only an administrator can change the settings")
    void settingsAreProtected() throws Exception {
        mvc.perform(put("/api/admin/appointments/settings")
                .header(HttpHeaders.AUTHORIZATION, ava())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hideHoursBeforeStart\":5}"))
                .andExpect(status().isForbidden());
    }
}