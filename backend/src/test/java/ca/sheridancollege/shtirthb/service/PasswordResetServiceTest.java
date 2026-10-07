package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.PasswordResetToken;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.PasswordResetTokenRepository;

/**
 * UC-01 / UC-03: Forgot password and reset password for clients and admins.
 * Unit tests for PasswordResetService. The database and password encoder are mocked.
 *
 * This file has 11 tests:
 *
 *   Request a reset link (5 tests)
 *     Test 1  - Client requests reset -> token is created, valid for 30 minutes
 *     Test 2  - Email not registered -> no token is created, no error
 *     Test 3  - Admin requests reset -> ADMIN token is created
 *     Test 4  - Admin email not found -> no token is created
 *     Test 5  - Two requests -> two different tokens
 *
 *   Reset the password (6 tests)
 *     Test 6  - Valid client token -> password changed, token marked as used
 *     Test 7  - Valid admin token -> admin password changed and account unlocked
 *     Test 8  - Token does not exist -> reset fails
 *     Test 9  - Token already used -> reset fails
 *     Test 10 - Token expired -> reset fails
 *     Test 11 - User was deleted -> reset fails, token stays unused
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    // Fake database for client accounts
    @Mock
    private ClientUserRepository clientUserRepository;

    // Fake database for admin accounts
    @Mock
    private AdminUserRepository adminUserRepository;

    // Fake database for reset tokens
    @Mock
    private PasswordResetTokenRepository tokenRepository;

    // Fake password encoder (no real hashing in these tests)
    @Mock
    private PasswordEncoder passwordEncoder;

    // The real PasswordResetService, using all the fakes above
    @InjectMocks
    private PasswordResetService passwordResetService;

    // ---------- request reset ----------

    /*
     * TEST 1: Client requests a reset link
     * Checks that:
     *   - A CLIENT token is saved for the correct user
     *   - The token is not empty and not used yet
     *   - The token expires in about 30 minutes
     */
    @Test
    void requestClientReset_createsToken_validFor30Minutes() {
        // Arrange: client with id 7 exists
        ClientUser user = mock(ClientUser.class);
        when(user.getId()).thenReturn(7L);
        when(clientUserRepository.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        // Act: request a reset (email has extra spaces and capitals)
        LocalDateTime before = LocalDateTime.now();
        passwordResetService.requestClientPasswordReset("  Jane@Mail.com ");

        // Assert: capture the saved token and check its values
        ArgumentCaptor<PasswordResetToken> captor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());

        PasswordResetToken saved = captor.getValue();
        assertEquals("CLIENT", saved.getUserType());
        assertEquals(7L, saved.getUserId());
        assertFalse(saved.isUsed());
        assertNotNull(saved.getToken());
        assertFalse(saved.getToken().isBlank());
        assertTrue(saved.getExpiresAt().isAfter(before.plusMinutes(29)));
        assertTrue(saved.getExpiresAt().isBefore(before.plusMinutes(31)));
    }

    /*
     * TEST 2: Email is not registered
     * Checks that:
     *   - No error is thrown (so attackers cannot tell which emails exist)
     *   - No token is created
     */
    @Test
    void requestClientReset_doesNothing_whenEmailNotRegistered() {
        // Arrange: no client with this email
        when(clientUserRepository.findByEmail("nobody@mail.com")).thenReturn(Optional.empty());

        // Act + Assert: no error and nothing saved
        assertDoesNotThrow(() ->
                passwordResetService.requestClientPasswordReset("nobody@mail.com"));

        verify(tokenRepository, never()).save(any());
    }

    /*
     * TEST 3: Admin requests a reset link
     * Checks that:
     *   - An ADMIN token is saved for the correct admin
     */
    @Test
    void requestAdminReset_createsAdminToken() {
        // Arrange: admin with id 1 exists
        AdminUser admin = mock(AdminUser.class);
        when(admin.getId()).thenReturn(1L);
        when(adminUserRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        // Act: request a reset
        passwordResetService.requestAdminPasswordReset("admin@test.com");

        // Assert: token is for an ADMIN with id 1
        ArgumentCaptor<PasswordResetToken> captor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertEquals("ADMIN", captor.getValue().getUserType());
        assertEquals(1L, captor.getValue().getUserId());
    }

    /*
     * TEST 4: Admin email not found
     * Checks that:
     *   - No token is created
     */
    @Test
    void requestAdminReset_doesNothing_whenAdminNotFound() {
        // Arrange: no admin with this email
        when(adminUserRepository.findByEmail("x@test.com")).thenReturn(Optional.empty());

        // Act: request a reset
        passwordResetService.requestAdminPasswordReset("x@test.com");

        // Assert: nothing saved
        verify(tokenRepository, never()).save(any());
    }

    /*
     * TEST 5: Every request gets a new token
     * Checks that:
     *   - Two reset requests create two different tokens
     */
    @Test
    void eachRequest_generatesDifferentToken() {
        // Arrange: client exists
        ClientUser user = mock(ClientUser.class);
        when(user.getId()).thenReturn(7L);
        when(clientUserRepository.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        // Act: request a reset two times
        passwordResetService.requestClientPasswordReset("jane@mail.com");
        passwordResetService.requestClientPasswordReset("jane@mail.com");

        // Assert: the two saved tokens are different
        ArgumentCaptor<PasswordResetToken> captor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository, times(2)).save(captor.capture());
        assertNotEquals(captor.getAllValues().get(0).getToken(),
                captor.getAllValues().get(1).getToken());
    }

    // ---------- reset password ----------

    /*
     * TEST 6: Successful client password reset
     * Checks that:
     *   - The new password is saved in hashed form
     *   - The token is marked as used, so it cannot be used again
     */
    @Test
    void resetPassword_updatesClientPassword_andMarksTokenUsed() {
        // Arrange: valid token for client 7, client exists
        PasswordResetToken token = new PasswordResetToken(
                "abc", "CLIENT", 7L, LocalDateTime.now().plusMinutes(10));
        ClientUser user = new ClientUser("Jane", "jane@mail.com", "OLD");

        when(tokenRepository.findByToken("abc")).thenReturn(Optional.of(token));
        when(clientUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassword1")).thenReturn("NEW_ENCODED");

        // Act + Assert: reset succeeds
        assertTrue(passwordResetService.resetPassword("abc", "NewPassword1"));

        // Assert: password changed and token used
        assertEquals("NEW_ENCODED", user.getPassword());
        assertTrue(token.isUsed(), "Token must not be usable a second time");
        verify(clientUserRepository).save(user);
        verify(tokenRepository).save(token);
    }

    /*
     * TEST 7: Successful admin password reset
     * Checks that:
     *   - The admin password is changed
     *   - A locked admin account is unlocked (failed count back to 0)
     *   - The client table is not touched
     */
    @Test
    void resetPassword_updatesAdminPassword() {
        // Arrange: valid token for admin 1, admin exists
        PasswordResetToken token = new PasswordResetToken(
                "adm", "ADMIN", 1L, LocalDateTime.now().plusMinutes(10));
        AdminUser admin = new AdminUser("Administrator", "admin@test.com", "OLD");

        // The admin was locked after 5 failed logins
        admin.setFailedLoginAttempts(5);
        admin.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        when(tokenRepository.findByToken("adm")).thenReturn(Optional.of(token));
        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode("NewPassword1")).thenReturn("NEW_ENCODED");

        // Act + Assert: reset succeeds and only the admin is updated
        assertTrue(passwordResetService.resetPassword("adm", "NewPassword1"));
        assertEquals("NEW_ENCODED", admin.getPassword());
        verify(clientUserRepository, never()).findById(any());

        // Assert: the lock is removed
        assertEquals(0, admin.getFailedLoginAttempts());
        assertNull(admin.getLockedUntil());
    }

    /*
     * TEST 8: Token does not exist
     * Checks that:
     *   - Reset fails
     *   - No new password is created
     */
    @Test
    void resetPassword_fails_whenTokenDoesNotExist() {
        // Arrange: token not found
        when(tokenRepository.findByToken("fake")).thenReturn(Optional.empty());

        // Act + Assert: reset fails
        assertFalse(passwordResetService.resetPassword("fake", "NewPassword1"));
        verify(passwordEncoder, never()).encode(anyString());
    }

    /*
     * TEST 9: Token was already used
     * Checks that:
     *   - Reset fails
     *   - The user is not changed
     */
    @Test
    void resetPassword_fails_whenTokenAlreadyUsed() {
        // Arrange: token exists but is already used
        PasswordResetToken token = new PasswordResetToken(
                "abc", "CLIENT", 7L, LocalDateTime.now().plusMinutes(10));
        token.setUsed(true);
        when(tokenRepository.findByToken("abc")).thenReturn(Optional.of(token));

        // Act + Assert: reset fails and nothing is saved
        assertFalse(passwordResetService.resetPassword("abc", "NewPassword1"));
        verify(clientUserRepository, never()).save(any());
    }

    /*
     * TEST 10: Token has expired
     * Checks that:
     *   - Reset fails
     *   - The user is not changed
     */
    @Test
    void resetPassword_fails_whenTokenExpired() {
        // Arrange: token expired 1 minute ago
        PasswordResetToken token = new PasswordResetToken(
                "abc", "CLIENT", 7L, LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByToken("abc")).thenReturn(Optional.of(token));

        // Act + Assert: reset fails and nothing is saved
        assertFalse(passwordResetService.resetPassword("abc", "NewPassword1"));
        verify(clientUserRepository, never()).save(any());
    }

    /*
     * TEST 11: User account was deleted
     * Checks that:
     *   - Reset fails
     *   - The token is NOT marked as used
     */
    @Test
    void resetPassword_fails_whenUserWasDeleted() {
        // Arrange: valid token, but client 7 no longer exists
        PasswordResetToken token = new PasswordResetToken(
                "abc", "CLIENT", 7L, LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByToken("abc")).thenReturn(Optional.of(token));
        when(clientUserRepository.findById(7L)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("NewPassword1")).thenReturn("NEW_ENCODED");

        // Act + Assert: reset fails and token stays unused
        assertFalse(passwordResetService.resetPassword("abc", "NewPassword1"));
        assertFalse(token.isUsed());
    }
}