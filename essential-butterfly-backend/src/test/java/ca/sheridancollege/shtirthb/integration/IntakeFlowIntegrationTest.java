package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import ca.sheridancollege.shtirthb.repository.IntakeSubmissionRepository;
import ca.sheridancollege.shtirthb.service.JwtService;

/*
 * Integration tests for the intake journey:
 *   client sends an intake request -> admin opens it -> admin accepts or rejects
 *   -> the client sees the decision.
 *
 * Real application, real PostgreSQL (Testcontainers), real security rules.
 * The logins are covered in AuthFlowIntegrationTest, so here the clients and
 * the admin get their tokens directly from JwtService. Every test is rolled
 * back at the end.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///intake_flow_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890"
})
@AutoConfigureMockMvc
@Transactional
class IntakeFlowIntegrationTest {

    private static final String EMAIL = "jane@mail.com";

    private static final String INTAKE_JSON = """
            {"clientFullName":"Alex Lee",
             "parentGuardianName":"Jane Lee",
             "contactInfo":"jane@mail.com",
             "preferredContactMethod":"Email",
             "overview":"Looking for weekly support.",
             "supportNeeds":"Respite care",
             "respiteGoals":"Two evenings a week",
             "subjectFocus":"Autism",
             "additionalNotes":"None"}
            """;

    @Autowired MockMvc mvc;
    @Autowired ClientUserRepository clients;
    @Autowired IntakeSubmissionRepository intakes;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    // ---------------------------------------------------------------- helpers

    private ResultMatcher messageContains(String text) {
        return result -> assertTrue(
                result.getResponse().getContentAsString().contains(text),
                result.getResponse().getContentAsString());
    }

    /** A client who can already log in. The phone is verified only when asked. */
    private String client(String email, boolean phoneVerified) {
        ClientUser user = new ClientUser("Jane Lee", email, encoder.encode("Password123"));
        user.setPhoneVerified(phoneVerified);
        clients.save(user);
        return jwt.generateToken(email, "CLIENT");
    }

    private String admin() {
        return jwt.generateToken("admin@test.com", "ADMIN");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private ResultActions submit(String token, String json) throws Exception {
        return mvc.perform(post("/api/intake")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions clientStatus(String token) throws Exception {
        return mvc.perform(get("/api/intake/status").header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private long onlySubmissionId() {
        assertEquals(1, intakes.count());
        return intakes.findAll().get(0).getId();
    }

    private ResultActions adminGet(String path) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(admin())));
    }

    private ResultActions accept(String token, long id) throws Exception {
        return mvc.perform(post("/api/admin/intake-submissions/" + id + "/accept")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions reject(String token, long id, String reason) throws Exception {
        return mvc.perform(post("/api/admin/intake-submissions/" + id + "/reject")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"rejectionReason\":\"%s\"}", reason)));
    }

    // ------------------------------------------------------------ client side

    @Test
    void submit_withoutLoggingIn_isUnauthorized() throws Exception {
        mvc.perform(post("/api/intake").contentType(MediaType.APPLICATION_JSON).content(INTAKE_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submit_beforeThePhoneIsVerified_isRefused() throws Exception {
        String token = client(EMAIL, false);

        submit(token, INTAKE_JSON)
                .andExpect(status().isConflict())
                .andExpect(messageContains("verify your phone number"));

        assertEquals(0, intakes.count());
    }

    @Test
    void submit_withAMissingRequiredField_isRejected() throws Exception {
        String token = client(EMAIL, true);

        submit(token, INTAKE_JSON.replace("\"overview\":\"Looking for weekly support.\"", "\"overview\":\"\""))
                .andExpect(status().isBadRequest());

        assertEquals(0, intakes.count());
    }

    @Test
    void submit_savesTheRequestAndTheClientSeesItAsSubmitted() throws Exception {
        String token = client(EMAIL, true);

        submit(token, INTAKE_JSON)
                .andExpect(status().isOk())
                .andExpect(messageContains("received successfully"));

        assertEquals(1, intakes.count());
        clientStatus(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void submit_whileAnotherRequestIsUnderReview_isRefused() throws Exception {
        String token = client(EMAIL, true);
        submit(token, INTAKE_JSON).andExpect(status().isOk());

        submit(token, INTAKE_JSON)
                .andExpect(status().isConflict())
                .andExpect(messageContains("already have an intake request"));

        assertEquals(1, intakes.count());
    }

    @Test
    void status_forAClientWhoNeverSubmitted_isNotFound() throws Exception {
        String token = client(EMAIL, true);

        clientStatus(token).andExpect(status().isNotFound());
    }

    @Test
    void status_showsOnlyTheClientsOwnRequest() throws Exception {
        String jane = client(EMAIL, true);
        String sam = client("sam@mail.com", true);
        submit(jane, INTAKE_JSON).andExpect(status().isOk());

        clientStatus(sam).andExpect(status().isNotFound());
        clientStatus(jane).andExpect(status().isOk());
    }

    // -------------------------------------------------------------- admin side

    @Test
    void adminList_isClosedWithoutAToken() throws Exception {
        mvc.perform(get("/api/admin/intake-submissions")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminList_isClosedToClients() throws Exception {
        String token = client(EMAIL, true);

        mvc.perform(get("/api/admin/intake-submissions").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminList_showsTheSubmittedRequest() throws Exception {
        submit(client(EMAIL, true), INTAKE_JSON).andExpect(status().isOk());

        adminGet("/api/admin/intake-submissions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clientFullName").value("Alex Lee"))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
    }

    @Test
    void adminOpeningARequest_movesItToInProgress() throws Exception {
        String token = client(EMAIL, true);
        submit(token, INTAKE_JSON).andExpect(status().isOk());

        adminGet("/api/admin/intake-submissions/" + onlySubmissionId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        clientStatus(token).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void adminAccepting_updatesWhatTheClientSees() throws Exception {
        String token = client(EMAIL, true);
        submit(token, INTAKE_JSON).andExpect(status().isOk());

        accept(admin(), onlySubmissionId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        clientStatus(token).andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void adminAccepting_aRequestThatWasAlreadyDecided_isRefused() throws Exception {
        submit(client(EMAIL, true), INTAKE_JSON).andExpect(status().isOk());
        long id = onlySubmissionId();
        accept(admin(), id).andExpect(status().isOk());

        accept(admin(), id)
                .andExpect(status().isConflict())
                .andExpect(messageContains("already been decided"));
    }

    @Test
    void adminRejecting_withoutAReason_isRefused() throws Exception {
        submit(client(EMAIL, true), INTAKE_JSON).andExpect(status().isOk());

        reject(admin(), onlySubmissionId(), "").andExpect(status().isBadRequest());
    }

    @Test
    void adminRejecting_showsTheReasonToTheClient() throws Exception {
        String token = client(EMAIL, true);
        submit(token, INTAKE_JSON).andExpect(status().isOk());

        reject(admin(), onlySubmissionId(), "We are not taking new clients this month.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        clientStatus(token)
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("We are not taking new clients this month."));
    }

    @Test
    void aRejectedClient_canSubmitANewRequest() throws Exception {
        String token = client(EMAIL, true);
        submit(token, INTAKE_JSON).andExpect(status().isOk());
        reject(admin(), onlySubmissionId(), "Not now.").andExpect(status().isOk());

        submit(token, INTAKE_JSON).andExpect(status().isOk());

        assertEquals(2, intakes.count());
    }

    @Test
    void adminDecision_onARequestThatDoesNotExist_isNotFound() throws Exception {
        accept(admin(), 999999L).andExpect(status().isNotFound());
    }

    @Test
    void aClient_cannotAcceptRequests() throws Exception {
        String token = client(EMAIL, true);
        submit(token, INTAKE_JSON).andExpect(status().isOk());

        accept(token, onlySubmissionId()).andExpect(status().isForbidden());

        clientStatus(token).andExpect(jsonPath("$.status").value("SUBMITTED"));
    }
}