package ca.sheridancollege.shtirthb.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;

import ca.sheridancollege.shtirthb.dto.AdminLoginChallengeResponse;
import ca.sheridancollege.shtirthb.dto.LoginRequest;
import ca.sheridancollege.shtirthb.dto.ResendVerificationRequest;
import ca.sheridancollege.shtirthb.dto.VerifyEmailRequest;
import ca.sheridancollege.shtirthb.dto.SignupRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.service.AdminEmailSecurityService;
import ca.sheridancollege.shtirthb.service.AuthService;
import ca.sheridancollege.shtirthb.service.EmailVerificationService;
import ca.sheridancollege.shtirthb.service.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    @Mock AuthService authService;
    @Mock JwtService jwtService;
    @Mock ClientUserRepository users;
    @Mock EmailVerificationService emailVerificationService;
    @Mock AdminEmailSecurityService adminEmailSecurityService;
    @Mock org.springframework.security.core.Authentication authentication;
    @InjectMocks AuthController controller;

    @Test void signupReturnsCreated() {
        SignupRequest request = mock(SignupRequest.class);
        var response = controller.signup(request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(authService).signup(request);
    }

    @Test void loginReturnsUnauthorizedForInvalidCredentials() {
        LoginRequest request = mock(LoginRequest.class);
        when(authService.authenticate(request)).thenReturn(null);
        assertThrows(AppExceptions.InvalidCredentialsException.class, () -> controller.login(request));
        verifyNoInteractions(jwtService);
    }

    @Test void loginReturnsGeneratedJwt() {
        LoginRequest request = mock(LoginRequest.class);
        when(authService.authenticate(request)).thenReturn(new AuthService.AuthenticatedAccount("a@example.com","CLIENT"));
        when(jwtService.generateToken("a@example.com","CLIENT")).thenReturn("jwt-token");
        assertEquals("jwt-token", controller.login(request).getBody());
    }

    @Test void returnsCurrentClientName() {
        ClientUser user = mock(ClientUser.class);
        when(authentication.getName()).thenReturn("a@example.com");
        when(users.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(user.getFullName()).thenReturn("Alex");

        Map<String, String> expected = new java.util.LinkedHashMap<>();
        expected.put("fullName", "Alex");
        expected.put("email", null);
        expected.put("phoneNumber", null);

        assertEquals(expected, controller.getCurrentUser(authentication).getBody());
    }

    @Test void returnsNotFoundWhenCurrentUserMissing() {
        when(authentication.getName()).thenReturn("missing@example.com");
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> controller.getCurrentUser(authentication));
    }

    @Test void signupTellsTheUserAVerificationCodeWasSent() {
        SignupRequest request = mock(SignupRequest.class);
        var response = controller.signup(request);
        assertTrue(response.getBody().contains("verification code has been sent"));
    }

    @Test void verifyEmailCallsTheServiceAndReturnsOk() {
        VerifyEmailRequest request = mock(VerifyEmailRequest.class);
        var response = controller.verifyEmail(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("verified successfully"));
        verify(emailVerificationService).verify(request);
    }

    @Test void verifyEmailPassesTheServiceErrorOn() {
        VerifyEmailRequest request = mock(VerifyEmailRequest.class);
        doThrow(new AppExceptions.ValidationException("The verification code is incorrect."))
                .when(emailVerificationService).verify(request);
        assertThrows(AppExceptions.ValidationException.class, () -> controller.verifyEmail(request));
    }

    @Test void resendVerificationCallsTheServiceAndReturnsOk() {
        ResendVerificationRequest request = mock(ResendVerificationRequest.class);
        var response = controller.resendVerification(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(emailVerificationService).resend(request);
    }

    @Test void adminLoginStartsTheEmailCodeStepAndIssuesNoJwt() {
        LoginRequest request = mock(LoginRequest.class);
        var challenge = new AdminLoginChallengeResponse(true, "7.secret", "ad***@test.com");
        when(authService.authenticate(request)).thenReturn(new AuthService.AuthenticatedAccount("admin@test.com","ADMIN"));
        when(adminEmailSecurityService.startLoginVerification("admin@test.com")).thenReturn(challenge);
        var response = controller.login(request);
        assertSame(challenge, response.getBody());
        verifyNoInteractions(jwtService);
    }

    @Test void clientLoginDoesNotAskForAnEmailCode() {
        LoginRequest request = mock(LoginRequest.class);
        when(authService.authenticate(request)).thenReturn(new AuthService.AuthenticatedAccount("a@example.com","CLIENT"));
        when(jwtService.generateToken("a@example.com","CLIENT")).thenReturn("jwt-token");
        controller.login(request);
        verifyNoInteractions(adminEmailSecurityService);
    }
}