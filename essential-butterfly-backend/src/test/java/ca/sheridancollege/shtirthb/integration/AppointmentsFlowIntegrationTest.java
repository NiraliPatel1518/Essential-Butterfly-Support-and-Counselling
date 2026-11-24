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

    /*
     * Checks: A visitor with no login can see the open appointment slots.
     * How:    The admin creates a 10:00 slot. Then we ask the public list as a visitor. The list must
     *   contain 10:00, and 11:00, which is the end time of the same one-hour slot.
     */
    @Test
    @DisplayName("Visitors can see open slots without signing in")
    void visitorsSeeOpenSlots() throws Exception {
        openSlot("10:00");

        String json = openSlotsJson();

        assertTrue(json.contains(slot("10:00")), json);
        assertTrue(json.contains(slot("11:00")), json);
    }

    /*
     * Checks: The public slot list never shows who booked.
     * How:    A client books the 10:00 slot. We read the public list again and check that neither her
     *   name nor her email is in it. This is a privacy check.
     */
    @Test
    @DisplayName("The open slot list never shows who booked")
    void slotListHidesClientDetails() throws Exception {
        openSlot("10:00");
        book(ava(), at("10:00")).andExpect(status().isCreated());

        String json = openSlotsJson();

        assertFalse(json.contains("Ava"), json);
        assertFalse(json.contains("ava@test.com"), json);
    }

    /*
     * Checks: A booked slot is removed from the public list.
     * How:    We check the slot is in the list, book it as a client, and check it is gone. This stops
     *   two people booking the same time.
     */
    @Test
    @DisplayName("A booked slot disappears from the public list")
    void bookedSlotDisappears() throws Exception {
        openSlot("10:00");
        assertTrue(openSlotsJson().contains(slot("10:00")));

        book(ava(), at("10:00")).andExpect(status().isCreated());

        assertFalse(openSlotsJson().contains(slot("10:00")));
    }

    /*
     * Checks: A slot that is too far in the future is not offered.
     * How:    The admin adds a slot 60 days from now. It is outside the booking window, so the public
     *   list must not contain that date.
     */
    @Test
    @DisplayName("A slot outside the booking window is not offered")
    void farAwaySlotIsNotOffered() throws Exception {
        LocalDate far = tomorrow.plusDays(60);
        addRule(far, "10:00", 60).andExpect(status().isCreated());

        assertFalse(openSlotsJson().contains(far.toString()));
    }

    /*
     * Checks: Turning the appointment feature off hides all slots and blocks booking.
     * How:    The admin sends the feature list with APPOINTMENTS set to false. The public list must
     *   then be empty, and a booking must be refused with 409 and the message that booking is
     *   not available.
     */
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

    /*
     * Checks: A client can book an open slot, and it starts as REQUESTED.
     * How:    We book the 10:00 slot as a signed-in client. The answer must be 201 Created, with
     *   status REQUESTED and the correct start and end times.
     */
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

    /*
     * Checks: Two clients cannot book the same slot (no double booking).
     * How:    Ava books 10:00. Ben tries the same time and must get 409 Conflict with the message that
     *   the time is no longer available.
     */
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

    /*
     * Checks: A time that does not match an open slot is refused.
     * How:    Only 10:00 is open. Booking 10:30 must be refused with 409 Conflict.
     */
    @Test
    @DisplayName("A time that is not an open slot is refused")
    void timeThatIsNotASlotIsRefused() throws Exception {
        openSlot("10:00");

        book(ava(), at("10:30")).andExpect(status().isConflict());
    }

    /*
     * Checks: A start time that cannot be read is a validation error.
     * How:    We send the text "tomorrow" instead of a date and time. The server must answer 400 with
     *   the message to choose a time from the list.
     */
    @Test
    @DisplayName("An unreadable start time is a validation error")
    void unreadableStartTime() throws Exception {
        book(ava(), "tomorrow")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose an appointment time from the list."));
    }

    /*
     * Checks: Booking needs a signed-in client.
     * How:    We send a booking request with no login token. The server must answer 401 Unauthorized.
     */
    @Test
    @DisplayName("Booking needs a signed-in client")
    void bookingNeedsSignIn() throws Exception {
        mvc.perform(post("/api/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startsAt\":\"" + at("10:00") + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    /*
     * Checks: An administrator cannot use the client booking endpoint.
     * How:    We book with an admin token. The server must answer 403 Forbidden, because this action
     *   is only for clients.
     */
    @Test
    @DisplayName("An administrator cannot use the client booking endpoint")
    void adminCannotBookAsClient() throws Exception {
        book(admin(), at("10:00")).andExpect(status().isForbidden());
    }

    /*
     * Checks: A client whose email is not verified cannot book.
     * How:    We create a client with a not-verified email and try to book. The server must refuse
     *   with a 4xx error, and the slot must still be open afterwards.
     */
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

    /*
     * Checks: A client sees only their own bookings.
     * How:    Ava books a slot. Her My Bookings list has 1 item with status REQUESTED. Ben's list for
     *   the same call is empty.
     */
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

    /*
     * Checks: My Bookings needs a signed-in client.
     * How:    With no token the server answers 401. With an admin token it answers 403.
     */
    @Test
    @DisplayName("My bookings needs a signed-in client")
    void myBookingsNeedsSignIn() throws Exception {
        mvc.perform(get("/api/appointments/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/mine")
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isForbidden());
    }

    // ============================================================= admin list

    /*
     * Checks: The administrator sees every booking with the client details.
     * How:    After Ava books, the admin list shows her name and email, status REQUESTED, canCancel
     *   true and started false.
     */
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

    /*
     * Checks: The admin list can be filtered by status and searched by client.
     * How:    We filter by REQUESTED (1 result) and CONFIRMED (0 results). We search "AVA" (1 result,
     *   capital letters do not matter) and "nobody-here" (0 results).
     */
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

    /*
     * Checks: The symbols % and _ in a search are matched as normal characters.
     * How:    In database searches these symbols can act as wildcards. Searching "%" or "_va" must
     *   return 0 results, so the search cannot be abused.
     */
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

    /*
     * Checks: An unknown status filter is a validation error.
     * How:    We filter by PENDING, which is not a real status. The server must answer 400 with the
     *   message to choose a valid status.
     */
    @Test
    @DisplayName("An unknown status filter is a validation error")
    void unknownStatusFilter() throws Exception {
        mvc.perform(get("/api/admin/appointments").param("status", "PENDING")
                .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a valid appointment status."));
    }

    /*
     * Checks: Only an administrator can list appointments.
     * How:    No token gives 401. A client token gives 403.
     */
    @Test
    @DisplayName("Only an administrator can list appointments")
    void adminListIsProtected() throws Exception {
        mvc.perform(get("/api/admin/appointments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/appointments")
                .header(HttpHeaders.AUTHORIZATION, ava()))
                .andExpect(status().isForbidden());
    }

    // ================================================================ confirm

    /*
     * Checks: Accepting a request confirms it and warns that Google Calendar is not connected.
     * How:    The fake calendar says it is not configured. The admin confirms. Status becomes
     *   CONFIRMED, calendarEventAdded is false, and a warning is shown.
     */
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

    /*
     * Checks: Accepting a request adds a Calendar event when Calendar is connected.
     * How:    The fake calendar says it is configured and returns an event id. After the admin
     *   confirms, calendarEventAdded is true and there is no warning.
     */
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

    /*
     * Checks: A Calendar problem does not stop the confirmation.
     * How:    The fake calendar returns the error "Quota exceeded". The appointment is still
     *   CONFIRMED, calendarEventAdded is false, and the warning shows the problem.
     */
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

    /*
     * Checks: A confirmed appointment can be added to Calendar later.
     * How:    We confirm first (Calendar not connected). Then the fake calendar is switched to
     *   connected, and the admin calls the calendar endpoint. calendarEventAdded becomes true.
     */
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

    /*
     * Checks: A request that is not confirmed cannot be added to Calendar.
     * How:    We call the calendar endpoint on a REQUESTED appointment. The server must answer 409
     *   with the message that only a confirmed appointment can be added.
     */
    @Test
    @DisplayName("A request that is not confirmed cannot be added to Calendar")
    void cannotAddRequestedToCalendar() throws Exception {
        long id = bookOpenSlot("10:00");

        adminPost("/api/admin/appointments/" + id + "/calendar", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Only a confirmed appointment can be added to the calendar."));
    }

    /*
     * Checks: An appointment cannot be accepted twice.
     * How:    We confirm an appointment that is already CONFIRMED. The server must answer 409 with the
     *   message that only a requested appointment can be accepted.
     */
    @Test
    @DisplayName("An appointment cannot be accepted twice")
    void cannotConfirmTwice() throws Exception {
        long id = confirmed("10:00");

        adminPost("/api/admin/appointments/" + id + "/confirm", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Only a requested appointment can be accepted."));
    }

    /*
     * Checks: Confirming an appointment that does not exist gives 404.
     * How:    We confirm the id 999999, which does not exist. The server must answer 404 Not Found.
     */
    @Test
    @DisplayName("Confirming an appointment that does not exist is a 404")
    void confirmUnknownAppointment() throws Exception {
        adminPost("/api/admin/appointments/999999/confirm", null)
                .andExpect(status().isNotFound());
    }

    /*
     * Checks: A client cannot accept an appointment.
     * How:    A client token gives 403 Forbidden, and no token gives 401 Unauthorized.
     */
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

    /*
     * Checks: Declining needs a reason.
     * How:    We send a reason made only of spaces. The server must answer 400 with the message asking
     *   for a short reason.
     */
    @Test
    @DisplayName("Declining needs a reason")
    void declineNeedsAReason() throws Exception {
        long id = bookOpenSlot("10:00");

        adminPost("/api/admin/appointments/" + id + "/decline", "{\"reason\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Enter a short reason for declining this request."));
    }

    /*
     * Checks: A declined request shows its reason to the client and frees the slot.
     * How:    The admin declines with a reason. The status becomes DECLINED. The client sees the same
     *   reason in My Bookings, and the slot is back in the public list.
     */
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

    /*
     * Checks: A confirmed appointment cannot be declined.
     * How:    We try to decline a CONFIRMED appointment. The server must answer 409 Conflict.
     */
    @Test
    @DisplayName("A confirmed appointment cannot be declined")
    void cannotDeclineConfirmed() throws Exception {
        long id = confirmed("10:00");

        adminPost("/api/admin/appointments/" + id + "/decline", "{\"reason\":\"Sorry\"}")
                .andExpect(status().isConflict());
    }

    // ================================================================= cancel

    /*
     * Checks: Cancelling a confirmed appointment removes its Calendar event and frees the slot.
     * How:    The fake calendar creates event-9 and agrees to delete it. After the cancel, the status
     *   is CANCELLED with the reason, there is no warning, deleteEvent was called once, and the
     *   slot is open again.
     */
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

    /*
     * Checks: If the Calendar event cannot be removed, the cancel still works and shows a warning.
     * How:    The fake calendar refuses to delete the event. The appointment is still CANCELLED, and
     *   the warning says the event could not be removed.
     */
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

    /*
     * Checks: A requested appointment can be cancelled without a reason.
     * How:    We cancel with the reason set to null. The status becomes CANCELLED.
     */
    @Test
    @DisplayName("A requested appointment can be cancelled without a reason")
    void cancelRequestedWithoutReason() throws Exception {
        long id = bookOpenSlot("10:00");

        adminPost("/api/admin/appointments/" + id + "/cancel", "{\"reason\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.status").value("CANCELLED"));
    }

    /*
     * Checks: An appointment that is already cancelled cannot be cancelled again.
     * How:    We cancel it once (OK), then again. The second call must answer 409 with the message
     *   that it can no longer be cancelled.
     */
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

    /*
     * Checks: An appointment that has already started cannot be cancelled.
     * How:    startedAppointment() writes a confirmed appointment that began an hour ago straight into
     *   the database. The cancel must answer 409 with the message that it has started.
     */
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

    /*
     * Checks: The invitation saves the meeting link and emails it to the client.
     * How:    The admin saves a link on a confirmed appointment. The link and invitationSentAt are
     *   saved, there is no warning, the fake mail sender was called with the right name, email
     *   and link, and the client can see the link in My Bookings.
     */
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

    /*
     * Checks: If the email fails, the link stays saved and a warning is shown.
     * How:    The fake mail sender returns false. The link is saved, invitationSentAt stays empty, and
     *   the warning says the email could not be sent.
     */
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

    /*
     * Checks: The invitation can be sent again.
     * How:    The first email fails. Then the fake mail sender is changed to succeed, and the admin
     *   calls resend. invitationSentAt is now filled and there is no warning.
     */
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

    /*
     * Checks: Resending needs a saved link.
     * How:    We call resend with no link saved. The server must answer 409 with the message to add a
     *   meeting link first.
     */
    @Test
    @DisplayName("Resending needs a saved link")
    void resendNeedsALink() throws Exception {
        long id = confirmed("10:00");

        adminPost("/api/admin/appointments/" + id + "/invitation/resend", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Add a meeting link first."));
    }

    /*
     * Checks: A meeting link must start with https://
     * How:    We try three bad links in a loop: an http link, a javascript link, and an empty text.
     *   Each must give 400. This blocks unsafe links.
     */
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

    /*
     * Checks: A link can only be added to a confirmed appointment.
     * How:    We try to save a link on a REQUESTED appointment. The server must answer 409 Conflict.
     */
    @Test
    @DisplayName("A link can only be added to a confirmed appointment")
    void linkNeedsAConfirmedAppointment() throws Exception {
        long id = bookOpenSlot("10:00");

        adminPut("/api/admin/appointments/" + id + "/invitation",
                "{\"meetingLink\":\"" + LINK + "\"}")
                .andExpect(status().isConflict());
    }

    /*
     * Checks: A client cannot send an invitation.
     * How:    A client token on the invitation endpoint must give 403 Forbidden.
     */
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

    /*
     * Checks: Feedback is shown to the client, but the private note never is.
     * How:    The admin saves feedback and a private note on a started appointment. The client's My
     *   Bookings shows the feedback, and the text of the private note and the word privateNotes
     *   are not in the answer.
     */
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

    /*
     * Checks: Feedback and the private note can be saved one at a time.
     * How:    We save only feedback, then only the note. After the second save, both values are
     *   present, so one save does not erase the other.
     */
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

    /*
     * Checks: Feedback cannot be written before the appointment starts.
     * How:    We write feedback on a confirmed appointment tomorrow. The server must answer 409 with
     *   the message that feedback can be written once it has started.
     */
    @Test
    @DisplayName("Feedback cannot be written before the appointment starts")
    void feedbackNeedsAStartedAppointment() throws Exception {
        long id = confirmed("10:00");

        adminPut("/api/admin/appointments/" + id + "/feedback", "{\"feedback\":\"Too early\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Feedback can be written once a confirmed appointment has started."));
    }

    /*
     * Checks: Empty feedback and feedback that is too long are refused.
     * How:    An empty request and a request with only spaces both give 400. A text of 4001 characters
     *   gives 400 with the 4000-character limit message.
     */
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

    /*
     * Checks: A client cannot write feedback.
     * How:    A client token on the feedback endpoint must give 403 Forbidden.
     */
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

    /*
     * Checks: The admin can add, list, change and remove a one-date slot.
     * How:    We add a slot at 10:00 and see it in the admin list with end time 11:00. We change it to
     *   14:00 for 90 minutes (end time 15:30). We delete it (204), and 14:00 is no longer in the
     *   public list.
     */
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

    /*
     * Checks: A weekly slot repeats and is offered inside the booking window.
     * How:    The admin adds a weekly rule for tomorrow's weekday at 16:00. The public list must
     *   contain tomorrow at 16:00.
     */
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

    /*
     * Checks: Two slots that overlap on the same day are refused.
     * How:    A slot at 10:00 for 60 minutes exists. Adding 10:30 on the same day must give 409 with
     *   the overlap message.
     */
    @Test
    @DisplayName("Overlapping slots on the same day are refused")
    void overlappingSlotsAreRefused() throws Exception {
        openSlot("10:00");

        addRule(tomorrow, "10:30", 60)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("This time overlaps another slot on the same day."));
    }

    /*
     * Checks: Bad slot input is refused with a clear message.
     * How:    We try: a time written as "10am", a 20-minute slot, a 200-minute slot, a date in the
     *   past, and a rule with no weekday or date. Each must give 400, and the past date and
     *   missing day have their own messages.
     */
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

    /*
     * Checks: Changing or removing a slot that does not exist gives 404.
     * How:    We update and delete the id 999999. Both must answer 404 Not Found.
     */
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

    /*
     * Checks: Only an administrator can manage availability.
     * How:    No token gives 401 when listing. A client token gives 403 when adding a slot.
     */
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

    /*
     * Checks: The hide-before-start setting can be read and changed.
     * How:    The admin sets it to 24 hours. Reading it back shows 24 and the time zone
     *   America/Toronto.
     */
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

    /*
     * Checks: A slot closer than the hide setting is not offered and cannot be booked.
     * How:    The slot is first in the list. The admin sets the hide time to 168 hours (7 days). Now
     *   the slot is gone from the list, and booking it gives 409.
     */
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

    /*
     * Checks: The hide setting only accepts 0 to 168 hours.
     * How:    169 and -1 give 400. The limits 0 and 168 are accepted with 200.
     */
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

    /*
     * Checks: Only an administrator can change the settings.
     * How:    A client token on the settings endpoint must give 403 Forbidden.
     */
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