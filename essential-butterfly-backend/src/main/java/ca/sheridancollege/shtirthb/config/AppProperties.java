
package ca.sheridancollege.shtirthb.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();
    private Mail mail = new Mail();
    private Content content = new Content();
    private Appointments appointments = new Appointments();
    private GoogleCalendar googleCalendar = new GoogleCalendar();
    private MemoryWall memoryWall = new MemoryWall();

    private String frontendBaseUrl;
    private String publicBaseUrl;

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }

    public Appointments getAppointments() {
        return appointments;
    }

    public void setAppointments(Appointments appointments) {
        this.appointments = appointments;
    }

    public GoogleCalendar getGoogleCalendar() {
        return googleCalendar;
    }

    public void setGoogleCalendar(GoogleCalendar googleCalendar) {
        this.googleCalendar = googleCalendar;
    }

    public MemoryWall getMemoryWall() {
        return memoryWall;
    }

    public void setMemoryWall(MemoryWall memoryWall) {
        this.memoryWall = memoryWall;
    }

    public String getFrontendBaseUrl() {
        return frontendBaseUrl;
    }

    public void setFrontendBaseUrl(String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    /*
     * Memory Wall (UC-12). The values come from application.properties
     * (app.memory-wall.*), so no limit is written into the services.
     */
    public static class MemoryWall {

        // Where the picture files are kept: "local" (a folder) or "s3".
        private String storage = "local";

        // Folder used when storage is "local".
        private String storageDirectory = "data/memory-wall";

        // At most this many photos can be uploaded in total.
        private int maxPhotos = 150;

        // At most this many photos can be shown on the public wall.
        private int maxPublishedPhotos = 120;

        private long maxFileSizeBytes = 5L * 1024 * 1024;

        private List<String> allowedFileExtensions =
                new ArrayList<>(List.of("jpg", "jpeg", "png", "webp"));

        public String getStorage() {
            return storage;
        }

        public void setStorage(String storage) {
            this.storage = storage;
        }

        public String getStorageDirectory() {
            return storageDirectory;
        }

        public void setStorageDirectory(String storageDirectory) {
            this.storageDirectory = storageDirectory;
        }

        public int getMaxPhotos() {
            return maxPhotos;
        }

        public void setMaxPhotos(int maxPhotos) {
            this.maxPhotos = maxPhotos;
        }

        public int getMaxPublishedPhotos() {
            return maxPublishedPhotos;
        }

        public void setMaxPublishedPhotos(int maxPublishedPhotos) {
            this.maxPublishedPhotos = maxPublishedPhotos;
        }

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }

        public List<String> getAllowedFileExtensions() {
            return allowedFileExtensions;
        }

        public void setAllowedFileExtensions(List<String> allowedFileExtensions) {
            this.allowedFileExtensions = allowedFileExtensions;
        }
    }

    /*
     * Appointment scheduling rules (UC-11). The values come from
     * application.properties (app.appointments.*).
     */
    public static class Appointments {

        // The clinic's local time zone: slots, "now" and calendar events.
        private String timeZone = "America/Toronto";

        // Availability is published for this many calendar days, including today.
        private int bookingWindowDays = 7;

        public String getTimeZone() {
            return timeZone;
        }

        public void setTimeZone(String timeZone) {
            this.timeZone = timeZone;
        }

        public int getBookingWindowDays() {
            return bookingWindowDays;
        }

        public void setBookingWindowDays(int bookingWindowDays) {
            this.bookingWindowDays = bookingWindowDays;
        }
    }

    /*
     * Google Calendar connection (UC-11). Credentials come from environment
     * variables (Docker secrets); when they are empty the calendar step is
     * skipped and the administrator is told.
     */
    public static class GoogleCalendar {

        private String clientId = "";
        private String clientSecret = "";
        private String refreshToken = "";
        private String calendarId = "primary";
        private String tokenUrl = "https://oauth2.googleapis.com/token";
        private String apiBaseUrl = "https://www.googleapis.com/calendar/v3";

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank()
                    && refreshToken != null && !refreshToken.isBlank();
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public String getCalendarId() {
            return calendarId;
        }

        public void setCalendarId(String calendarId) {
            this.calendarId = calendarId;
        }

        public String getTokenUrl() {
            return tokenUrl;
        }

        public void setTokenUrl(String tokenUrl) {
            this.tokenUrl = tokenUrl;
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }
    }

    public static class Cors {

        private List<String> allowedOrigins = new ArrayList<>();
        private List<String> allowedMethods = new ArrayList<>();
        private List<String> allowedHeaders = new ArrayList<>();
        private boolean allowCredentials = true;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
    }

    public static class Jwt {

        private String secret;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class Mail {

        private String fromAddress;

        public String getFromAddress() {
            return fromAddress;
        }

        public void setFromAddress(String fromAddress) {
            this.fromAddress = fromAddress;
        }
    }

    /*
     * Website content and Resource Library rules. The values come from
     * application.properties (app.content.*), so nothing is hard-coded in
     * the services.
     */
    public static class Content {

        private long maxFileSizeBytes = 10L * 1024 * 1024;
        private List<String> allowedFileExtensions =
                new ArrayList<>(List.of("pdf", "docx", "png", "jpg", "jpeg"));
        private List<String> allowedLinkSchemes =
                new ArrayList<>(List.of("https"));

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }

        public List<String> getAllowedFileExtensions() {
            return allowedFileExtensions;
        }

        public void setAllowedFileExtensions(List<String> allowedFileExtensions) {
            this.allowedFileExtensions = allowedFileExtensions;
        }

        public List<String> getAllowedLinkSchemes() {
            return allowedLinkSchemes;
        }

        public void setAllowedLinkSchemes(List<String> allowedLinkSchemes) {
            this.allowedLinkSchemes = allowedLinkSchemes;
        }
    }
}
