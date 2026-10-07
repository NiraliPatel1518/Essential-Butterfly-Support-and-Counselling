package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
 * This file has 3 tests:
 *   Test 1 - Correct admin email and password -> admin is returned
 *   Test 2 - Wrong password -> no admin is returned
 *   Test 3 - Email not registered as admin -> no admin is returned
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

    /*
     * TEST 1: Correct admin login
     * Checks that:
     *   - The admin account is returned when email and password are correct
     *   - Extra spaces and capital letters in the email do not matter
     */
    @Test
    void authenticate_returnsAdmin_whenCredentialsAreCorrect() {
        // Arrange: admin exists and password matches
        AdminUser admin = new AdminUser("Administrator", "admin@test.com", "ENCODED");
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
        AdminUser admin = new AdminUser("Administrator", "admin@test.com", "ENCODED");
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
}