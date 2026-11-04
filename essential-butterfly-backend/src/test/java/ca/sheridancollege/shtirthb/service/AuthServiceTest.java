package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import ca.sheridancollege.shtirthb.dto.LoginRequest;
import ca.sheridancollege.shtirthb.dto.SignupRequest;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

/**
 * UC-01: Create Account / Log In
 * Unit tests for AuthService. The database and password encoder are mocked,
 * so only the business rules inside AuthService are tested.
 *
 * This file has 7 tests:
 *
 *   Signup (4 tests)
 *     Test 1 - Valid signup -> user is saved with clean email and hashed password
 *     Test 2 - Email already exists -> error, user is not saved
 *     Test 3 - Duplicate check ignores upper/lower case in the email
 *     Test 4 - Passwords do not match -> error, user is not saved
 *
 *   Login (3 tests)
 *     Test 5 - Correct email and password -> login succeeds
 *     Test 6 - Wrong password -> login fails
 *     Test 7 - Email not registered -> login fails
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // Fake database for client accounts
    @Mock
    private ClientUserRepository clientUserRepository;

    // Fake password encoder (no real hashing in these tests)
    @Mock
    private PasswordEncoder passwordEncoder;

    // The real AuthService, using the fake database and fake encoder above
    @InjectMocks
    private AuthService authService;

    // Helper: builds a signup request with the given values
    private SignupRequest signupRequest(String name, String email,
                                        String password, String confirm) {
        SignupRequest request = new SignupRequest();
        request.setFullName(name);
        request.setEmail(email);
        request.setPassword(password);
        request.setConfirmPassword(confirm);
        return request;
    }

    // Helper: builds a login request with the given values
    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    // ---------- signup ----------

    /*
     * TEST 1: Valid signup
     * Checks that:
     *   - Extra spaces are removed from the name and email
     *   - The email is saved in lowercase
     *   - The password is saved in hashed form, never as plain text
     */
    @Test
    void signup_savesNewUser_withTrimmedLowercaseEmail_andEncodedPassword() {
        // Arrange: email is not taken, encoder returns a fake hash
        when(clientUserRepository.existsByEmail("jane@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("ENCODED");

        // Act: sign up with extra spaces and mixed-case email
        authService.signup(signupRequest("  Jane Doe  ", "  Jane@Mail.COM ",
                "Password123", "Password123"));

        // Assert: capture the user that was saved and check its values
        ArgumentCaptor<ClientUser> captor = ArgumentCaptor.forClass(ClientUser.class);
        verify(clientUserRepository).save(captor.capture());

        ClientUser saved = captor.getValue();
        assertEquals("Jane Doe", saved.getFullName());
        assertEquals("jane@mail.com", saved.getEmail());
        assertEquals("ENCODED", saved.getPassword());
        assertNotEquals("Password123", saved.getPassword(),
                "Plain text password must never be saved");
    }

    /*
     * TEST 2: Email already exists
     * Checks that:
     *   - The error "An account with this email already exists" is thrown
     *   - No user is saved
     */
    @Test
    void signup_throwsError_whenEmailAlreadyExists() {
        // Arrange: email is already taken
        when(clientUserRepository.existsByEmail("jane@mail.com")).thenReturn(true);

        // Act + Assert: signup throws an error
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.signup(signupRequest("Jane", "jane@mail.com",
                        "Password123", "Password123")));

        assertEquals("An account with this email already exists", ex.getMessage());
        verify(clientUserRepository, never()).save(any());
    }

    /*
     * TEST 3: Duplicate check ignores email case
     * Checks that:
     *   - "JANE@MAIL.COM" is checked as "jane@mail.com"
     *   - So the same person cannot sign up twice using capital letters
     */
    @Test
    void signup_duplicateCheck_ignoresEmailCase() {
        // Arrange: lowercase email is already taken
        when(clientUserRepository.existsByEmail("jane@mail.com")).thenReturn(true);

        // Act + Assert: signup with uppercase email is still blocked
        assertThrows(IllegalArgumentException.class,
                () -> authService.signup(signupRequest("Jane", "JANE@MAIL.COM",
                        "Password123", "Password123")));

        verify(clientUserRepository).existsByEmail("jane@mail.com");
    }

    /*
     * TEST 4: Passwords do not match
     * Checks that:
     *   - The error "Passwords do not match" is thrown
     *   - No user is saved and the password is not hashed
     */
    @Test
    void signup_throwsError_whenPasswordsDoNotMatch() {
        // Arrange: email is free
        when(clientUserRepository.existsByEmail("jane@mail.com")).thenReturn(false);

        // Act + Assert: signup with two different passwords throws an error
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.signup(signupRequest("Jane", "jane@mail.com",
                        "Password123", "Different123")));

        assertEquals("Passwords do not match", ex.getMessage());
        verify(clientUserRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    // ---------- login ----------

    /*
     * TEST 5: Correct login
     * Checks that:
     *   - Login returns true when email and password are correct
     *   - Extra spaces and capital letters in the email do not matter
     */
    @Test
    void login_returnsTrue_whenEmailAndPasswordAreCorrect() {
        // Arrange: user exists and password matches
        ClientUser user = new ClientUser("Jane", "jane@mail.com", "ENCODED");
        when(clientUserRepository.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "ENCODED")).thenReturn(true);

        // Act + Assert: login succeeds
        assertTrue(authService.login(loginRequest(" Jane@Mail.com ", "Password123")));
    }

    /*
     * TEST 6: Wrong password
     * Checks that:
     *   - Login returns false when the password does not match
     */
    @Test
    void login_returnsFalse_whenPasswordIsWrong() {
        // Arrange: user exists but password does not match
        ClientUser user = new ClientUser("Jane", "jane@mail.com", "ENCODED");
        when(clientUserRepository.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass1", "ENCODED")).thenReturn(false);

        // Act + Assert: login fails
        assertFalse(authService.login(loginRequest("jane@mail.com", "WrongPass1")));
    }

    /*
     * TEST 7: Email not registered
     * Checks that:
     *   - Login returns false when no account has this email
     *   - The password is not even checked
     */
    @Test
    void login_returnsFalse_whenUserDoesNotExist() {
        // Arrange: no user with this email
        when(clientUserRepository.findByEmail("nobody@mail.com")).thenReturn(Optional.empty());

        // Act + Assert: login fails and password is never checked
        assertFalse(authService.login(loginRequest("nobody@mail.com", "Password123")));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}