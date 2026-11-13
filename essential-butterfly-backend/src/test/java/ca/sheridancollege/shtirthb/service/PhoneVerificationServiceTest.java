package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ca.sheridancollege.shtirthb.dto.PhoneNumberRequest;
import ca.sheridancollege.shtirthb.dto.PhoneVerificationStatusResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

    private static final String EMAIL = "jane@mail.com";
    private static final String PHONE = "+14165551234";

    @Mock
    private ClientUserRepository users;

    private PhoneVerificationService service;
    private ClientUser user;

    @BeforeEach
    void setUp() {
        // Empty credentials intentionally exercise the safe, unconfigured CI path.
        service = new PhoneVerificationService(users, "", "", "", "https://api.twilio.com");
        user = new ClientUser("Jane Doe", EMAIL, "ENCODED");
    }

    private PhoneNumberRequest phoneRequest(String number) {
        PhoneNumberRequest request = new PhoneNumberRequest();
        request.setPhoneNumber(number);
        return request;
    }

    @Test
    void getStatusReturnsVerifiedAndPendingNumbers() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        user.setPhoneNumber("+14165550000");

        PhoneVerificationStatusResponse status = service.getStatus(EMAIL);

        assertEquals("+14165550000", status.phoneNumber());
        assertTrue(status.phoneVerified());
        assertNull(status.pendingPhoneNumber());
    }

    @Test
    void getStatusThrowsWhenUserDoesNotExist() {
        when(users.findByEmail("missing@mail.com")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.getStatus("missing@mail.com"));
    }

    @Test
    void startVerificationRejectsAlreadyVerifiedNumber() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        user.setPhoneNumber(PHONE);

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.startVerification(phoneRequest(PHONE), EMAIL));

        verify(users, never()).save(any());
    }

    @Test
    void startVerificationDoesNotPersistLocalCodeWhenTwilioIsNotConfigured() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> service.startVerification(phoneRequest(PHONE), EMAIL));

        assertEquals(PHONE, user.getPendingPhoneNumber());
        assertNull(user.getPhoneVerificationCodeHash());
        verify(users, never()).save(any());
    }
}