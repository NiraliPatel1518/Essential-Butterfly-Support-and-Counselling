package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.WebsiteUpdate;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.WebsiteUpdateRepository;
import ca.sheridancollege.shtirthb.service.JwtService;

/*
 * Integration tests for website updates:
 *   admin writes an update -> publishes it -> visitors see the title and a
 *   preview -> logged-in users can read the full update.
 *   Drafts stay hidden. The admin can edit, unpublish and delete.
 *
 * Real application, real PostgreSQL (Testcontainers), real security rules.
 * Every test is rolled back at the end.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///website_update_flow_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890"
})
@AutoConfigureMockMvc
@Transactional
class WebsiteUpdateFlowIntegrationTest {

    private static final String PHOTO = "data:image/png;base64,iVBORw0KGgo=";

    @Autowired MockMvc mvc;
    @Autowired WebsiteUpdateRepository updates;
    @Autowired ClientUserRepository clients;
    @Autowired JwtService jwt;

    @BeforeEach
    void setUp() throws Exception {
        // The website asks for /api/features when it opens. This also creates the
        // default settings (all features on), which public updates depend on.
        mvc.perform(get("/api/features")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- helpers

    private String admin() {
        return "Bearer " + jwt.generateToken("admin@test.com", "ADMIN");
    }

    private String clientToken() {
        if (clients.findByEmail("jane@mail.com").isEmpty()) {
            clients.save(new ClientUser("Jane Lee", "jane@mail.com", "hash"));
        }
        return "Bearer " + jwt.generateToken("jane@mail.com", "CLIENT");
    }

    private String json(String title, String content, String published,
                        String photosJson, String linksJson) {
        return String.format(
                "{\"title\":\"%s\",\"content\":\"%s\",\"published\":%s,\"photos\":%s,\"socialLinks\":%s}",
                title, content, published, photosJson, linksJson);
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/admin/updates")
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void createUpdate(String title, boolean published) throws Exception {
        create(json(title, "Some news for everyone.", String.valueOf(published), "[]", "[]"))
                .andExpect(status().isCreated());
    }

    private long idOf(String title) {
        return updates.findAll().stream()
                .filter(u -> u.getTitle().equals(title))
                .findFirst().orElseThrow().getId();
    }

    private ResultActions edit(long id, String json) throws Exception {
        return mvc.perform(put("/api/admin/updates/" + id)
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions publicList() throws Exception {
        return mvc.perform(get("/api/updates"));
    }

    // ------------------------------------------------------------- admin creates

    @Test
    void admin_canCreateAnUpdate() throws Exception {
        create(json("  Summer news  ", "  We have new hours.  ", "true", "[\"" + PHOTO + "\"]",
                "[\"https://instagram.com/butterfly\"]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Summer news"))
                .andExpect(jsonPath("$.content").value("We have new hours."))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.socialLinks[0]").value("https://instagram.com/butterfly"));

        assertEquals(1, updates.count());
    }

    @Test
    void creatingNeedsAnAdminLogin() throws Exception {
        String body = json("Summer news", "News.", "true", "[]", "[]");

        mvc.perform(post("/api/admin/updates")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/admin/updates")
                        .header(HttpHeaders.AUTHORIZATION, clientToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        assertEquals(0, updates.count());
    }

    @Test
    void anUpdateNeedsATitleContentAndPublishingStatus() throws Exception {
        create(json("", "News.", "true", "[]", "[]")).andExpect(status().isBadRequest());
        create(json("Title", "", "true", "[]", "[]")).andExpect(status().isBadRequest());
        create("{\"title\":\"Title\",\"content\":\"News.\"}").andExpect(status().isBadRequest());

        assertEquals(0, updates.count());
    }

    @Test
    void aTitleOver180Characters_isRejected() throws Exception {
        create(json("a".repeat(181), "News.", "true", "[]", "[]"))
                .andExpect(status().isBadRequest());

        assertEquals(0, updates.count());
    }

    @Test
    void onlyFivePhotosAreAllowed() throws Exception {
        String photos = "[" + String.join(",", Collections.nCopies(6, "\"" + PHOTO + "\"")) + "]";

        create(json("Title", "News.", "true", photos, "[]")).andExpect(status().isBadRequest());

        assertEquals(0, updates.count());
    }

    @Test
    void photosMustBeJpegPngOrWebp() throws Exception {
        create(json("Title", "News.", "true",
                "[\"data:image/gif;base64,R0lGODlh\"]", "[]"))
                .andExpect(status().isBadRequest());

        create(json("Title", "News.", "true", "[\"not-a-photo\"]", "[]"))
                .andExpect(status().isBadRequest());

        assertEquals(0, updates.count());
    }

    @Test
    void onlyFiveLinksAreAllowed() throws Exception {
        String links = "[" + String.join(",",
                Collections.nCopies(6, "\"https://example.com\"")) + "]";

        create(json("Title", "News.", "true", "[]", links)).andExpect(status().isBadRequest());

        assertEquals(0, updates.count());
    }

    @Test
    void linksMustStartWithHttpOrHttps() throws Exception {
        create(json("Title", "News.", "true", "[]", "[\"ftp://example.com\"]"))
                .andExpect(status().isBadRequest());

        create(json("Title", "News.", "true", "[]", "[\"javascript:alert(1)\"]"))
                .andExpect(status().isBadRequest());

        assertEquals(0, updates.count());
    }

    // -------------------------------------------------------- what visitors see

    @Test
    void visitors_seeOnlyPublishedUpdates() throws Exception {
        createUpdate("Public news", true);
        createUpdate("Secret draft", false);

        publicList().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Public news"));
    }

    @Test
    void thePublicListShowsAPreviewNotTheFullText() throws Exception {
        create(json("Long news", "a".repeat(250), "true", "[]", "[]"))
                .andExpect(status().isCreated());

        publicList().andExpect(jsonPath("$[0].preview").value("a".repeat(200) + "..."))
                .andExpect(jsonPath("$[0].content").doesNotExist());
    }

    @Test
    void theCoverImageIsTheFirstPhoto() throws Exception {
        create(json("With photo", "News.", "true", "[\"" + PHOTO + "\"]", "[]"))
                .andExpect(status().isCreated());
        // Updates created in the same millisecond have the same time, so wait a little.
        Thread.sleep(25);
        create(json("Without photo", "News.", "true", "[]", "[]"))
                .andExpect(status().isCreated());

        publicList().andExpect(jsonPath("$.length()").value(2));

        // The list is newest first, so "Without photo" comes first.
        publicList().andExpect(jsonPath("$[0].coverImage").doesNotExist())
                .andExpect(jsonPath("$[1].coverImage").value(PHOTO));
    }

    @Test
    void theFullUpdate_needsALogin() throws Exception {
        createUpdate("Public news", true);
        long id = idOf("Public news");

        mvc.perform(get("/api/updates/" + id)).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/updates/" + id)
                        .header(HttpHeaders.AUTHORIZATION, clientToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Public news"))
                .andExpect(jsonPath("$.content").value("Some news for everyone."));
    }

    @Test
    void aDraft_cannotBeOpenedByAClient() throws Exception {
        createUpdate("Secret draft", false);
        long id = idOf("Secret draft");

        mvc.perform(get("/api/updates/" + id)
                        .header(HttpHeaders.AUTHORIZATION, clientToken()))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- admin list

    @Test
    void admin_seesDraftsToo() throws Exception {
        createUpdate("Public news", true);
        createUpdate("Secret draft", false);

        mvc.perform(get("/api/admin/updates").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void theAdminList_isNotForVisitorsOrClients() throws Exception {
        mvc.perform(get("/api/admin/updates")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/updates")
                        .header(HttpHeaders.AUTHORIZATION, clientToken()))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------- edit and publish

    @Test
    void admin_canEditAnUpdate() throws Exception {
        createUpdate("Old title", true);
        long id = idOf("Old title");

        edit(id, json("New title", "New text.", "true", "[]", "[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"));

        WebsiteUpdate saved = updates.findById(id).orElseThrow();
        assertEquals("New title", saved.getTitle());
        assertEquals("New text.", saved.getContent());
    }

    @Test
    void publishingADraft_makesItVisible() throws Exception {
        createUpdate("Soon public", false);
        long id = idOf("Soon public");
        publicList().andExpect(jsonPath("$.length()").value(0));

        edit(id, json("Soon public", "Some news for everyone.", "true", "[]", "[]"))
                .andExpect(status().isOk());

        publicList().andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void unpublishing_hidesItAgain() throws Exception {
        createUpdate("Public news", true);
        long id = idOf("Public news");
        publicList().andExpect(jsonPath("$.length()").value(1));

        edit(id, json("Public news", "Some news for everyone.", "false", "[]", "[]"))
                .andExpect(status().isOk());

        publicList().andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/updates/" + id)
                        .header(HttpHeaders.AUTHORIZATION, clientToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void editingAMissingUpdate_isNotFound() throws Exception {
        edit(999999, json("Title", "News.", "true", "[]", "[]"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEditWithABadPhoto_isRejected() throws Exception {
        createUpdate("Old title", true);
        long id = idOf("Old title");

        // In the real app the failed edit is rolled back. These tests run inside
        // one big transaction, so only the answer to the admin is checked here.
        edit(id, json("New title", "New text.", "true", "[\"not-a-photo\"]", "[]"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------- delete

    @Test
    void admin_canDeleteAnUpdate() throws Exception {
        createUpdate("Old news", true);
        long id = idOf("Old news");

        mvc.perform(delete("/api/admin/updates/" + id)
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNoContent());

        assertFalse(updates.findById(id).isPresent());
        publicList().andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingAMissingUpdate_isNotFound() throws Exception {
        mvc.perform(delete("/api/admin/updates/999999")
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleting_needsAnAdminLogin() throws Exception {
        createUpdate("Old news", true);
        long id = idOf("Old news");

        mvc.perform(delete("/api/admin/updates/" + id))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/admin/updates/" + id)
                        .header(HttpHeaders.AUTHORIZATION, clientToken()))
                .andExpect(status().isForbidden());

        assertEquals(1, updates.count());
    }

    // ----------------------------------------------------- website feature switch

    @Test
    void whenTheAdminTurnsUpdatesOff_visitorsAndClientsSeeNone() throws Exception {
        createUpdate("Public news", true);
        long id = idOf("Public news");
        publicList().andExpect(jsonPath("$.length()").value(1));

        mvc.perform(put("/api/admin/features")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"featureKey\":\"NEWSLETTER\",\"enabled\":true},"
                                + "{\"featureKey\":\"TESTIMONIALS\",\"enabled\":true},"
                                + "{\"featureKey\":\"UPDATES\",\"enabled\":false}]"))
                .andExpect(status().isOk());

        publicList().andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/updates/" + id)
                        .header(HttpHeaders.AUTHORIZATION, clientToken()))
                .andExpect(status().isNotFound());

        // The admin still sees everything.
        mvc.perform(get("/api/admin/updates").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(jsonPath("$.length()").value(1));
    }
}