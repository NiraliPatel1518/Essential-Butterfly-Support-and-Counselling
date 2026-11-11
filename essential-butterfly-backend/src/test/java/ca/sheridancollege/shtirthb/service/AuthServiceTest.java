package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import ca.sheridancollege.shtirthb.dto.LoginRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.dto.SignupRequest;
import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock ClientUserRepository clients;
    @Mock AdminUserRepository admins;
    @Mock PasswordEncoder encoder;
    @Mock AdminAuthService adminAuth;
    AuthService service;

    @BeforeEach void setUp() { service = new AuthService(clients, admins, encoder, adminAuth); }

    @Test void signupNormalizesEmailAndEncodesPassword() {
        SignupRequest r = signup(" Jane@Mail.com ", "Jane Doe", "Password123", "Password123");
        when(clients.existsByEmail("jane@mail.com")).thenReturn(false);
        when(encoder.encode("Password123")).thenReturn("HASH");
        service.signup(r);
        verify(clients).save(argThat(u -> u.getEmail().equals("jane@mail.com") &&
                u.getFullName().equals("Jane Doe") && u.getPassword().equals("HASH")));
    }

    @Test void signupRejectsExistingEmail() {
        when(clients.existsByEmail("jane@mail.com")).thenReturn(true);
        assertThrows(AppExceptions.DuplicateEmailException.class, () -> service.signup(
                signup("Jane@mail.com", "Jane", "Password123", "Password123")));
        verify(clients, never()).save(any());
    }

    @Test void signupRejectsPasswordMismatch() {
        when(clients.existsByEmail("jane@mail.com")).thenReturn(false);
        assertThrows(AppExceptions.ValidationException.class, () -> service.signup(
                signup("jane@mail.com", "Jane", "Password123", "Different123")));
        verify(clients, never()).save(any());
    }

    @Test void authenticateReturnsClientAccountWithNormalizedEmail() {
        ClientUser user = new ClientUser("Jane", "jane@mail.com", "HASH");
        when(clients.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));
        when(admins.findByEmail("jane@mail.com")).thenReturn(Optional.empty());
        when(encoder.matches("Password123", "HASH")).thenReturn(true);
        AuthService.AuthenticatedAccount result = service.authenticate(login(" Jane@Mail.com ", "Password123"));
        assertNotNull(result);
        assertEquals("jane@mail.com", result.email());
        assertEquals("CLIENT", result.role());
    }

    @Test void authenticateReturnsNullForIncorrectClientPassword() {
        when(clients.findByEmail("jane@mail.com")).thenReturn(Optional.of(
                new ClientUser("Jane", "jane@mail.com", "HASH")));
        when(admins.findByEmail("jane@mail.com")).thenReturn(Optional.empty());
        when(encoder.matches("bad", "HASH")).thenReturn(false);
        assertNull(service.authenticate(login("jane@mail.com", "bad")));
    }

    @Test void authenticateDelegatesAdminCredentialsToAdminAuthService() {
        AdminUser admin = new AdminUser("Admin", "admin@mail.com", "HASH");
        when(clients.findByEmail("admin@mail.com")).thenReturn(Optional.empty());
        when(admins.findByEmail("admin@mail.com")).thenReturn(Optional.of(admin));
        when(adminAuth.authenticate("admin@mail.com", "Password123")).thenReturn(admin);
        AuthService.AuthenticatedAccount result = service.authenticate(login("ADMIN@mail.com", "Password123"));
        assertEquals("ADMIN", result.role());
        verify(adminAuth).authenticate("admin@mail.com", "Password123");
    }

    @Test void authenticateRejectsEmailMappedToBothAccountTypes() {
        when(clients.findByEmail("both@mail.com")).thenReturn(Optional.of(
                new ClientUser("Client", "both@mail.com", "HASH")));
        when(admins.findByEmail("both@mail.com")).thenReturn(Optional.of(
                new AdminUser("Admin", "both@mail.com", "HASH")));
        assertThrows(AppExceptions.ConflictException.class, () -> service.authenticate(login("both@mail.com", "Password123")));
        verifyNoInteractions(adminAuth);
    }

    @Test void authenticateReturnsNullForUnknownEmail() {
        when(clients.findByEmail("none@mail.com")).thenReturn(Optional.empty());
        when(admins.findByEmail("none@mail.com")).thenReturn(Optional.empty());
        assertNull(service.authenticate(login("none@mail.com", "Password123")));
    }

    private SignupRequest signup(String email, String name, String password, String confirm) {
        SignupRequest r = new SignupRequest(); r.setEmail(email); r.setFullName(name);
        r.setPassword(password); r.setConfirmPassword(confirm); return r;
    }
    private LoginRequest login(String email, String password) {
        LoginRequest r = new LoginRequest(); r.setEmail(email); r.setPassword(password); return r;
    }
}