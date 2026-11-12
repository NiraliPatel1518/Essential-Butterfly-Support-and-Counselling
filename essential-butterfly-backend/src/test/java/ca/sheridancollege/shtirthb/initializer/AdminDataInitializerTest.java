package ca.sheridancollege.shtirthb.initializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    private static final String DEFAULT_EMAIL = "admin@test.com";

    private AdminUserRepository repository;
    private PasswordEncoder encoder;
    private CommandLineRunner runner;

    @BeforeEach
    void setUp() {
        repository = mock(AdminUserRepository.class);
        encoder = new BCryptPasswordEncoder();
        runner = new AdminDataInitializer().createDefaultAdmin(repository, encoder);
    }

    @Test
    void createsTheDefaultAdminWhenNoneExists() throws Exception {
        when(repository.existsByEmail(DEFAULT_EMAIL)).thenReturn(false);

        runner.run();

        ArgumentCaptor<AdminUser> saved = ArgumentCaptor.forClass(AdminUser.class);
        verify(repository).save(saved.capture());
        assertEquals("Administrator", saved.getValue().getFullName());
        assertEquals(DEFAULT_EMAIL, saved.getValue().getEmail());
    }

    @Test
    void storesOnlyAHashedPassword() throws Exception {
        when(repository.existsByEmail(DEFAULT_EMAIL)).thenReturn(false);

        runner.run();

        ArgumentCaptor<AdminUser> saved = ArgumentCaptor.forClass(AdminUser.class);
        verify(repository).save(saved.capture());
        String stored = saved.getValue().getPassword();
        assertNotEquals("Admin@12345", stored);
        assertEquals(true, encoder.matches("Admin@12345", stored));
    }

    @Test
    void doesNotCreateASecondAdminWhenOneAlreadyExists() throws Exception {
        when(repository.existsByEmail(DEFAULT_EMAIL)).thenReturn(true);

        runner.run();

        verify(repository, never()).save(any(AdminUser.class));
    }
}