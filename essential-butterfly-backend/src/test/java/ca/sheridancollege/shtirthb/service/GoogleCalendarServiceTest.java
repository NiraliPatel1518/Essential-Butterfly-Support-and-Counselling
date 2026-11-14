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

    @Test void isConfiguredWhenAllThreeSecretsArePresent() {
        assertTrue(service().isConfigured());
    }

    @Test void isNotConfiguredWhenASecretIsMissing() {
        properties.getGoogleCalendar().setRefreshToken("");

        assertFalse(service().isConfigured());
    }

    // --------------------------------------------------------- createEvent

    @Test void createsAnEventAndReturnsItsId() {
        var result = service().createEvent("Appointment: Ann", "Client: Ann", START, END);

        assertTrue(result.added());
        assertEquals("event-1", result.eventId());
        assertNull(result.problem());
    }

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

    @Test void theEventCarriesTheTitleTheTimesAndTheClinicTimeZone() {
        service().createEvent("Appointment: Ann", "Client: Ann", START, END);

        String body = seen.get(1).body();
        assertTrue(body.contains("\"summary\":\"Appointment: Ann\""));
        assertTrue(body.contains("\"description\":\"Client: Ann\""));
        assertTrue(body.contains("\"start\":{\"dateTime\":\"2026-11-16T10:00:00\",\"timeZone\":\"America/Toronto\"}"));
        assertTrue(body.contains("\"end\":{\"dateTime\":\"2026-11-16T11:00:00\",\"timeZone\":\"America/Toronto\"}"));
    }

    @Test void timesWithZeroSecondsStillSendTheSeconds() {
        service().createEvent("T", "D", LocalDateTime.parse("2026-11-16T09:30"), LocalDateTime.parse("2026-11-16T10:00"));

        String body = seen.get(1).body();
        assertTrue(body.contains("2026-11-16T09:30:00"));
        assertTrue(body.contains("2026-11-16T10:00:00"));
    }

    @Test void theEventAsksForAPopupAndAnEmailReminder() {
        service().createEvent("T", "D", START, END);

        String body = seen.get(1).body();
        assertTrue(body.contains("\"useDefault\":false"));
        assertTrue(body.contains("{\"method\":\"popup\",\"minutes\":30}"));
        assertTrue(body.contains("{\"method\":\"email\",\"minutes\":60}"));
    }

    @Test void specialCharactersInTheTextAreEscaped() {
        service().createEvent("Say \"hi\"", "Line one\nLine two\\ end", START, END);

        String body = seen.get(1).body();
        assertTrue(body.contains("\"summary\":\"Say \\\"hi\\\"\""));
        assertTrue(body.contains("Line one\\nLine two\\\\ end"));
    }

    @Test void usesTheConfiguredCalendarAndEncodesItsName() {
        properties.getGoogleCalendar().setCalendarId("clinic@example.org");

        service().createEvent("T", "D", START, END);

        assertEquals("/calendar/calendars/clinic%40example.org/events", seen.get(1).path());
    }

    @Test void doesNotContactGoogleWhenNotConfigured() {
        properties.getGoogleCalendar().setClientId("");

        var result = service().createEvent("T", "D", START, END);

        assertFalse(result.added());
        assertEquals("Google Calendar is not connected.", result.problem());
        assertTrue(seen.isEmpty());
    }

    @Test void explainsAGoogleErrorUsingGooglesOwnWords() {
        eventStatus = 403;
        eventBody = "{\"error\":{\"code\":403,\"message\":\"Calendar API has not been used.\"}}";

        var result = service().createEvent("T", "D", START, END);

        assertFalse(result.added());
        assertNull(result.eventId());
        assertEquals("HTTP 403: Calendar API has not been used.", result.problem());
    }

    @Test void usesTheErrorCodeWhenGoogleGivesNoMessage() {
        eventStatus = 400;
        eventBody = "{\"error\":\"invalid_request\"}";

        var result = service().createEvent("T", "D", START, END);

        assertEquals("HTTP 400: invalid_request", result.problem());
    }

    @Test void saysNoDetailsWhenGoogleExplainsNothing() {
        eventStatus = 500;
        eventBody = "oops";

        var result = service().createEvent("T", "D", START, END);

        assertEquals("HTTP 500: no details", result.problem());
    }

    @Test void reportsWhenGoogleReturnsNoEventId() {
        eventBody = "{\"status\":\"confirmed\"}";

        var result = service().createEvent("T", "D", START, END);

        assertFalse(result.added());
        assertEquals("Google did not return an event id.", result.problem());
    }

    @Test void reportsWhenGoogleRefusesTheSignIn() {
        tokenStatus = 400;
        tokenBody = "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been revoked.\"}";

        var result = service().createEvent("T", "D", START, END);

        assertFalse(result.added());
        assertEquals("Google refused the sign-in. HTTP 400: Token has been revoked.", result.problem());
        assertEquals(1, seen.size());
    }

    @Test void reportsWhenTheSignInAnswerHasNoAccessToken() {
        tokenBody = "{\"expires_in\":3600}";

        var result = service().createEvent("T", "D", START, END);

        assertFalse(result.added());
        assertTrue(result.problem().startsWith("Google refused the sign-in."));
    }

    @Test void reportsWhenGoogleCannotBeReached() {
        server.stop(0);

        var result = service().createEvent("T", "D", START, END);

        assertFalse(result.added());
        assertNull(result.eventId());
    }

    @Test void theResultSaysAddedOnlyWhenThereIsAnEventId() {
        assertTrue(new GoogleCalendarService.CalendarResult("id", null).added());
        assertFalse(new GoogleCalendarService.CalendarResult(null, "problem").added());
    }

    // --------------------------------------------------------- deleteEvent

    @Test void deletesTheEvent() {
        boolean removed = service().deleteEvent("event-1");

        assertTrue(removed);
        Seen delete = seen.get(1);
        assertEquals("DELETE", delete.method());
        assertEquals("/calendar/calendars/primary/events/event-1", delete.path());
        assertEquals("Bearer token-123", delete.authorization());
    }

    @Test void anEventThatIsAlreadyGoneCountsAsRemoved() {
        deleteStatus = 404;
        assertTrue(service().deleteEvent("event-1"));

        deleteStatus = 410;
        assertTrue(service().deleteEvent("event-1"));
    }

    @Test void reportsFailureWhenGoogleRefusesToDelete() {
        deleteStatus = 403;

        assertFalse(service().deleteEvent("event-1"));
    }

    @Test void deleteDoesNothingWithoutAnEventId() {
        assertFalse(service().deleteEvent(null));
        assertFalse(service().deleteEvent("  "));
        assertTrue(seen.isEmpty());
    }

    @Test void deleteDoesNothingWhenNotConfigured() {
        properties.getGoogleCalendar().setClientSecret("");

        assertFalse(service().deleteEvent("event-1"));
        assertTrue(seen.isEmpty());
    }

    @Test void deleteReportsFailureWhenTheSignInIsRefused() {
        tokenStatus = 401;
        tokenBody = "{\"error\":\"invalid_client\"}";

        assertFalse(service().deleteEvent("event-1"));
    }

    @Test void deleteReportsFailureWhenGoogleCannotBeReached() {
        server.stop(0);

        assertFalse(service().deleteEvent("event-1"));
    }

    @Test void theEventIdIsEncodedSoItCannotChangeTheAddress() {
        service().deleteEvent("a/b c");

        assertEquals("/calendar/calendars/primary/events/a%2Fb+c", seen.get(1).path());
    }
}