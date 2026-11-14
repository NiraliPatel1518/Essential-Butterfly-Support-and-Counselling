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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import ca.sheridancollege.shtirthb.service.JwtService;

/*
 * UC-12 end-to-end flow: the Memory Wall.
 *
 * Runs the real application against a real PostgreSQL (Testcontainers) with
 * the real Flyway migrations (V16 table and audit triggers, V17 feature
 * toggle). Photos are stored in a temporary folder, never in the project.
 *
 * It checks: upload, review (pending until the admin selects it), the public
 * wall and public picture, unpublishing, descriptions, deleting, unsafe
 * uploads, the feature switch, the access rules and the audit log.
 *
 * Each test rolls back, except security events, which the application writes
 * in their own transaction on purpose. Those assertions compare counts.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///memory_wall_flow_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890",
        "app.memory-wall.storage=local"
})
@AutoConfigureMockMvc
@Transactional
class MemoryWallFlowIntegrationTest {

    private static final Path PHOTO_FOLDER = createPhotoFolder();

    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0, 'I', 'E', 'N', 'D'
    };

    private static final byte[] JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4
    };

    private static final String ALL_FEATURES_ON =
            "[{\"featureKey\":\"NEWSLETTER\",\"enabled\":true},"
            + "{\"featureKey\":\"TESTIMONIALS\",\"enabled\":true},"
            + "{\"featureKey\":\"UPDATES\",\"enabled\":true},"
            + "{\"featureKey\":\"APPOINTMENTS\",\"enabled\":true},"
            + "{\"featureKey\":\"MEMORY_WALL\",\"enabled\":%s}]";

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;

    private static Path createPhotoFolder() {
        try {
            return Files.createTempDirectory("memory-wall-test-");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @DynamicPropertySource
    static void photoFolder(DynamicPropertyRegistry registry) {
        registry.add("app.memory-wall.storage-directory", () -> PHOTO_FOLDER.toString());
    }

    @BeforeEach
    void seedFeatureSwitches() throws Exception {
        mvc.perform(get("/api/features")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ helpers

    private String admin() {
        return "Bearer " + jwt.generateToken("admin@test.com", "ADMIN");
    }

    private String client() {
        return "Bearer " + jwt.generateToken("client@test.com", "CLIENT");
    }

    private MockMultipartFile photo(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    private ResultActions upload(MockMultipartFile file, String description) throws Exception {
        var request = multipart("/api/admin/memory-wall/photos")
                .header(HttpHeaders.AUTHORIZATION, admin());

        if (file != null) {
            request.file(file);
        }

        if (description != null) {
            request.param("description", description);
        }

        return mvc.perform(request);
    }

    private long uploadPng(String name, String description) throws Exception {
        String json = upload(photo(name, PNG), description)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions select(long... ids) throws Exception {
        String list = java.util.Arrays.stream(ids)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));

        return mvc.perform(put("/api/admin/memory-wall/selection")
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"photoIds\":[" + list + "]}"));
    }

    private void setFeature(boolean on) throws Exception {
        mvc.perform(put("/api/admin/features")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ALL_FEATURES_ON.formatted(on)))
                .andExpect(status().isOk());
    }

    private int events(String action) {
        Integer count = jdbc.queryForObject(
                "select count(*) from audit_log where action = ?", Integer.class, action);
        return count == null ? 0 : count;
    }

    private String auditLog() throws Exception {
        return mvc.perform(get("/api/admin/audit-log").param("limit", "100")
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private List<String> filesOnDisk() throws IOException {
        try (var stream = Files.walk(PHOTO_FOLDER)) {
            return stream.filter(Files::isRegularFile)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        }
    }

    // ------------------------------------------------------------- upload

    @Test
    @DisplayName("A new photo waits for review and is not public")
    void upload_startsPending() throws Exception {
        upload(photo("trip.png", PNG), "  Our spring trip  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.source").value("ADMIN_UPLOAD"))
                .andExpect(jsonPath("$.fileName").value("trip.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(PNG.length))
                .andExpect(jsonPath("$.description").value("Our spring trip"))
                .andExpect(jsonPath("$.canPublish").value(true))
                .andExpect(jsonPath("$.publishedAt").doesNotExist());

        mvc.perform(get("/api/memory-wall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("JPEG photos are stored as JPEG")
    void upload_acceptsJpeg() throws Exception {
        upload(photo("trip.jpeg", JPEG), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    @DisplayName("A blank description is stored as no description")
    void upload_blankDescriptionIsNone() throws Exception {
        upload(photo("trip.png", PNG), "   ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    @DisplayName("A description of 200 characters is fine; 201 is refused")
    void upload_descriptionLimit() throws Exception {
        upload(photo("a.png", PNG), "x".repeat(200)).andExpect(status().isCreated());
        upload(photo("b.png", PNG), "x".repeat(201)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("The photo file is written to the storage folder")
    void upload_storesTheFile() throws Exception {
        int before = filesOnDisk().size();

        uploadPng("trip.png", null);

        assertEquals(before + 1, filesOnDisk().size());
    }

    @Test
    @DisplayName("No file returns 400")
    void upload_withoutAFile() throws Exception {
        upload(null, "No picture")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a photo to upload."));
    }

    @Test
    @DisplayName("A fake photo is refused with 400 and logged as a security event")
    void upload_fakePhotoIsRefusedAndLogged() throws Exception {
        int before = events("SECURITY_UNSAFE_FILE_REJECTED");

        upload(photo("fake.png", "not a picture".getBytes(StandardCharsets.UTF_8)), null)
                .andExpect(status().isBadRequest());

        assertTrue(events("SECURITY_UNSAFE_FILE_REJECTED") > before);
    }

    @Test
    @DisplayName("Programs and web pages are refused with 400 and logged")
    void upload_programsAreRefusedAndLogged() throws Exception {
        int before = events("SECURITY_UNSAFE_FILE_REJECTED");

        upload(photo("setup.exe", new byte[] {'M', 'Z', 1, 2}), null)
                .andExpect(status().isBadRequest());
        upload(photo("photo.html.png", PNG), null)
                .andExpect(status().isBadRequest());

        assertTrue(events("SECURITY_UNSAFE_FILE_REJECTED") > before);
    }

    @Test
    @DisplayName("A format that is not allowed is refused with 400")
    void upload_wrongFormatIsRefused() throws Exception {
        upload(photo("photo.gif", PNG), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("This file format is not supported. Please select a valid image."));
    }

    // ------------------------------------------------- review and publish

    @Test
    @DisplayName("A pending photo is hidden from visitors, picture included")
    void pendingPhoto_isHidden() throws Exception {
        long id = uploadPng("trip.png", "Spring trip");

        mvc.perform(get("/api/memory-wall"))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/memory-wall/photos/" + id + "/image"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Selecting a photo puts it on the public wall, grouped by month")
    void selection_publishesAPhoto() throws Exception {
        long id = uploadPng("trip.png", "Spring trip");

        select(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.publishedPhotos").value(1));

        mvc.perform(get("/api/memory-wall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].key").isNotEmpty())
                .andExpect(jsonPath("$[0].label").isNotEmpty())
                .andExpect(jsonPath("$[0].photos.length()").value(1))
                .andExpect(jsonPath("$[0].photos[0].id").value(id))
                .andExpect(jsonPath("$[0].photos[0].altText").value("Spring trip"));
    }

    @Test
    @DisplayName("A published photo's picture is public, safe and cacheable")
    void publishedPhoto_pictureIsPublic() throws Exception {
        long id = uploadPng("trip.png", null);
        select(id).andExpect(status().isOk());

        mvc.perform(get("/api/memory-wall/photos/" + id + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        org.hamcrest.Matchers.containsString("public")))
                .andExpect(content().bytes(PNG));
    }

    @Test
    @DisplayName("A photo without a description gets a plain fallback for screen readers")
    void publishedPhoto_hasAFallbackAltText() throws Exception {
        long id = uploadPng("trip.png", null);
        select(id).andExpect(status().isOk());

        mvc.perform(get("/api/memory-wall"))
                .andExpect(jsonPath("$[0].photos[0].altText")
                        .value(org.hamcrest.Matchers.startsWith("Memory Wall photo from ")));
    }

    @Test
    @DisplayName("Saving a selection without a photo takes it off the wall")
    void selection_unpublishesWhatIsLeftOut() throws Exception {
        long first = uploadPng("a.png", "First");
        long second = uploadPng("b.png", "Second");
        select(first, second).andExpect(status().isOk());

        select(second).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.publishedPhotos").value(1));

        mvc.perform(get("/api/memory-wall"))
                .andExpect(jsonPath("$[0].photos.length()").value(1))
                .andExpect(jsonPath("$[0].photos[0].id").value(second));

        mvc.perform(get("/api/memory-wall/photos/" + first + "/image"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("An empty selection empties the public wall and marks photos unpublished")
    void selection_emptyClearsTheWall() throws Exception {
        long id = uploadPng("a.png", null);
        select(id).andExpect(status().isOk());

        select().andExpect(status().isOk());

        mvc.perform(get("/api/memory-wall"))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/admin/memory-wall").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(jsonPath("$.photos[0].status").value("UNPUBLISHED"));
    }

    @Test
    @DisplayName("Photos that were never selected stay pending")
    void selection_leavesOtherPhotosPending() throws Exception {
        long chosen = uploadPng("a.png", null);
        uploadPng("b.png", null);

        select(chosen).andExpect(status().isOk());

        mvc.perform(get("/api/admin/memory-wall").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(jsonPath("$.photos[?(@.status=='PENDING_REVIEW')]",
                        org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.photos[?(@.status=='PUBLISHED')]",
                        org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("Selecting a photo that does not exist returns 400 and changes nothing")
    void selection_unknownPhotoIsRefused() throws Exception {
        long id = uploadPng("a.png", null);

        select(id, 999999).andExpect(status().isBadRequest());

        mvc.perform(get("/api/memory-wall"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Repeating a photo in the selection counts it once")
    void selection_ignoresRepeatsAndNulls() throws Exception {
        long id = uploadPng("a.png", null);

        mvc.perform(put("/api/admin/memory-wall/selection")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoIds\":[" + id + "," + id + ",null]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.publishedPhotos").value(1));
    }

    // ------------------------------------------------------ edit and delete

    @Test
    @DisplayName("The admin can change a photo's description")
    void description_canBeChanged() throws Exception {
        long id = uploadPng("a.png", "Old words");

        mvc.perform(put("/api/admin/memory-wall/photos/" + id)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"  New words  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("New words"));
    }

    @Test
    @DisplayName("A description can be removed, and a too-long one is refused")
    void description_canBeClearedButNotTooLong() throws Exception {
        long id = uploadPng("a.png", "Words");

        mvc.perform(put("/api/admin/memory-wall/photos/" + id)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist());

        mvc.perform(put("/api/admin/memory-wall/photos/" + id)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Changing the description of an unknown photo returns 404")
    void description_unknownPhoto() throws Exception {
        mvc.perform(put("/api/admin/memory-wall/photos/999999")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Words\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deleting a photo removes it everywhere")
    void delete_removesThePhoto() throws Exception {
        long id = uploadPng("a.png", null);
        select(id).andExpect(status().isOk());

        mvc.perform(delete("/api/admin/memory-wall/photos/" + id)
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/memory-wall"))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/memory-wall/photos/" + id + "/image"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/memory-wall/photos/" + id + "/image")
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deleting an unknown photo returns 404")
    void delete_unknownPhoto() throws Exception {
        mvc.perform(delete("/api/admin/memory-wall/photos/999999")
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------- admin view

    @Test
    @DisplayName("The admin sees every photo, even pending ones, with the limits")
    void adminOverview_showsPhotosAndLimits() throws Exception {
        long id = uploadPng("a.png", "Pending photo");

        mvc.perform(get("/api/admin/memory-wall").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].id").value(id))
                .andExpect(jsonPath("$.summary.totalPhotos").value(1))
                .andExpect(jsonPath("$.summary.maxPhotos").value(150))
                .andExpect(jsonPath("$.summary.remainingUploads").value(149))
                .andExpect(jsonPath("$.summary.publishedPhotos").value(0))
                .andExpect(jsonPath("$.summary.maxPublishedPhotos").value(120))
                .andExpect(jsonPath("$.summary.storageUsedBytes").value(PNG.length))
                .andExpect(jsonPath("$.summary.maxFileSizeLabel").value("5 MB"))
                .andExpect(jsonPath("$.summary.acceptedFormats").value("JPEG, PNG or WEBP"))
                .andExpect(jsonPath("$.summary.allowedExtensions.length()").value(4));
    }

    @Test
    @DisplayName("The admin can see the picture of a pending photo")
    void adminImage_worksForPendingPhotos() throws Exception {
        long id = uploadPng("a.png", null);

        mvc.perform(get("/api/admin/memory-wall/photos/" + id + "/image")
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(content().bytes(PNG));
    }

    // ------------------------------------------------------ feature switch

    @Test
    @DisplayName("With the feature switched off, visitors see nothing, but the admin can still manage")
    void featureOff_hidesTheWallFromVisitors() throws Exception {
        long id = uploadPng("a.png", "Spring trip");
        select(id).andExpect(status().isOk());

        setFeature(false);

        mvc.perform(get("/api/memory-wall")).andExpect(status().isNotFound());
        mvc.perform(get("/api/memory-wall/photos/" + id + "/image")).andExpect(status().isNotFound());

        mvc.perform(get("/api/admin/memory-wall").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(1));
        upload(photo("b.png", PNG), null).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Switching the feature back on shows the wall again")
    void featureBackOn_showsTheWallAgain() throws Exception {
        long id = uploadPng("a.png", null);
        select(id).andExpect(status().isOk());

        setFeature(false);
        setFeature(true);

        mvc.perform(get("/api/memory-wall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].photos.length()").value(1));
    }

    // ------------------------------------------------------- access rules

    @Test
    @DisplayName("Visitors need no login for the wall and published pictures")
    void publicRoutes_needNoLogin() throws Exception {
        mvc.perform(get("/api/memory-wall")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin routes need a token (401)")
    void adminRoutes_needAToken() throws Exception {
        mvc.perform(get("/api/admin/memory-wall")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/memory-wall/photos/1/image")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/admin/memory-wall/photos").file(photo("a.png", PNG)))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/memory-wall/selection")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"photoIds\":[]}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/memory-wall/photos/1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/memory-wall/photos/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin routes refuse a client (403)")
    void adminRoutes_refuseAClient() throws Exception {
        mvc.perform(get("/api/admin/memory-wall").header(HttpHeaders.AUTHORIZATION, client()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/memory-wall/photos").file(photo("a.png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, client()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/memory-wall/selection")
                        .header(HttpHeaders.AUTHORIZATION, client())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"photoIds\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/memory-wall/photos/1")
                        .header(HttpHeaders.AUTHORIZATION, client()))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ audit log

    @Test
    @DisplayName("Database triggers record every Memory Wall change in the audit log")
    void changes_areWrittenToTheAuditLog() throws Exception {
        long id = uploadPng("a.png", "Words");
        select(id).andExpect(status().isOk());
        mvc.perform(put("/api/admin/memory-wall/photos/" + id)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"New words\"}"))
                .andExpect(status().isOk());
        select().andExpect(status().isOk());
        mvc.perform(delete("/api/admin/memory-wall/photos/" + id)
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNoContent());

        String log = auditLog();

        assertTrue(log.contains("MEMORY_WALL_PHOTO_ADDED"), log);
        assertTrue(log.contains("MEMORY_WALL_PHOTO_PUBLISHED"), log);
        assertTrue(log.contains("MEMORY_WALL_PHOTO_UPDATED"), log);
        assertTrue(log.contains("MEMORY_WALL_PHOTO_UNPUBLISHED"), log);
        assertTrue(log.contains("MEMORY_WALL_PHOTO_DELETED"), log);
    }
}