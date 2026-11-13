package ca.sheridancollege.shtirthb.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ca.sheridancollege.shtirthb.config.AppProperties;

/*
 * Puts accepted appointments into the administrator's Google Calendar so the
 * calendar notifies them (pop-up and e-mail reminders).
 *
 * It talks to Google's REST API with a refresh token that comes from the
 * environment (Docker secrets), so no address or credential is written in the
 * code. The answers are "did it work": when Google is not configured or not
 * reachable the appointment itself stays confirmed and the administrator is
 * told the calendar step needs another try.
 */
@Service
public class GoogleCalendarService {

    private static final Logger log =
            LoggerFactory.getLogger(GoogleCalendarService.class);

    private static final Pattern ACCESS_TOKEN =
            Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    private static final Pattern GOOGLE_PROBLEM = Pattern.compile(
            "\"(?:message|error_description)\"\\s*:\\s*\"([^\"]+)\"");

    private static final Pattern GOOGLE_ERROR_CODE =
            Pattern.compile("\"error\"\\s*:\\s*\"([^\"]+)\"");

    private static final Pattern EVENT_ID =
            Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    private final AppProperties.GoogleCalendar settings;
    private final AppointmentClock clock;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public GoogleCalendarService(
            AppProperties properties,
            AppointmentClock clock) {

        this.settings = properties.getGoogleCalendar();
        this.clock = clock;
    }

    public boolean isConfigured() {
        return settings.isConfigured();
    }

    /*
     * The outcome of creating an event: the new event's id, or (when it did
     * not work) a short plain explanation taken from Google's own answer.
     */
    public record CalendarResult(String eventId, String problem) {

        public boolean added() {
            return eventId != null;
        }
    }

    public CalendarResult createEvent(
            String title,
            String description,
            LocalDateTime startsAt,
            LocalDateTime endsAt) {

        if (!isConfigured()) {
            return new CalendarResult(null, "Google Calendar is not connected.");
        }

        try {

            String token = fetchAccessToken();

            String zone = clock.zone().getId();

            String body = "{"
                    + "\"summary\":" + json(title) + ","
                    + "\"description\":" + json(description) + ","
                    + "\"start\":{\"dateTime\":" + json(rfc3339(startsAt))
                    + ",\"timeZone\":" + json(zone) + "},"
                    + "\"end\":{\"dateTime\":" + json(rfc3339(endsAt))
                    + ",\"timeZone\":" + json(zone) + "},"
                    + "\"reminders\":{\"useDefault\":false,\"overrides\":["
                    + "{\"method\":\"popup\",\"minutes\":30},"
                    + "{\"method\":\"email\",\"minutes\":60}]}"
                    + "}";

            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(eventsUrl()))
                            .timeout(Duration.ofSeconds(30))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {

                log.error("Google Calendar rejected the new event: HTTP {} {}",
                        response.statusCode(), response.body());

                return new CalendarResult(
                        null, explain(response.statusCode(), response.body()));
            }

            Matcher id = EVENT_ID.matcher(response.body());

            return id.find()
                    ? new CalendarResult(id.group(1), null)
                    : new CalendarResult(null,
                            "Google did not return an event id.");

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();
            log.error("Google Calendar request was interrupted.", ex);
            return new CalendarResult(null, "The request was interrupted.");

        } catch (Exception ex) {

            log.error("Google Calendar could not be reached.", ex);
            return new CalendarResult(null, ex.getMessage());
        }
    }

    // True when the event is gone (an event that no longer exists counts).
    public boolean deleteEvent(String eventId) {

        if (!isConfigured() || eventId == null || eventId.isBlank()) {
            return false;
        }

        try {

            String token = fetchAccessToken();

            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(
                                    eventsUrl() + "/" + encode(eventId)))
                            .timeout(Duration.ofSeconds(30))
                            .header("Authorization", "Bearer " + token)
                            .DELETE()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();

            if ((status >= 200 && status < 300)
                    || status == 404 || status == 410) {
                return true;
            }

            log.error("Google Calendar refused to remove an event: HTTP {} {}",
                    status, response.body());

            return false;

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();
            log.error("Google Calendar request was interrupted.", ex);
            return false;

        } catch (Exception ex) {

            log.error("Google Calendar could not be reached.", ex);
            return false;
        }
    }

    // ------------------------------------------------------------ helpers

    private String fetchAccessToken() throws Exception {

        String form = "client_id=" + encode(settings.getClientId())
                + "&client_secret=" + encode(settings.getClientSecret())
                + "&refresh_token=" + encode(settings.getRefreshToken())
                + "&grant_type=refresh_token";

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(settings.getTokenUrl()))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type",
                                "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        Matcher token = ACCESS_TOKEN.matcher(response.body());

        if (response.statusCode() != 200 || !token.find()) {

            log.error("Google refused the sign-in: HTTP {} {}",
                    response.statusCode(), response.body());

            throw new IllegalStateException(
                    "Google refused the sign-in. "
                    + explain(response.statusCode(), response.body()));
        }

        return token.group(1);
    }

    // "HTTP 403: Google Calendar API has not been used in project ..."
    private String explain(int status, String body) {

        Matcher message = GOOGLE_PROBLEM.matcher(body == null ? "" : body);
        Matcher code = GOOGLE_ERROR_CODE.matcher(body == null ? "" : body);

        String detail = message.find()
                ? message.group(1)
                : code.find() ? code.group(1) : "no details";

        return "HTTP " + status + ": " + detail;
    }

    // Google needs seconds ("2026-11-13T14:55:00"); LocalDateTime.toString()
    // leaves them out when they are zero.
    private String rfc3339(LocalDateTime time) {
        return time.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                .format(java.time.format.DateTimeFormatter
                        .ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }

    private String eventsUrl() {

        String base = settings.getApiBaseUrl().replaceAll("/+$", "");

        return base + "/calendars/"
                + encode(settings.getCalendarId()) + "/events";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String json(String value) {

        StringBuilder out = new StringBuilder("\"");

        for (char c : (value == null ? "" : value).toCharArray()) {

            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }

        return out.append('"').toString();
    }
}
