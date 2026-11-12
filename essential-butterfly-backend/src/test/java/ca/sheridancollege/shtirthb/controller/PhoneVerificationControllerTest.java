package ca.sheridancollege.shtirthb.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import ca.sheridancollege.shtirthb.dto.PhoneNumberRequest;
import ca.sheridancollege.shtirthb.dto.PhoneVerificationStatusResponse;
import ca.sheridancollege.shtirthb.dto.VerifyPhoneRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.service.PhoneVerificationService;

/*
 * Tests for PhoneVerificationController. The email always comes from the
 * logged-in client (Authentication), never from the request.
 */
@ExtendWith(MockitoExtension.class)
class PhoneVerificationControllerTest {

    @Mock PhoneVerificationService phoneVerificationService;
    @Mock Authentication authentication;
    @InjectMocks PhoneVerificationController controller;

    @Test
    void getStatus_returnsTheStatusOfTheLoggedInClient() {
        var status = new PhoneVerificationStatusResponse(true, "+14165551234", null);
        when(authentication.getName()).thenReturn("jane@mail.com");
        when(phoneVerificationService.getStatus("jane@mail.com")).thenReturn(status);

        var response = controller.getStatus(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(status, response.getBody());
    }

    @Test
    void sendCode_startsVerificationForTheLoggedInClient() {
        PhoneNumberRequest request = new PhoneNumberRequest();
        when(authentication.getName()).thenReturn("jane@mail.com");

        var response = controller.sendCode(request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("verification code has been sent"));
        verify(phoneVerificationService).startVerification(request, "jane@mail.com");
    }

    @Test
    void sendCode_passesTheServiceErrorOn() {
        PhoneNumberRequest request = new PhoneNumberRequest();
        when(authentication.getName()).thenReturn("jane@mail.com");
        doThrow(new AppExceptions.BusinessRuleException("This phone number is already verified."))
                .when(phoneVerificationService).startVerification(request, "jane@mail.com");

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> controller.sendCode(request, authentication));
    }

    @Test
    void verify_checksTheCodeForTheLoggedInClient() {
        VerifyPhoneRequest request = new VerifyPhoneRequest();
        when(authentication.getName()).thenReturn("jane@mail.com");

        var response = controller.verify(request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("verified successfully"));
        verify(phoneVerificationService).verify(request, "jane@mail.com");
    }

    @Test
    void verify_passesTheWrongCodeErrorOn() {
        VerifyPhoneRequest request = new VerifyPhoneRequest();
        when(authentication.getName()).thenReturn("jane@mail.com");
        doThrow(new AppExceptions.ValidationException("The verification code is incorrect."))
                .when(phoneVerificationService).verify(request, "jane@mail.com");

        assertThrows(AppExceptions.ValidationException.class,
                () -> controller.verify(request, authentication));
    }

    @Test
    void resend_resendsForTheLoggedInClient() {
        when(authentication.getName()).thenReturn("jane@mail.com");

        var response = controller.resend(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(phoneVerificationService).resend("jane@mail.com");
    }
}