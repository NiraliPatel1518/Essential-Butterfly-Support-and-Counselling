package ca.sheridancollege.shtirthb.initializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;

class AdminDataInitializerTest {

    // Test values only. The real ones come from ADMIN_EMAIL and ADMIN_PASSWORD.
    private static final String EMAIL = "Owner@Example.com";
    private static final String PASSWORD = "Sample-Password-123";

    private AdminUserRepository repository;
    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        repository = mock(AdminUserRepository.class);
        encoder = new BCryptPasswordEncoder();
    }

    private CommandLineRunner runner(String email, String password) {
        return new AdminDataInitializer()
                .createDefaultAdmin(repository, encoder, email, password);
    }

    private AdminUser savedAdmin() {
        ArgumentCaptor<AdminUser> saved = ArgumentCaptor.forClass(AdminUser.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    /*
     * Checks: When there is no admin, the first admin is created from the configured email and
     *   password.
     * How:    The fake repository says count is 0. We run the initializer and capture the saved
     *   AdminUser. Its full name must be Administrator.
     */
    @Test
    void createsTheAdminFromTheConfiguredEmailAndPasswordWhenThereAreNoAdmins() throws Exception {
        when(repository.count()).thenReturn(0L);

        runner(EMAIL, PASSWORD).run();

        assertEquals("Administrator", savedAdmin().getFullName());
    }

    /*
     * Checks: The admin email is trimmed and saved in lower case.
     * How:    We give the email with spaces and capital letters. The saved email must be
     *   owner@example.com.
     */
    @Test
    void theEmailIsTrimmedAndSavedInLowerCase() throws Exception {
        when(repository.count()).thenReturn(0L);

        runner("  " + EMAIL + "  ", PASSWORD).run();

        assertEquals("owner@example.com", savedAdmin().getEmail());
    }

    /*
     * Checks: Only a hashed (BCrypt) password is saved, never the plain text.
     * How:    The saved password must be different from the original, and the BCrypt encoder must say
     *   it matches the original.
     */
    @Test
    void storesOnlyAHashedPassword() throws Exception {
        when(repository.count()).thenReturn(0L);

        runner(EMAIL, PASSWORD).run();

        String stored = savedAdmin().getPassword();
        assertNotEquals(PASSWORD, stored);
        assertTrue(encoder.matches(PASSWORD, stored));
    }

    /*
     * Checks: No new admin is created when an admin already exists.
     * How:    The fake repository says count is 1. We verify that save is never called.
     */
    @Test
    void doesNotCreateAnAdminWhenOneAlreadyExists() throws Exception {
        when(repository.count()).thenReturn(1L);

        runner(EMAIL, PASSWORD).run();

        verify(repository, never()).save(any(AdminUser.class));
    }

    /*
     * Checks: The admin is not created again after the admin changed their email.
     * How:    One admin exists, but its email is not the configured one. The initializer must still
     *   not save a second admin.
     */
    @Test
    void doesNotRecreateTheAdminAfterTheAdminChangedTheirEmail() throws Exception {
        // The only admin now has a different email than the configured one.
        when(repository.count()).thenReturn(1L);
        when(repository.existsByEmail("owner@example.com")).thenReturn(false);

        runner(EMAIL, PASSWORD).run();

        verify(repository, never()).save(any(AdminUser.class));
    }

    /*
     * Checks: No admin is created when the email setting is empty.
     * How:    We run it with an empty email and a spaces-only email. save must never be called.
     */
    @Test
    void createsNoAdminWhenTheEmailIsNotConfigured() throws Exception {
        runner("", PASSWORD).run();
        runner("   ", PASSWORD).run();

        verify(repository, never()).save(any(AdminUser.class));
    }

    /*
     * Checks: No admin is created when the password setting is empty.
     * How:    We run it with an empty password and a spaces-only password. save must never be called.
     */
    @Test
    void createsNoAdminWhenThePasswordIsNotConfigured() throws Exception {
        runner(EMAIL, "").run();
        runner(EMAIL, "   ").run();

        verify(repository, never()).save(any(AdminUser.class));
    }
}