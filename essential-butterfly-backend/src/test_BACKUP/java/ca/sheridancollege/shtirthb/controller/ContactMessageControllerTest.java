package ca.sheridancollege.shtirthb.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ca.sheridancollege.shtirthb.service.ContactMessageService;

/**
 * UC-02: HTTP-level tests for POST /api/contact
 *
 * This file has 5 tests:
 *   Test 1 - Valid message -> 200 and success text
 *   Test 2 - Invalid email -> 400, message is not saved
 *   Test 3 - Empty subject -> 400, message is not saved
 *   Test 4 - Empty message -> 400, message is not saved
 *   Test 5 - Service error -> 400 with the error text
 *
 * No login token is needed because /api/contact is public.
 */
class ContactMessageControllerTest {

    private ContactMessageService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ContactMessageService.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new ContactMessageController(service))
                .build();
    }

    private static final String VALID_MESSAGE = """
            {"fullName":"Jane Doe","email":"jane@mail.com",
             "subject":"Question","message":"Hello there"}
            """;

    /*
     * TEST 1: Valid contact message
     */
    @Test
    void submit_returns200_whenDataIsValid() throws Exception {
        mvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MESSAGE))
                .andExpect(status().isOk())
                .andExpect(content().string("Your message has been submitted successfully"));

        verify(service).submitMessage(any());
    }

    /*
     * TEST 2: Email format is wrong
     */
    @Test
    void submit_returns400_whenEmailInvalid() throws Exception {
        mvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane","email":"not-an-email",
                                 "subject":"Question","message":"Hello"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    /*
     * TEST 3: Subject is empty
     */
    @Test
    void submit_returns400_whenSubjectEmpty() throws Exception {
        mvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane","email":"jane@mail.com",
                                 "subject":"","message":"Hello"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    /*
     * TEST 4: Message is empty
     */
    @Test
    void submit_returns400_whenMessageEmpty() throws Exception {
        mvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane","email":"jane@mail.com",
                                 "subject":"Question","message":"   "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    /*
     * TEST 5: Service throws an error
     */
    @Test
    void submit_returns400_withMessage_whenServiceRejects() throws Exception {
        doThrow(new IllegalArgumentException("Something went wrong"))
                .when(service).submitMessage(any());

        mvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MESSAGE))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Something went wrong"));
    }
}
