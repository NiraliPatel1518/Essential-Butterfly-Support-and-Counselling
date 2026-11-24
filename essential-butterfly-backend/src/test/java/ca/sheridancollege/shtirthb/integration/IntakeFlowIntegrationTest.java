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
        "spring.jpa.hibernate.ddl-auto=validate",
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
        if (phoneVerified) {
            int suffix = Math.floorMod(email.hashCode(), 10_000_000);
            user.setPhoneNumber("+1416" + String.format(java.util.Locale.ROOT, "%07d", suffix));
        }
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

    /*
     * Checks: An intake form cannot be sent without logging in.
     * How:    We post the form with no token. The answer is 401.
     */
    @Test
    void submit_withoutLoggingIn_isUnauthorized() throws Exception {
        mvc.perform(post("/api/intake").contentType(MediaType.APPLICATION_JSON).content(INTAKE_JSON))
                .andExpect(status().isUnauthorized());
    }

    /*
     * Checks: A client with an unverified phone cannot submit the intake.
     * How:    The client has no verified phone. The answer is 409 with a message about verifying the
     *   phone number, and nothing is saved.
     */
    @Test
    void submit_beforeThePhoneIsVerified_isRefused() throws Exception {
        String token = client(EMAIL, false);

        submit(token, INTAKE_JSON)
                .andExpect(status().isConflict())
                .andExpect(messageContains("verify your phone number"));

        assertEquals(0, intakes.count());
    }

    /*
     * Checks: A missing required field is refused.
     * How:    We empty the overview field. The answer is 400, and nothing is saved.
     */
    @Test
    void submit_withAMissingRequiredField_isRejected() throws Exception {
        String token = client(EMAIL, true);

        submit(token, INTAKE_JSON.replace("\"overview\":\"Looking for weekly support.\"", "\"overview\":\"\""))
                .andExpect(status().isBadRequest());

        assertEquals(0, intakes.count());
    }

    /*
     * Checks: A valid intake is saved and the client sees the status SUBMITTED.
     * How:    The answer says received successfully. The database has 1 request, and the status URL
     *   shows SUBMITTED.
     */
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

    /*
     * Checks: A second intake while the first one is waiting is refused.
     * How:    The second submit gives 409 with "already have an intake request", and the database
     *   still has only 1.
     */
    @Test
    void submit_whileAnotherRequestIsUnderReview_isRefused() throws Exception {
        String token = client(EMAIL, true);
        submit(token, INTAKE_JSON).andExpect(status().isOk());

        submit(token, INTAKE_JSON)
                .andExpect(status().isConflict())
                .andExpect(messageContains("already have an intake request"));

        assertEquals(1, intakes.count());
    }

    /*
     * Checks: A client who never submitted gets 404 for the status.
     * How:    We ask the status URL with no request saved.
     */
    @Test
    void status_forAClientWhoNeverSubmitted_isNotFound() throws Exception {
        String token = client(EMAIL, true);

        clientStatus(token).andExpect(status().isNotFound());
    }

    /*
     * Checks: A client sees only their own request.
     * How:    Jane submits. Sam gets 404 for the status, and Jane gets 200.
     */
    @Test
    void status_showsOnlyTheClientsOwnRequest() throws Exception {
        String jane = client(EMAIL, true);
        String sam = client("sam@mail.com", true);
        submit(jane, INTAKE_JSON).andExpect(status().isOk());

        clientStatus(sam).andExpect(status().isNotFound());
        clientStatus(jane).andExpect(status().isOk());
    }

    // -------------------------------------------------------------- admin side

    /*
     * Checks: The admin list needs a token.
     * How:    No token gives 401.
     */
    @Test
    void adminList_isClosedWithoutAToken() throws Exception {
        mvc.perform(get("/api/admin/intake-submissions")).andExpect(status().isUnauthorized());
    }

    /*
     * Checks: A client cannot open the admin list.
     * How:    A client token gives 403.
     */
    @Test
    void adminList_isClosedToClients() throws Exception {
        String token = client(EMAIL, true);

        mvc.perform(get("/api/admin/intake-submissions").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    /*
     * Checks: The admin list shows the submitted request.
     * How:    After a client submits, the admin list has 1 item with the client's name and status
     *   SUBMITTED.
     */
    @Test
    void adminList_showsTheSubmittedRequest() throws Exception {
        submit(client(EMAIL, true), INTAKE_JSON).andExpect(status().isOk());

        adminGet("/api/admin/intake-submissions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clientFullName").value("Alex Lee"))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
    }

    /*
     * Checks: When the admin opens a request, it moves to IN_PROGRESS.
     * How:    The admin opens it by id. The answer shows IN_PROGRESS, and the client's status URL
     *   shows IN_PROGRESS as well.
     */
    @Test
    void adminOpeningARequest_movesItToInProgress() throws Exception {
        String token = client(EMAIL, true);
        submit(token, INTAKE_JSON).andExpect(status().isOk());

        adminGet("/api/admin/intake-submissions/" + onlySubmissionId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        clientStatus(token).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    /*
     * Checks: When the admin accepts, the client sees ACCEPTED.
     * How:    The accept URL answers 200 with ACCEPTED, and the client's status is ACCEPTED.
     */
    @Test
    void adminAccepting_updatesWhatTheClientSees() throws Exception {
        String token = client(EMAIL, true);
        submit(token, INTAKE_JSON).andExpect(status().isOk());

        accept(admin(), onlySubmissionId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        clientStatus(token).andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    /*
     * Checks: A request that was already decided cannot be accepted again.
     * How:    The second accept gives 409 with the message "already been decided".
     */
    @Test
    void adminAccepting_aRequestThatWasAlreadyDecided_isRefused() throws Exception {
        submit(client(EMAIL, true), INTAKE_JSON).andExpect(status().isOk());
        long id = onlySubmissionId();
        accept(admin(), id).andExpect(status().isOk());

        accept(admin(), id)
                .andExpect(status().isConflict())
                .andExpect(messageContains("already been decided"));
    }

    /*
     * Checks: Rejecting needs a reason.
     * How:    An empty reason gives 400.
     */
    @Test
    void adminRejecting_withoutAReason_isRefused() throws Exception {
        submit(client(EMAIL, true), INTAKE_JSON).andExpect(status().isOk());

        reject(admin(), onlySubmissionId(), "").andExpect(status().isBadRequest());
    }

    /*
     * Checks: A rejection shows its reason to the client.
     * How:    The admin rejects with a reason. The client's status is REJECTED and contains that
     *   reason.
     */
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

    /*
     * Checks: A rejected client can send a new request.
     * How:    After a rejection, a new submit gives 200 and the database has 2 requests.
     */
    @Test
    void aRejectedClient_canSubmitANewRequest() throws Exception {
        String token = client(EMAIL, true);
        submit(token, INTAKE_JSON).andExpect(status().isOk());
        reject(admin(), onlySubmissionId(), "Not now.").andExpect(status().isOk());

        submit(token, INTAKE_JSON).andExpect(status().isOk());

        assertEquals(2, intakes.count());
    }

    /*
     * Checks: A decision on a request that does not exist gives 404.
     * How:    We accept id 999999 as the admin.
     */
    @Test
    void adminDecision_onARequestThatDoesNotExist_isNotFound() throws Exception {
        accept(admin(), 999999L).andExpect(status().isNotFound());
    }

    /*
     * Checks: A client cannot accept requests.
     * How:    A client token on the accept URL gives 403, and the request is still SUBMITTED.
     */
    @Test
    void aClient_cannotAcceptRequests() throws Exception {
        String token = client(EMAIL, true);
        submit(token, INTAKE_JSON).andExpect(status().isOk());

        accept(token, onlySubmissionId()).andExpect(status().isForbidden());

        clientStatus(token).andExpect(jsonPath("$.status").value("SUBMITTED"));
    }
}