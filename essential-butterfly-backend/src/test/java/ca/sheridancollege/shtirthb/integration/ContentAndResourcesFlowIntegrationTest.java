package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import ca.sheridancollege.shtirthb.service.JwtService;

/*
 * UC-09 end-to-end flow: website content pages and the Resource Library.
 *
 * Runs the real application against a real PostgreSQL (Testcontainers) with
 * the real Flyway migrations (V11 tables, triggers and seed pages; V12), so
 * it checks the controllers, security rules, services, validators, database
 * constraints and audit triggers working together.
 *
 * Each test rolls back, except security events, which the application
 * writes in their own transaction on purpose (so a rejected upload is still
 * logged). Those assertions compare counts before and after.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///content_resources_flow_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890"
})
@AutoConfigureMockMvc
@Transactional
class ContentAndResourcesFlowIntegrationTest {

    private static final String BODY_ABOUT =
            "{\"fields\":{\"intro\":\"We support families.\"}}";

    private static final String BODY_SERVICES =
            "{\"fields\":{\"intro\":\"What we offer\"},"
            + "\"services\":[{\"id\":\"counselling\",\"title\":\"Counselling\","
            + "\"text\":\"One to one support.\"}]}";

    private static final byte[] PDF_BYTES =
            "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n"
                    .getBytes(StandardCharsets.US_ASCII);

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0, 'I', 'E', 'N', 'D'
    };

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;

    // ------------------------------------------------------------ helpers

    private String adminToken() {
        return "Bearer " + jwt.generateToken("admin@test.com", "ADMIN");
    }

    private String clientToken() {
        return "Bearer " + jwt.generateToken("client@test.com", "CLIENT");
    }

    private String pageJson(String title, String body, boolean publish) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                java.util.Map.of("title", title, "body", body, "publish", publish));
    }

    private void savePage(String key, String title, String body, boolean publish)
            throws Exception {
        mvc.perform(put("/api/admin/content/" + key)
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageJson(title, body, publish)))
                .andExpect(status().isOk());
    }

    private MockMultipartHttpServletRequestBuilder resourceRequest(String method, String url) {
        MockMultipartHttpServletRequestBuilder builder = multipart(url);
        builder.with(request -> {
            request.setMethod(method);
            return request;
        });
        return builder.header(HttpHeaders.AUTHORIZATION, adminToken());
    }

    private long createLinkResource(String title, String link, boolean publish)
            throws Exception {

        String json = mvc.perform(resourceRequest("POST", "/api/admin/resources")
                        .param("title", title)
                        .param("description", "A short description")
                        .param("category", "Guides")
                        .param("linkUrls", link)
                        .param("publish", String.valueOf(publish)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long createFileResource(
            String title, MockMultipartFile file, String altText, boolean publish)
            throws Exception {

        MockMultipartHttpServletRequestBuilder request =
                resourceRequest("POST", "/api/admin/resources")
                        .file(file)
                        .param("title", title)
                        .param("description", "A short description")
                        .param("category", "Guides")
                        .param("publish", String.valueOf(publish));

        if (altText != null) {
            request.param("altText", altText);
        }

        String json = mvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private int securityEvents(String action) {
        Integer count = jdbc.queryForObject(
                "select count(*) from audit_log where action = ?",
                Integer.class, action);
        return count == null ? 0 : count;
    }

    private String auditLog() throws Exception {
        return mvc.perform(get("/api/admin/audit-log").param("limit", "100")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------ content pages

    @Test
    @DisplayName("Pages start unpublished: visitors get 204 and the built-in page stays")
    void unpublishedPages_returnNoContent() throws Exception {
        for (String key : new String[] {"services", "about", "privacy"}) {
            mvc.perform(get("/api/content/" + key))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @DisplayName("Admin sees the three seeded pages")
    void adminListsTheSeededPages() throws Exception {
        mvc.perform(get("/api/admin/content")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.pageKey=='services')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.pageKey=='about')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.pageKey=='privacy')]").isNotEmpty());
    }

    @Test
    @DisplayName("A saved draft is visible to admin but never to visitors")
    void draft_doesNotLeakToVisitors() throws Exception {
        savePage("about", "About us (draft)", BODY_ABOUT, false);

        mvc.perform(get("/api/content/about"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/content/about")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.draftTitle").value("About us (draft)"))
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(true));
    }

    @Test
    @DisplayName("Publishing makes the page public with its title and body")
    void publish_makesThePagePublic() throws Exception {
        savePage("about", "About us", BODY_ABOUT, true);

        mvc.perform(get("/api/content/about"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageKey").value("about"))
                .andExpect(jsonPath("$.title").value("About us"))
                .andExpect(jsonPath("$.body").value(BODY_ABOUT))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        mvc.perform(get("/api/admin/content/about")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(false));
    }

    @Test
    @DisplayName("Editing a published page keeps the live copy until it is published again")
    void editAfterPublish_keepsLiveCopyUntilRepublished() throws Exception {
        savePage("about", "About us", BODY_ABOUT, true);
        savePage("about", "About us v2", BODY_ABOUT, false);

        mvc.perform(get("/api/content/about"))
                .andExpect(jsonPath("$.title").value("About us"));

        mvc.perform(get("/api/admin/content/about")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(true))
                .andExpect(jsonPath("$.publishedTitle").value("About us"))
                .andExpect(jsonPath("$.draftTitle").value("About us v2"));

        savePage("about", "About us v2", BODY_ABOUT, true);

        mvc.perform(get("/api/content/about"))
                .andExpect(jsonPath("$.title").value("About us v2"));
    }

    @Test
    @DisplayName("The services page accepts a services list")
    void servicesPage_acceptsServicesList() throws Exception {
        savePage("services", "Our services", BODY_SERVICES, true);

        mvc.perform(get("/api/content/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Our services"));
    }

    @Test
    @DisplayName("A services list on another page is rejected with 400")
    void servicesList_onAnotherPage_isRejected() throws Exception {
        mvc.perform(put("/api/admin/content/about")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageJson("About", BODY_SERVICES, true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Missing title, empty body, bad JSON and over-long text return 400")
    void invalidPageContent_isRejected() throws Exception {
        String[][] bad = {
                {"", BODY_ABOUT},
                {"Title", ""},
                {"Title", "not json"},
                {"Title", "{\"unknown\":{}}"},
                {"Title", "{\"fields\":{\"intro\":\"" + "x".repeat(1501) + "\"}}"}
        };

        for (String[] pair : bad) {
            mvc.perform(put("/api/admin/content/about")
                            .header(HttpHeaders.AUTHORIZATION, adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pageJson(pair[0], pair[1], true)))
                    .andExpect(status().isBadRequest());
        }

        mvc.perform(get("/api/content/about"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("An unknown page key returns 404 for admin")
    void unknownPageKey_isNotFound() throws Exception {
        mvc.perform(get("/api/admin/content/unknown")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/admin/content/unknown")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageJson("T", BODY_ABOUT, true)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Content admin routes need a token (401) and the admin role (403)")
    void contentAdminRoutes_areProtected() throws Exception {
        mvc.perform(get("/api/admin/content"))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/admin/content/about")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageJson("T", BODY_ABOUT, true)))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/content")
                        .header(HttpHeaders.AUTHORIZATION, clientToken()))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/admin/content/about")
                        .header(HttpHeaders.AUTHORIZATION, clientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageJson("T", BODY_ABOUT, true)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/content/about"))
                .andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------- resources

    @Test
    @DisplayName("A draft link resource is hidden from visitors until it is published")
    void linkResource_draftThenPublish() throws Exception {
        long id = createLinkResource(
                "Coping guide", "https://example.org/coping", false);

        mvc.perform(get("/api/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title=='Coping guide')]").isEmpty());

        mvc.perform(get("/api/admin/resources")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(jsonPath("$[?(@.title=='Coping guide')].status")
                        .value("DRAFT"));

        mvc.perform(resourceRequest("PUT", "/api/admin/resources/" + id)
                        .param("title", "Coping guide")
                        .param("description", "A short description")
                        .param("category", "Guides")
                        .param("linkUrls", "https://example.org/coping")
                        .param("publish", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mvc.perform(get("/api/resources"))
                .andExpect(jsonPath("$[?(@.title=='Coping guide')]").isNotEmpty());
    }

    @Test
    @DisplayName("Publishing a document makes it downloadable with safe headers")
    void pdfResource_isDownloadable() throws Exception {
        long id = createFileResource(
                "Intake checklist",
                new MockMultipartFile("file", "checklist.pdf",
                        "application/pdf", PDF_BYTES),
                null, true);

        mvc.perform(get("/api/resources/" + id + "/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("checklist.pdf")))
                .andExpect(content().bytes(PDF_BYTES));
    }

    @Test
    @DisplayName("A draft document cannot be downloaded by visitors")
    void draftDocument_isNotDownloadable() throws Exception {
        long id = createFileResource(
                "Hidden guide",
                new MockMultipartFile("file", "hidden.pdf",
                        "application/pdf", PDF_BYTES),
                null, false);

        mvc.perform(get("/api/resources/" + id + "/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("An image needs alternative text; with it, the upload works")
    void image_needsAltText() throws Exception {
        MockMultipartFile png = new MockMultipartFile(
                "file", "poster.png", "image/png", PNG_BYTES);

        mvc.perform(resourceRequest("POST", "/api/admin/resources")
                        .file(png)
                        .param("title", "Poster")
                        .param("description", "A poster")
                        .param("category", "Posters")
                        .param("publish", "true"))
                .andExpect(status().isBadRequest());

        long id = createFileResource("Poster", png, "A butterfly on a leaf", true);

        mvc.perform(get("/api/resources"))
                .andExpect(jsonPath("$[?(@.id==" + id + ")].altText")
                        .value("A butterfly on a leaf"));
    }

    @Test
    @DisplayName("Missing title, category or content returns 400")
    void missingRequiredFields_returns400() throws Exception {
        mvc.perform(resourceRequest("POST", "/api/admin/resources")
                        .param("description", "No title")
                        .param("category", "Guides")
                        .param("linkUrls", "https://example.org/a"))
                .andExpect(status().isBadRequest());

        mvc.perform(resourceRequest("POST", "/api/admin/resources")
                        .param("title", "No category")
                        .param("description", "d")
                        .param("linkUrls", "https://example.org/b"))
                .andExpect(status().isBadRequest());

        mvc.perform(resourceRequest("POST", "/api/admin/resources")
                        .param("title", "No link or file")
                        .param("description", "d")
                        .param("category", "Guides"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A duplicate title (any capitalisation) or link returns 409")
    void duplicates_returnConflict() throws Exception {
        createLinkResource("Unique title", "https://example.org/unique", false);

        mvc.perform(resourceRequest("POST", "/api/admin/resources")
                        .param("title", "UNIQUE TITLE")
                        .param("description", "d")
                        .param("category", "Guides")
                        .param("linkUrls", "https://example.org/other"))
                .andExpect(status().isConflict());

        mvc.perform(resourceRequest("POST", "/api/admin/resources")
                        .param("title", "Different title")
                        .param("description", "d")
                        .param("category", "Guides")
                        .param("linkUrls", "https://example.org/unique"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Unsafe links are rejected with 400 and logged as a security event")
    void unsafeLink_isRejectedAndLogged() throws Exception {
        int before = securityEvents("SECURITY_UNSAFE_LINK_REJECTED");

        for (String link : new String[] {
                "javascript:alert(1)", "http://example.org/plain",
                "https://user:pass@example.org/x"}) {

            mvc.perform(resourceRequest("POST", "/api/admin/resources")
                            .param("title", "Bad link " + link.length())
                            .param("description", "d")
                            .param("category", "Guides")
                            .param("linkUrls", link))
                    .andExpect(status().isBadRequest());
        }

        assertTrue(securityEvents("SECURITY_UNSAFE_LINK_REJECTED") > before,
                "rejected links should be written to the audit log");
    }

    @Test
    @DisplayName("A fake or executable file is rejected with 400 and logged")
    void unsafeFile_isRejectedAndLogged() throws Exception {
        int before = securityEvents("SECURITY_UNSAFE_FILE_REJECTED");

        // text pretending to be a PDF
        mvc.perform(resourceRequest("POST", "/api/admin/resources")
                        .file(new MockMultipartFile("file", "fake.pdf",
                                "application/pdf",
                                "this is not a pdf".getBytes(StandardCharsets.UTF_8)))
                        .param("title", "Fake pdf")
                        .param("description", "d")
                        .param("category", "Guides"))
                .andExpect(status().isBadRequest());

        // executable
        mvc.perform(resourceRequest("POST", "/api/admin/resources")
                        .file(new MockMultipartFile("file", "setup.exe",
                                "application/octet-stream",
                                new byte[] {'M', 'Z', 1, 2, 3}))
                        .param("title", "Program")
                        .param("description", "d")
                        .param("category", "Guides"))
                .andExpect(status().isBadRequest());

        assertTrue(securityEvents("SECURITY_UNSAFE_FILE_REJECTED") > before,
                "rejected files should be written to the audit log");
    }

    @Test
    @DisplayName("Removing a resource deletes it everywhere and its download returns 404")
    void remove_hidesTheResource() throws Exception {
        long id = createFileResource(
                "Old guide",
                new MockMultipartFile("file", "old.pdf",
                        "application/pdf", PDF_BYTES),
                null, true);

        mvc.perform(delete("/api/admin/resources/" + id)
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/resources/" + id + "/download"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/resources"))
                .andExpect(jsonPath("$[?(@.id==" + id + ")]").isEmpty());

        mvc.perform(get("/api/admin/resources")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(jsonPath("$[?(@.id==" + id + ")]").isEmpty());
    }

    @Test
    @DisplayName("A removed title can be used again")
    void removedTitle_canBeReused() throws Exception {
        long id = createLinkResource("Reusable", "https://example.org/r1", false);

        mvc.perform(delete("/api/admin/resources/" + id)
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isNoContent());

        createLinkResource("Reusable", "https://example.org/r1", false);
    }

    @Test
    @DisplayName("Editing or removing an unknown resource returns 404")
    void unknownResource_isNotFound() throws Exception {
        mvc.perform(delete("/api/admin/resources/999999")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isNotFound());

        mvc.perform(resourceRequest("PUT", "/api/admin/resources/999999")
                        .param("title", "x")
                        .param("description", "d")
                        .param("category", "c")
                        .param("linkUrls", "https://example.org/z"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/resources/999999/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Upload rules tell the form what is accepted")
    void uploadRules_areAvailableToAdmin() throws Exception {
        mvc.perform(get("/api/admin/resources/upload-rules")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("pdf")));
    }

    @Test
    @DisplayName("Resource admin routes need a token (401) and the admin role (403)")
    void resourceAdminRoutes_areProtected() throws Exception {
        mvc.perform(get("/api/admin/resources"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/resources/upload-rules"))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/admin/resources/1"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/resources")
                        .header(HttpHeaders.AUTHORIZATION, clientToken()))
                .andExpect(status().isForbidden());

        mvc.perform(multipart("/api/admin/resources")
                        .param("title", "x")
                        .header(HttpHeaders.AUTHORIZATION, clientToken()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/audit-log"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/audit-log")
                        .header(HttpHeaders.AUTHORIZATION, clientToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("The public resource list needs no login")
    void publicResourceList_needsNoLogin() throws Exception {
        mvc.perform(get("/api/resources"))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------- audit log

    @Test
    @DisplayName("Database triggers record content and resource changes in the audit log")
    void changes_areWrittenToTheAuditLog() throws Exception {
        savePage("privacy", "Privacy", BODY_ABOUT, false);
        savePage("privacy", "Privacy", BODY_ABOUT, true);

        long id = createLinkResource("Audited", "https://example.org/audit", true);

        mvc.perform(delete("/api/admin/resources/" + id)
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isNoContent());

        String log = auditLog();

        assertTrue(log.contains("CONTENT_DRAFT_SAVED"), log);
        assertTrue(log.contains("CONTENT_PUBLISHED"), log);
        assertTrue(log.contains("RESOURCE_CREATED"), log);
        assertTrue(log.contains("RESOURCE_REMOVED"), log);
    }

    @Test
    @DisplayName("The audit log limit is respected")
    void auditLog_respectsLimit() throws Exception {
        savePage("privacy", "Privacy", BODY_ABOUT, false);
        savePage("privacy", "Privacy 2", BODY_ABOUT, false);
        savePage("privacy", "Privacy 3", BODY_ABOUT, false);

        String json = mvc.perform(get("/api/admin/audit-log").param("limit", "2")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(2, ((java.util.List<?>) JsonPath.read(json, "$")).size());
    }
}