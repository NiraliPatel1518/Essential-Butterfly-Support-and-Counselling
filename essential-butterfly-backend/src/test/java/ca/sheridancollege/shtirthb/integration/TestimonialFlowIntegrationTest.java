package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.CustomerTestimonialRepository;
import ca.sheridancollege.shtirthb.service.JwtService;

/*
 * Integration tests for the testimonial journey:
 *   client writes a testimonial -> admin approves or rejects it -> admin chooses
 *   where it is shown (public page, home page) -> visitors see only what is allowed.
 *
 * Real application, real PostgreSQL (Testcontainers), real security rules.
 * Tokens come directly from JwtService (logins are covered in
 * AuthFlowIntegrationTest). Every test is rolled back at the end.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///testimonial_flow_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890"
})
@AutoConfigureMockMvc
@Transactional
class TestimonialFlowIntegrationTest {

    private static final String PHOTO = "data:image/png;base64,iVBORw0KGgo=";

    @Autowired MockMvc mvc;
    @Autowired ClientUserRepository clients;
    @Autowired CustomerTestimonialRepository testimonials;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    private String jane;

    @BeforeEach
    void setUp() throws Exception {
        jane = client("jane@mail.com");
        // The website asks for /api/features when it opens. This also creates the
        // default settings (all features on), which public testimonials depend on.
        mvc.perform(get("/api/features")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- helpers

    private ResultMatcher messageContains(String text) {
        return result -> assertTrue(
                result.getResponse().getContentAsString().contains(text),
                result.getResponse().getContentAsString());
    }

    private String client(String email) {
        clients.save(new ClientUser("Jane Lee", email, encoder.encode("Password123")));
        return jwt.generateToken(email, "CLIENT");
    }

    private String admin() {
        return jwt.generateToken("admin@test.com", "ADMIN");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(String name, String content, boolean consent, String photosJson) {
        return String.format(
                "{\"displayName\":\"%s\",\"content\":\"%s\",\"publicationConsent\":%s,\"photos\":%s}",
                name, content, consent, photosJson);
    }

    private ResultActions submit(String token, String json) throws Exception {
        return mvc.perform(post("/api/testimonials")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void submitValid(String token, String name) throws Exception {
        submit(token, json(name, "Casey was a great support for our family.", true, "[]"))
                .andExpect(status().isCreated());
    }

    private long idOf(String displayName) {
        return testimonials.findAll().stream()
                .filter(t -> t.getDisplayName().equals(displayName))
                .findFirst().orElseThrow().getId();
    }

    private ResultActions adminPost(String path) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer(admin())));
    }

    private ResultActions approve(long id) throws Exception {
        return adminPost("/api/admin/testimonials/" + id + "/approve");
    }

    private ResultActions display(long id, boolean onPublic, boolean onHome) throws Exception {
        return mvc.perform(put("/api/admin/testimonials/" + id + "/display")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"displayOnPublic\":%s,\"featuredOnHome\":%s}", onPublic, onHome)));
    }

    /** Submits a testimonial and makes it visible on the public page. */
    private long publishedTestimonial(String name, boolean onHome) throws Exception {
        submitValid(jane, name);
        long id = idOf(name);
        approve(id).andExpect(status().isOk());
        display(id, true, onHome).andExpect(status().isOk());
        return id;
    }

    private ResultActions publicList() throws Exception {
        return mvc.perform(get("/api/testimonials/public"));
    }

    private ResultActions featuredList() throws Exception {
        return mvc.perform(get("/api/testimonials/featured"));
    }

    // ----------------------------------------------------------- client submits

    @Test
    void submit_withoutLoggingIn_isUnauthorized() throws Exception {
        mvc.perform(post("/api/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Jane", "Casey was a great support.", true, "[]")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submit_savesTheTestimonialAsPendingAndHidesPrivateDetails() throws Exception {
        submit(jane, json("Jane L.", "Casey was a great support for our family.", true, "[]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Jane L."))
                .andExpect(jsonPath("$.ownerEmail").doesNotExist());

        assertEquals("PENDING", testimonials.findAll().get(0).getStatus());
    }

    @Test
    void submit_withoutPublicationConsent_isRefused() throws Exception {
        submit(jane, json("Jane L.", "Casey was a great support.", false, "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("Publication consent is required."));

        assertEquals(0, testimonials.count());
    }

    @Test
    void submit_withATextThatIsTooShort_isRefused() throws Exception {
        submit(jane, json("Jane L.", "Too short", true, "[]")).andExpect(status().isBadRequest());
    }

    @Test
    void submit_withoutADisplayName_isRefused() throws Exception {
        submit(jane, json("  ", "Casey was a great support.", true, "[]")).andExpect(status().isBadRequest());
    }

    @Test
    void submit_withAPhoto_keepsThePhoto() throws Exception {
        submit(jane, json("Jane L.", "Casey was a great support.", true, "[\"" + PHOTO + "\"]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photos.length()").value(1));
    }

    @Test
    void submit_withMoreThanThreePhotos_isRefused() throws Exception {
        String photos = "[\"" + PHOTO + "\",\"" + PHOTO + "\",\"" + PHOTO + "\",\"" + PHOTO + "\"]";

        submit(jane, json("Jane L.", "Casey was a great support.", true, photos))
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("up to 3 photos"));
    }

    @Test
    void submit_withAFileThatIsNotAnImage_isRefused() throws Exception {
        String photos = "[\"data:application/pdf;base64,AAAA\"]";

        submit(jane, json("Jane L.", "Casey was a great support.", true, photos))
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("JPG, PNG or WEBP"));
    }

    @Test
    void mine_showsOnlyTheClientsOwnTestimonials() throws Exception {
        String sam = client("sam@mail.com");
        submitValid(jane, "Jane L.");
        submitValid(sam, "Sam K.");

        mvc.perform(get("/api/testimonials/mine").header(HttpHeaders.AUTHORIZATION, bearer(jane)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].displayName").value("Jane L."));
    }

    // ------------------------------------------------------------ admin reviews

    @Test
    void adminList_isClosedWithoutATokenAndToClients() throws Exception {
        mvc.perform(get("/api/admin/testimonials")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/testimonials").header(HttpHeaders.AUTHORIZATION, bearer(jane)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminList_showsPendingTestimonialsWithTheOwnerEmail() throws Exception {
        submitValid(jane, "Jane L.");

        mvc.perform(get("/api/admin/testimonials").header(HttpHeaders.AUTHORIZATION, bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].ownerEmail").value("jane@mail.com"));
    }

    @Test
    void aClient_cannotApproveTestimonials() throws Exception {
        submitValid(jane, "Jane L.");

        mvc.perform(post("/api/admin/testimonials/" + idOf("Jane L.") + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jane)))
                .andExpect(status().isForbidden());

        assertEquals("PENDING", testimonials.findAll().get(0).getStatus());
    }

    @Test
    void approving_aTestimonialThatDoesNotExist_isNotFound() throws Exception {
        approve(999999L).andExpect(status().isNotFound());
    }

    // ------------------------------------------------- what visitors can see

    @Test
    void publicPage_showsNothingWhileTestimonialsArePending() throws Exception {
        submitValid(jane, "Jane L.");

        publicList().andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void approving_doesNotShowItYet_untilTheAdminChoosesToDisplayIt() throws Exception {
        submitValid(jane, "Jane L.");
        long id = idOf("Jane L.");

        approve(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        publicList().andExpect(jsonPath("$.length()").value(0));

        display(id, true, false).andExpect(status().isOk());
        publicList()
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].displayName").value("Jane L."))
                .andExpect(jsonPath("$[0].ownerEmail").doesNotExist());
    }

    @Test
    void displaying_aTestimonialThatIsNotApproved_isRefused() throws Exception {
        submitValid(jane, "Jane L.");

        display(idOf("Jane L."), true, false)
                .andExpect(status().isConflict())
                .andExpect(messageContains("Only approved testimonials"));
    }

    @Test
    void rejecting_keepsATestimonialOffThePublicPage() throws Exception {
        submitValid(jane, "Jane L.");

        adminPost("/api/admin/testimonials/" + idOf("Jane L.") + "/reject")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        publicList().andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejecting_aTestimonialThatWasPublic_removesItFromThePublicPage() throws Exception {
        long id = publishedTestimonial("Jane L.", false);
        publicList().andExpect(jsonPath("$.length()").value(1));

        adminPost("/api/admin/testimonials/" + id + "/reject").andExpect(status().isOk());

        publicList().andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void removing_takesATestimonialOffTheWebsite() throws Exception {
        long id = publishedTestimonial("Jane L.", true);

        adminPost("/api/admin/testimonials/" + id + "/remove")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"));

        publicList().andExpect(jsonPath("$.length()").value(0));
        featuredList().andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------- home page

    @Test
    void featuredTestimonial_appearsOnTheHomePage() throws Exception {
        publishedTestimonial("Jane L.", true);

        featuredList()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].displayName").value("Jane L."));
    }

    @Test
    void featuring_aTestimonialThatIsNotPublic_isRefused() throws Exception {
        submitValid(jane, "Jane L.");
        long id = idOf("Jane L.");
        approve(id).andExpect(status().isOk());

        display(id, false, true)
                .andExpect(status().isConflict())
                .andExpect(messageContains("must also be public"));
    }

    @Test
    void onlyTwoTestimonials_canBeFeaturedOnTheHomePage() throws Exception {
        publishedTestimonial("First", true);
        publishedTestimonial("Second", true);
        submitValid(jane, "Third");
        long third = idOf("Third");
        approve(third).andExpect(status().isOk());

        display(third, true, true)
                .andExpect(status().isConflict())
                .andExpect(messageContains("Only two testimonials"));

        featuredList().andExpect(jsonPath("$.length()").value(2));
    }

    // ------------------------------------------------- website feature switch

    @Test
    void whenTheAdminTurnsTestimonialsOff_visitorsSeeNone() throws Exception {
        publishedTestimonial("Jane L.", true);
        publicList().andExpect(jsonPath("$.length()").value(1));

        mvc.perform(put("/api/admin/features")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"featureKey\":\"NEWSLETTER\",\"enabled\":true},"
                                + "{\"featureKey\":\"TESTIMONIALS\",\"enabled\":false},"
                                + "{\"featureKey\":\"UPDATES\",\"enabled\":true},"
                                + "{\"featureKey\":\"APPOINTMENTS\",\"enabled\":true},"
                                + "{\"featureKey\":\"MEMORY_WALL\",\"enabled\":true}]"))
                .andExpect(status().isOk());

        publicList().andExpect(jsonPath("$.length()").value(0));
        featuredList().andExpect(jsonPath("$.length()").value(0));
    }
}