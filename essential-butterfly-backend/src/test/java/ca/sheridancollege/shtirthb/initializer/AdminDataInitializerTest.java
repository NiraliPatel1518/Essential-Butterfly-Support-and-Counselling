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
    void createsTheDefaultAdminWhenThereAreNoAdmins() throws Exception {
        when(repository.count()).thenReturn(0L);

        runner.run();

        ArgumentCaptor<AdminUser> saved = ArgumentCaptor.forClass(AdminUser.class);
        verify(repository).save(saved.capture());
        assertEquals("Administrator", saved.getValue().getFullName());
        assertEquals("admin@test.com", saved.getValue().getEmail());
    }

    @Test
    void storesOnlyAHashedPassword() throws Exception {
        when(repository.count()).thenReturn(0L);

        runner.run();

        ArgumentCaptor<AdminUser> saved = ArgumentCaptor.forClass(AdminUser.class);
        verify(repository).save(saved.capture());
        String stored = saved.getValue().getPassword();
        assertNotEquals("Admin@12345", stored);
        assertEquals(true, encoder.matches("Admin@12345", stored));
    }

    @Test
    void doesNotCreateAnAdminWhenOneAlreadyExists() throws Exception {
        when(repository.count()).thenReturn(1L);

        runner.run();

        verify(repository, never()).save(any(AdminUser.class));
    }

    @Test
    void doesNotRecreateTheDefaultAdminAfterTheAdminChangedTheirEmail() throws Exception {
        // The only admin now has a different email, so admin@test.com does not exist.
        when(repository.count()).thenReturn(1L);
        when(repository.existsByEmail("admin@test.com")).thenReturn(false);

        runner.run();

        verify(repository, never()).save(any(AdminUser.class));
    }
}