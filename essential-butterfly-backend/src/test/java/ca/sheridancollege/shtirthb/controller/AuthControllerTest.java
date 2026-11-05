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

import java.util.Map;
import java.util.Optional;
import ca.sheridancollege.shtirthb.dto.LoginRequest;
import ca.sheridancollege.shtirthb.dto.SignupRequest;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.service.AuthService;
import ca.sheridancollege.shtirthb.service.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    @Mock AuthService authService;
    @Mock JwtService jwtService;
    @Mock ClientUserRepository users;
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
        assertEquals(HttpStatus.UNAUTHORIZED, controller.login(request).getStatusCode());
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
        assertEquals(Map.of("fullName","Alex"), controller.getCurrentUser(authentication).getBody());
    }
    @Test void returnsNotFoundWhenCurrentUserMissing() {
        when(authentication.getName()).thenReturn("missing@example.com");
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, controller.getCurrentUser(authentication).getStatusCode());
    }
}
