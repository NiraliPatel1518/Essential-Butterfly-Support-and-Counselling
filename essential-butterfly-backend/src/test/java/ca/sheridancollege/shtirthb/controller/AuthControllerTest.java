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

    /*
     * Checks: Sign-up answers 201 Created and calls the auth service.
     * How:    We pass a fake SignupRequest and verify authService.signup is called with it.
     */
    @Test void signupReturnsCreated() {
        SignupRequest request = mock(SignupRequest.class);
        var response = controller.signup(request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(authService).signup(request);
    }

    /*
     * Checks: A failed login throws InvalidCredentialsException and makes no token.
     * How:    The fake service returns null (no account matched). The controller throws, and
     *   verifyNoInteractions proves JwtService was not used.
     */
    @Test void loginReturnsUnauthorizedForInvalidCredentials() {
        LoginRequest request = mock(LoginRequest.class);
        when(authService.authenticate(request)).thenReturn(null);
        assertThrows(AppExceptions.InvalidCredentialsException.class, () -> controller.login(request));
        verifyNoInteractions(jwtService);
    }

    /*
     * Checks: A correct client login returns a JWT.
     * How:    The fake service returns a CLIENT account. The fake JwtService returns jwt-token, and
     *   the controller returns it.
     */
    @Test void loginReturnsGeneratedJwt() {
        LoginRequest request = mock(LoginRequest.class);
        when(authService.authenticate(request)).thenReturn(new AuthService.AuthenticatedAccount("a@example.com","CLIENT"));
        when(jwtService.generateToken("a@example.com","CLIENT")).thenReturn("jwt-token");
        assertEquals("jwt-token", controller.login(request).getBody());
    }

    /*
     * Checks: The current-user URL returns the name of the logged-in client.
     * How:    The fake repository finds the client by email. The answer is a map with fullName Alex,
     *   and empty email and phone.
     */
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

    /*
     * Checks: A logged-in email with no client record gives ResourceNotFoundException.
     * How:    The fake repository returns Optional.empty, so the controller throws.
     */
    @Test void returnsNotFoundWhenCurrentUserMissing() {
        when(authentication.getName()).thenReturn("missing@example.com");
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> controller.getCurrentUser(authentication));
    }

    /*
     * Checks: The sign-up answer tells the user that a verification code was sent.
     * How:    The text of the response must contain "verification code has been sent".
     */
    @Test void signupTellsTheUserAVerificationCodeWasSent() {
        SignupRequest request = mock(SignupRequest.class);
        var response = controller.signup(request);
        assertTrue(response.getBody().contains("verification code has been sent"));
    }

    /*
     * Checks: Verifying the email calls the service and answers 200.
     * How:    The answer text says verified successfully, and emailVerificationService.verify is
     *   called.
     */
    @Test void verifyEmailCallsTheServiceAndReturnsOk() {
        VerifyEmailRequest request = mock(VerifyEmailRequest.class);
        var response = controller.verifyEmail(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("verified successfully"));
        verify(emailVerificationService).verify(request);
    }

    /*
     * Checks: A wrong verification code error is passed on.
     * How:    The service throws ValidationException. The controller throws it as well.
     */
    @Test void verifyEmailPassesTheServiceErrorOn() {
        VerifyEmailRequest request = mock(VerifyEmailRequest.class);
        doThrow(new AppExceptions.ValidationException("The verification code is incorrect."))
                .when(emailVerificationService).verify(request);
        assertThrows(AppExceptions.ValidationException.class, () -> controller.verifyEmail(request));
    }

    /*
     * Checks: Resending the verification code calls the service and answers 200.
     * How:    We verify emailVerificationService.resend is called with the request.
     */
    @Test void resendVerificationCallsTheServiceAndReturnsOk() {
        ResendVerificationRequest request = mock(ResendVerificationRequest.class);
        var response = controller.resendVerification(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(emailVerificationService).resend(request);
    }

    /*
     * Checks: An admin login starts the email code step and does not give a token yet.
     * How:    The fake service says the account is ADMIN. The controller returns the challenge, and
     *   verifyNoInteractions proves no JWT was made.
     */
    @Test void adminLoginStartsTheEmailCodeStepAndIssuesNoJwt() {
        LoginRequest request = mock(LoginRequest.class);
        var challenge = new AdminLoginChallengeResponse(true, "7.secret", "ad***@test.com");
        when(authService.authenticate(request)).thenReturn(new AuthService.AuthenticatedAccount("admin@test.com","ADMIN"));
        when(adminEmailSecurityService.startLoginVerification("admin@test.com")).thenReturn(challenge);
        var response = controller.login(request);
        assertSame(challenge, response.getBody());
        verifyNoInteractions(jwtService);
    }

    /*
     * Checks: A client login does not ask for an email code.
     * How:    After a CLIENT login, verifyNoInteractions proves the admin email-security service was
     *   never used.
     */
    @Test void clientLoginDoesNotAskForAnEmailCode() {
        LoginRequest request = mock(LoginRequest.class);
        when(authService.authenticate(request)).thenReturn(new AuthService.AuthenticatedAccount("a@example.com","CLIENT"));
        when(jwtService.generateToken("a@example.com","CLIENT")).thenReturn("jwt-token");
        controller.login(request);
        verifyNoInteractions(adminEmailSecurityService);
    }
}