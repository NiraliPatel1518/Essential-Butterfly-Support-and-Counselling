package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ca.sheridancollege.shtirthb.config.AppProperties;

/*
 * Google is replaced by a small web server that runs on this computer for the
 * length of each test, so no real Google account is ever contacted.
 */
class GoogleCalendarServiceTest {

    private record Seen(String method, String path, String authorization, String body) {}

    private HttpServer server;
    private final List<Seen> seen = new ArrayList<>();

    private int tokenStatus = 200;
    private String tokenBody = "{\"access_token\":\"token-123\",\"expires_in\":3600}";
    private int eventStatus = 200;
    private String eventBody = "{\"id\":\"event-1\"}";
    private int deleteStatus = 204;

    private AppProperties properties;
    private AppointmentClock clock;

    private static final LocalDateTime START = LocalDateTime.parse("2026-11-16T10:00:00");
    private static final LocalDateTime END = LocalDateTime.parse("2026-11-16T11:00:00");

    @BeforeEach
    void startFakeGoogle() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();

        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        properties = new AppProperties();
        properties.getGoogleCalendar().setClientId("client-id");
        properties.getGoogleCalendar().setClientSecret("client-secret");
        properties.getGoogleCalendar().setRefreshToken("refresh-token");
        properties.getGoogleCalendar().setCalendarId("primary");
        properties.getGoogleCalendar().setTokenUrl(base + "/token");
        properties.getGoogleCalendar().setApiBaseUrl(base + "/calendar/");
        properties.getAppointments().setTimeZone("America/Toronto");

