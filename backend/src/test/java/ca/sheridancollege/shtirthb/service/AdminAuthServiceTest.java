package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;

/**
 * UC-03: Admin Logs In Securely
 * Unit tests for AdminAuthService. The database and password encoder are mocked,
 * so only the login rules inside AdminAuthService are tested.
 *
 * This file has 10 tests:
 *
 *   Normal login (3 tests)
 *     Test 1  - Correct admin email and password -> admin is returned
 *     Test 2  - Wrong password -> no admin is returned
 *     Test 3  - Email not registered as admin -> no admin is returned
 *
 *   Account lockout (7 tests)
 *     Test 4  - Wrong password -> failed attempt count goes up by 1
 *     Test 5  - 4 failed attempts -> account is NOT locked yet
 *     Test 6  - 5th failed attempt -> account is locked for 15 minutes
 *     Test 7  - Locked account -> login is blocked, even with the correct password
 *     Test 8  - Lock time has passed -> admin can log in again
 *     Test 9  - Successful login -> failed attempt count is reset to 0
 *     Test 10 - unlockAfterPasswordReset -> lock and count are cleared
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    // Fake database for admin accounts
    @Mock
    private AdminUserRepository adminUserRepository;

    // Fake password encoder (no real hashing in these tests)
    @Mock
    private PasswordEncoder passwordEncoder;

    // The real AdminAuthService, using the fake database and fake encoder above
    @InjectMocks
    private AdminAuthService adminAuthService;

    // Helper: creates a test admin whose stored (hashed) password is "ENCODED"
    private AdminUser testAdmin() {
        return new AdminUser("Administrator", "admin@test.com", "ENCODED");
    }

    // ---------- normal login ----------

    /*
     * TEST 1: Correct admin login
     * Checks that:
     *   - The admin account is returned when email and password are correct
     *   - Extra spaces and capital letters in the email do not matter
     */
    @Test
    void authenticate_returnsAdmin_whenCredentialsAreCorrect() {
        // Arrange: admin exists and password matches
        AdminUser admin = testAdmin();
        when(adminUserRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Admin@12345", "ENCODED")).thenReturn(true);

        // Act: log in with extra spaces and mixed-case email
        AdminUser result = adminAuthService.authenticate("  ADMIN@test.com ", "Admin@12345");

        // Assert: the correct admin is returned
        assertNotNull(result);
        assertEquals("admin@test.com", result.getEmail());
    }

    /*
     * TEST 2: Wrong admin password
     * Checks that:
     *   - Nothing (null) is returned when the password does not match
     */
    @Test
    void authenticate_returnsNull_whenPasswordIsWrong() {
        // Arrange: admin exists but password does not match
        AdminUser admin = testAdmin();
        when(adminUserRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "ENCODED")).thenReturn(false);

        // Act + Assert: login fails
        assertNull(adminAuthService.authenticate("admin@test.com", "wrong"));
    }

    /*
     * TEST 3: Email is not an admin
     * Checks that:
     *   - Nothing (null) is returned when no admin has this email
     *   - The password is not even checked
     */
    @Test
    void authenticate_returnsNull_whenAdminDoesNotExist() {
        // Arrange: no admin with this email
        when(adminUserRepository.findByEmail("someone@test.com")).thenReturn(Optional.empty());

        // Act + Assert: login fails and password is never checked
        assertNull(adminAuthService.authenticate("someone@test.com", "Admin@12345"));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    // ---------- account lockout ----------

    /*
     * TEST 4: Failed attempt is counted
     * Checks that:
     *   - One wrong password changes the count from 0 to 1
     *   - The new count is saved to the database
     */
    @Test
    void wrongPassword_increasesFailedAttempts() {
        // Arrange: admin with 0 failed attempts
        AdminUser admin = testAdmin();
        when(adminUserRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "ENCODED")).thenReturn(false);

        // Act: one wrong login
        adminAuthService.authenticate("admin@test.com", "wrong");

        // Assert: count is 1, not locked, and saved
        assertEquals(1, admin.getFailedLoginAttempts());
        assertNull(admin.getLockedUntil());
        verify(adminUserRepository).save(admin);
    }

    /*
     * TEST 5: 4 failed attempts
     * Checks that:
     *   - The account is still NOT locked after the 4th wrong password
     */
    @Test
    void fourFailedAttempts_doNotLockAccount() {
        // Arrange: admin already has 3 failed attempts
        AdminUser admin = testAdmin();
        admin.setFailedLoginAttempts(3);
        when(adminUserRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "ENCODED")).thenReturn(false);

        // Act: 4th wrong login
        adminAuthService.authenticate("admin@test.com", "wrong");

        // Assert: count is 4 and the account is not locked
        assertEquals(4, admin.getFailedLoginAttempts());
        assertNull(admin.getLockedUntil());
    }

    /*
     * TEST 6: 5th failed attempt locks the account
     * Checks that:
     *   - After the 5th wrong password, the account is locked
     *   - The lock lasts about 15 minutes
     */
    @Test
    void fifthFailedAttempt_locksAccountFor15Minutes() {
        // Arrange: admin already has 4 failed attempts
        AdminUser admin = testAdmin();
        admin.setFailedLoginAttempts(4);
        when(adminUserRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "ENCODED")).thenReturn(false);

        // Act: 5th wrong login
        LocalDateTime before = LocalDateTime.now();
        assertNull(adminAuthService.authenticate("admin@test.com", "wrong"));

        // Assert: count is 5 and locked for about 15 minutes
        assertEquals(5, admin.getFailedLoginAttempts());
        assertNotNull(admin.getLockedUntil());
        assertTrue(admin.getLockedUntil().isAfter(before.plusMinutes(14)));
        assertTrue(admin.getLockedUntil().isBefore(before.plusMinutes(16)));
    }

    /*
     * TEST 7: Locked account
     * Checks that:
     *   - Login throws an error while the account is locked
     *   - This happens even if the password is correct
     *   - The password is not even checked
     */
    @Test
    void lockedAccount_blocksLogin_evenWithCorrectPassword() {
        // Arrange: admin is locked for 10 more minutes
        AdminUser admin = testAdmin();
        admin.setFailedLoginAttempts(5);
        admin.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(adminUserRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        // Act + Assert: login throws "locked" error
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> adminAuthService.authenticate("admin@test.com", "Admin@12345"));

        assertEquals("Admin account is temporarily locked", ex.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    /*
     * TEST 8: Lock time is over
     * Checks that:
     *   - When the lock has expired, the admin can log in again
     *   - The lock and the failed count are cleared
     */
    @Test
    void expiredLock_allowsLoginAgain() {
        // Arrange: lock ended 1 minute ago
        AdminUser admin = testAdmin();
        admin.setFailedLoginAttempts(5);
        admin.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(adminUserRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Admin@12345", "ENCODED")).thenReturn(true);

        // Act: log in with the correct password
        AdminUser result = adminAuthService.authenticate("admin@test.com", "Admin@12345");

        // Assert: login works and the lock is removed
        assertNotNull(result);
        assertEquals(0, admin.getFailedLoginAttempts());
        assertNull(admin.getLockedUntil());
    }

    /*
     * TEST 9: Successful login resets the count
     * Checks that:
     *   - After a correct login, the failed count goes back to 0
     *   - So old mistakes do not add up over time
     */
    @Test
    void successfulLogin_resetsFailedAttempts() {
        // Arrange: admin has 3 failed attempts from before
        AdminUser admin = testAdmin();
        admin.setFailedLoginAttempts(3);
        when(adminUserRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Admin@12345", "ENCODED")).thenReturn(true);

        // Act: correct login
        adminAuthService.authenticate("admin@test.com", "Admin@12345");

        // Assert: count is back to 0 and saved
        assertEquals(0, admin.getFailedLoginAttempts());
        verify(adminUserRepository).save(admin);
    }

    /*
     * TEST 10: Unlock after password reset
     * Checks that:
     *   - unlockAfterPasswordReset clears the lock and the failed count
     *   - The change is saved
     */
    @Test
    void unlockAfterPasswordReset_clearsLock() {
        // Arrange: admin is locked
        AdminUser admin = testAdmin();
        admin.setFailedLoginAttempts(5);
        admin.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        // Act: unlock
        adminAuthService.unlockAfterPasswordReset(admin);

        // Assert: lock removed, count is 0, and saved
        assertEquals(0, admin.getFailedLoginAttempts());
        assertNull(admin.getLockedUntil());
        verify(adminUserRepository).save(admin);
    }
}
