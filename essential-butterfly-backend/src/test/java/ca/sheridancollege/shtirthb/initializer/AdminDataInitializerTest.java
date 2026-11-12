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

    @Test
    void createsTheAdminFromTheConfiguredEmailAndPasswordWhenThereAreNoAdmins() throws Exception {
        when(repository.count()).thenReturn(0L);

        runner(EMAIL, PASSWORD).run();

        assertEquals("Administrator", savedAdmin().getFullName());
    }

    @Test
    void theEmailIsTrimmedAndSavedInLowerCase() throws Exception {
        when(repository.count()).thenReturn(0L);

        runner("  " + EMAIL + "  ", PASSWORD).run();

        assertEquals("owner@example.com", savedAdmin().getEmail());
    }

    @Test
    void storesOnlyAHashedPassword() throws Exception {
        when(repository.count()).thenReturn(0L);

        runner(EMAIL, PASSWORD).run();

        String stored = savedAdmin().getPassword();
        assertNotEquals(PASSWORD, stored);
        assertTrue(encoder.matches(PASSWORD, stored));
    }

    @Test
    void doesNotCreateAnAdminWhenOneAlreadyExists() throws Exception {
        when(repository.count()).thenReturn(1L);

        runner(EMAIL, PASSWORD).run();

        verify(repository, never()).save(any(AdminUser.class));
    }

    @Test
    void doesNotRecreateTheAdminAfterTheAdminChangedTheirEmail() throws Exception {
        // The only admin now has a different email than the configured one.
        when(repository.count()).thenReturn(1L);
        when(repository.existsByEmail("owner@example.com")).thenReturn(false);

        runner(EMAIL, PASSWORD).run();

        verify(repository, never()).save(any(AdminUser.class));
    }

    @Test
    void createsNoAdminWhenTheEmailIsNotConfigured() throws Exception {
        runner("", PASSWORD).run();
        runner("   ", PASSWORD).run();

        verify(repository, never()).save(any(AdminUser.class));
    }

    @Test
    void createsNoAdminWhenThePasswordIsNotConfigured() throws Exception {
        runner(EMAIL, "").run();
        runner(EMAIL, "   ").run();

        verify(repository, never()).save(any(AdminUser.class));
    }
}