        clock = new AppointmentClock(properties);
    }

    @AfterEach
    void stopFakeGoogle() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().getRawPath();
        seen.add(new Seen(exchange.getRequestMethod(), path,
                exchange.getRequestHeaders().getFirst("Authorization"), body));

        int status;
        String reply;

        if (path.equals("/token")) {
            status = tokenStatus;
            reply = tokenBody;
        } else if (exchange.getRequestMethod().equals("DELETE")) {
            status = deleteStatus;
            reply = "";
        } else {
            status = eventStatus;
            reply = eventBody;
        }

        byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private GoogleCalendarService service() {
        return new GoogleCalendarService(properties, clock);
    }

    // ---------------------------------------------------------- configured

    /*
     * Checks: The service is configured when all three secrets exist.
     * How:    A small fake Google web server runs in the test (no real Google). With client id, client
     *   secret and refresh token set, isConfigured() is true.
     */
    @Test void isConfiguredWhenAllThreeSecretsArePresent() {
        assertTrue(service().isConfigured());
    }

    /*
     * Checks: The service is not configured when a secret is missing.
     * How:    isConfigured() is false.
     */
    @Test void isNotConfiguredWhenASecretIsMissing() {
        properties.getGoogleCalendar().setRefreshToken("");

        assertFalse(service().isConfigured());
    }

    // --------------------------------------------------------- createEvent

    /*
     * Checks: An event is created and its id is returned.
     * How:    The fake Google returns event-1. The result says added, with that id and no problem.
     */
    @Test void createsAnEventAndReturnsItsId() {
        var result = service().createEvent("Appointment: Ann", "Client: Ann", START, END);

        assertTrue(result.added());
        assertEquals("event-1", result.eventId());
        assertNull(result.problem());
    }

    /*
     * Checks: The service signs in with the refresh token and then creates the event.
     * How:    The fake Google saw 2 requests. First a POST to /token with grant_type, refresh_token
     *   and client_id. Then a POST to the events URL with the bearer token.
     */
    @Test void signsInWithTheRefreshTokenThenCreatesTheEvent() {
        service().createEvent("Appointment: Ann", "Client: Ann", START, END);

        assertEquals(2, seen.size());
        Seen token = seen.get(0);
        assertEquals("POST", token.method());
        assertEquals("/token", token.path());
        assertTrue(token.body().contains("grant_type=refresh_token"));
        assertTrue(token.body().contains("refresh_token=refresh-token"));
        assertTrue(token.body().contains("client_id=client-id"));

        Seen event = seen.get(1);
        assertEquals("POST", event.method());
        assertEquals("/calendar/calendars/primary/events", event.path());
        assertEquals("Bearer token-123", event.authorization());
    }

    /*
     * Checks: The event has the title, the times and the clinic time zone.
     * How:    The request body contains the summary, the description, and start and end times with
     *   America/Toronto.
     */
    @Test void theEventCarriesTheTitleTheTimesAndTheClinicTimeZone() {
        service().createEvent("Appointment: Ann", "Client: Ann", START, END);

        String body = seen.get(1).body();
        assertTrue(body.contains("\"summary\":\"Appointment: Ann\""));
        assertTrue(body.contains("\"description\":\"Client: Ann\""));
        assertTrue(body.contains("\"start\":{\"dateTime\":\"2026-11-16T10:00:00\",\"timeZone\":\"America/Toronto\"}"));
        assertTrue(body.contains("\"end\":{\"dateTime\":\"2026-11-16T11:00:00\",\"timeZone\":\"America/Toronto\"}"));
    }

    /*
     * Checks: Times with zero seconds still include the seconds.
     * How:    The body contains 09:30:00 and 10:00:00.
     */
    @Test void timesWithZeroSecondsStillSendTheSeconds() {
        service().createEvent("T", "D", LocalDateTime.parse("2026-11-16T09:30"), LocalDateTime.parse("2026-11-16T10:00"));

        String body = seen.get(1).body();
        assertTrue(body.contains("2026-11-16T09:30:00"));
        assertTrue(body.contains("2026-11-16T10:00:00"));
    }

    /*
     * Checks: The event asks for a popup and an email reminder.
     * How:    The body has useDefault false, a popup at 30 minutes and an email at 60 minutes.
     */
    @Test void theEventAsksForAPopupAndAnEmailReminder() {
        service().createEvent("T", "D", START, END);

        String body = seen.get(1).body();
        assertTrue(body.contains("\"useDefault\":false"));
        assertTrue(body.contains("{\"method\":\"popup\",\"minutes\":30}"));
        assertTrue(body.contains("{\"method\":\"email\",\"minutes\":60}"));
    }

    /*
     * Checks: Special characters in the text are escaped.
     * How:    Quotes, new lines and backslashes are escaped in the JSON body.
     */
    @Test void specialCharactersInTheTextAreEscaped() {
        service().createEvent("Say \"hi\"", "Line one\nLine two\\ end", START, END);

        String body = seen.get(1).body();
        assertTrue(body.contains("\"summary\":\"Say \\\"hi\\\"\""));
        assertTrue(body.contains("Line one\\nLine two\\\\ end"));
    }

    /*
     * Checks: The configured calendar is used and its name is URL-encoded.
     * How:    A calendar id with @ becomes %40 in the URL.
     */
    @Test void usesTheConfiguredCalendarAndEncodesItsName() {
        properties.getGoogleCalendar().setCalendarId("clinic@example.org");

        service().createEvent("T", "D", START, END);

        assertEquals("/calendar/calendars/clinic%40example.org/events", seen.get(1).path());
    }

    /*
     * Checks: Google is not contacted when not configured.
     * How:    The result says "Google Calendar is not connected." and the fake Google saw no requests.
     */
    @Test void doesNotContactGoogleWhenNotConfigured() {
        properties.getGoogleCalendar().setClientId("");

        var result = service().createEvent("T", "D", START, END);

        assertFalse(result.added());
        assertEquals("Google Calendar is not connected.", result.problem());
        assertTrue(seen.isEmpty());
    }

    /*
     * Checks: A Google error is explained with Google's own message.
     * How:    A 403 with a message gives "HTTP 403: Calendar API has not been used."
     */
    @Test void explainsAGoogleErrorUsingGooglesOwnWords() {
        eventStatus = 403;
        eventBody = "{\"error\":{\"code\":403,\"message\":\"Calendar API has not been used.\"}}";

        var result = service().createEvent("T", "D", START, END);

        assertFalse(result.added());
        assertNull(result.eventId());
        assertEquals("HTTP 403: Calendar API has not been used.", result.problem());
    }

    /*
     * Checks: The error code is used when Google gives no message.
     * How:    The result is "HTTP 400: invalid_request".
     */
    @Test void usesTheErrorCodeWhenGoogleGivesNoMessage() {
        eventStatus = 400;
        eventBody = "{\"error\":\"invalid_request\"}";

        var result = service().createEvent("T", "D", START, END);

        assertEquals("HTTP 400: invalid_request", result.problem());
    }

    /*
     * Checks: "no details" is shown when Google explains nothing.
     * How:    The result is "HTTP 500: no details".
     */
    @Test void saysNoDetailsWhenGoogleExplainsNothing() {
        eventStatus = 500;
        eventBody = "oops";

        var result = service().createEvent("T", "D", START, END);

        assertEquals("HTTP 500: no details", result.problem());
    }

    /*
     * Checks: It reports when Google returns no event id.
     * How:    The result is "Google did not return an event id."
     */
    @Test void reportsWhenGoogleReturnsNoEventId() {
        eventBody = "{\"status\":\"confirmed\"}";

        var result = service().createEvent("T", "D", START, END);

        assertFalse(result.added());
        assertEquals("Google did not return an event id.", result.problem());
    }

    /*
     * Checks: It reports when Google refuses the sign-in.
     * How:    The problem says "Google refused the sign-in. HTTP 400: Token has been revoked." and
     *   only 1 request was made.
     */
    @Test void reportsWhenGoogleRefusesTheSignIn() {
        tokenStatus = 400;
        tokenBody = "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been revoked.\"}";

        var result = service().createEvent("T", "D", START, END);

        assertFalse(result.added());
        assertEquals("Google refused the sign-in. HTTP 400: Token has been revoked.", result.problem());
        assertEquals(1, seen.size());
    }

    /*
     * Checks: It reports when the sign-in answer has no access token.
     * How:    The problem starts with "Google refused the sign-in."
     */
    @Test void reportsWhenTheSignInAnswerHasNoAccessToken() {
        tokenBody = "{\"expires_in\":3600}";

        var result = service().createEvent("T", "D", START, END);

        assertFalse(result.added());
        assertTrue(result.problem().startsWith("Google refused the sign-in."));
    }

    /*
     * Checks: It reports when Google cannot be reached.
     * How:    The result is not added and has no event id.
     */
    @Test void reportsWhenGoogleCannotBeReached() {
        server.stop(0);

        var result = service().createEvent("T", "D", START, END);

        assertFalse(result.added());
        assertNull(result.eventId());
    }

    /*
     * Checks: The problem text is readable when Google cannot be reached.
     * How:    The problem is not null, not blank, and does not contain the word "null".
     */
    @Test void explainsInWordsWhenGoogleCannotBeReached() {
        server.stop(0);

        var result = service().createEvent("T", "D", START, END);

        assertNotNull(result.problem());
        assertFalse(result.problem().isBlank());
        assertFalse(result.problem().contains("null"));
    }

    /*
     * Checks: added() is true only when there is an event id.
     * How:    A result with an id is added. A result with a problem and no id is not.
     */
    @Test void theResultSaysAddedOnlyWhenThereIsAnEventId() {
        assertTrue(new GoogleCalendarService.CalendarResult("id", null).added());
        assertFalse(new GoogleCalendarService.CalendarResult(null, "problem").added());
    }

    // --------------------------------------------------------- deleteEvent

    /*
     * Checks: An event can be deleted.
     * How:    The fake Google saw a DELETE to the right event URL with the bearer token, and the
     *   method returns true.
     */
    @Test void deletesTheEvent() {
        boolean removed = service().deleteEvent("event-1");

        assertTrue(removed);
        Seen delete = seen.get(1);
        assertEquals("DELETE", delete.method());
        assertEquals("/calendar/calendars/primary/events/event-1", delete.path());
        assertEquals("Bearer token-123", delete.authorization());
    }

    /*
     * Checks: An event that is already gone counts as removed.
     * How:    Google answers that the event is gone (404 or 410). deleteEvent returns true.
     */
    @Test void anEventThatIsAlreadyGoneCountsAsRemoved() {
        deleteStatus = 404;
        assertTrue(service().deleteEvent("event-1"));

        deleteStatus = 410;
        assertTrue(service().deleteEvent("event-1"));
    }

    /*
     * Checks: A refused delete returns false.
     * How:    Google answers with an error, and deleteEvent returns false.
     */
    @Test void reportsFailureWhenGoogleRefusesToDelete() {
        deleteStatus = 403;

        assertFalse(service().deleteEvent("event-1"));
    }

    /*
     * Checks: Delete does nothing without an event id.
     * How:    null and blank return false and Google sees no request.
     */
    @Test void deleteDoesNothingWithoutAnEventId() {
        assertFalse(service().deleteEvent(null));
        assertFalse(service().deleteEvent("  "));
        assertTrue(seen.isEmpty());
    }

    /*
     * Checks: Delete does nothing when not configured.
     * How:    It returns false and Google sees no request.
     */
    @Test void deleteDoesNothingWhenNotConfigured() {
        properties.getGoogleCalendar().setClientSecret("");

        assertFalse(service().deleteEvent("event-1"));
        assertTrue(seen.isEmpty());
    }

    /*
     * Checks: Delete returns false when the sign-in is refused.
     * How:    The fake Google refuses the token request.
     */
    @Test void deleteReportsFailureWhenTheSignInIsRefused() {
        tokenStatus = 401;
        tokenBody = "{\"error\":\"invalid_client\"}";

        assertFalse(service().deleteEvent("event-1"));
    }

    /*
     * Checks: Delete returns false when Google cannot be reached.
     * How:    The fake Google is stopped, so the call fails and returns false.
     */
    @Test void deleteReportsFailureWhenGoogleCannotBeReached() {
        server.stop(0);

        assertFalse(service().deleteEvent("event-1"));
    }

    /*
     * Checks: The event id is URL-encoded so it cannot change the address.
     * How:    An id like a/b c becomes a%2Fb+c in the request path.
     */
    @Test void theEventIdIsEncodedSoItCannotChangeTheAddress() {
        service().deleteEvent("a/b c");

        assertEquals("/calendar/calendars/primary/events/a%2Fb+c", seen.get(1).path());
    }
}