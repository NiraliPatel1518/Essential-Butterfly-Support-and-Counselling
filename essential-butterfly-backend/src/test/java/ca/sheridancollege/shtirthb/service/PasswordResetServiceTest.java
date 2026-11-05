
package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.PasswordResetToken;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.PasswordResetTokenRepository;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private ClientUserRepository clients;

    @Mock
    private AdminUserRepository admins;

    @Mock
    private PasswordResetTokenRepository tokens;

    @Mock
    private PasswordEncoder encoder;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                clients, admins, tokens, encoder);
    }

    @Test
    void requestResetCreatesClientTokenWithThirtyMinuteExpiry() {
        ClientUser client = mock(ClientUser.class);

        when(client.getId()).thenReturn(4L);
        when(clients.findByEmail("jane@mail.com"))
                .thenReturn(Optional.of(client));
        when(admins.findByEmail("jane@mail.com"))
                .thenReturn(Optional.empty());

        LocalDateTime before = LocalDateTime.now();

        service.requestPasswordReset(" Jane@Mail.com ");

        ArgumentCaptor<PasswordResetToken> captor =
                ArgumentCaptor.forClass(PasswordResetToken.class);

        verify(tokens).save(captor.capture());

        PasswordResetToken saved = captor.getValue();

        assertEquals("CLIENT", saved.getUserType());
        assertEquals(4L, saved.getUserId());
        assertFalse(saved.isUsed());

        assertTrue(saved.getExpiresAt()
                .isAfter(before.plusMinutes(29)));

        assertTrue(saved.getExpiresAt()
                .isBefore(before.plusMinutes(31)));
    }

    @Test
    void requestResetCreatesAdminToken() {
        AdminUser admin = mock(AdminUser.class);

        when(admin.getId()).thenReturn(2L);
        when(clients.findByEmail("admin@mail.com"))
                .thenReturn(Optional.empty());
        when(admins.findByEmail("admin@mail.com"))
                .thenReturn(Optional.of(admin));

        service.requestPasswordReset("admin@mail.com");

        verify(tokens).save(argThat(token ->
                token.getUserType().equals("ADMIN")
                && token.getUserId().equals(2L)));
    }

    @Test
    void requestResetDoesNothingForUnknownEmail() {
        when(clients.findByEmail("unknown@mail.com"))
                .thenReturn(Optional.empty());
        when(admins.findByEmail("unknown@mail.com"))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                service.requestPasswordReset("unknown@mail.com"));

        verify(tokens, never()).save(any());
    }

    @Test
    void requestResetDoesNotCreateTokenForAmbiguousAccount() {
        when(clients.findByEmail("both@mail.com"))
                .thenReturn(Optional.of(mock(ClientUser.class)));

        when(admins.findByEmail("both@mail.com"))
                .thenReturn(Optional.of(mock(AdminUser.class)));

        service.requestPasswordReset("both@mail.com");

        verify(tokens, never()).save(any());
    }

    @Test
    void resetUpdatesClientPasswordAndConsumesToken() {
        PasswordResetToken token = new PasswordResetToken(
                "token", "CLIENT", 7L,
                LocalDateTime.now().plusMinutes(5));

        ClientUser client =
                new ClientUser("Jane", "jane@mail.com", "OLD");

        when(tokens.findByToken("token"))
                .thenReturn(Optional.of(token));

        when(clients.findById(7L))
                .thenReturn(Optional.of(client));

        when(encoder.encode("NewPassword123"))
                .thenReturn("HASH");

        assertTrue(service.resetPassword(
                "token", "NewPassword123"));

        assertEquals("HASH", client.getPassword());
        assertTrue(token.isUsed());

        verify(clients).save(client);
        verify(tokens).save(token);
    }

    @Test
    void resetUpdatesAdminAndClearsLockout() {
        PasswordResetToken token = new PasswordResetToken(
                "admin-token", "ADMIN", 3L,
                LocalDateTime.now().plusMinutes(5));

        AdminUser admin =
                new AdminUser("Admin", "admin@mail.com", "OLD");

        admin.setFailedLoginAttempts(5);
        admin.setLockedUntil(
                LocalDateTime.now().plusMinutes(5));

        when(tokens.findByToken("admin-token"))
                .thenReturn(Optional.of(token));

        when(admins.findById(3L))
                .thenReturn(Optional.of(admin));

        when(encoder.encode("NewPassword123"))
                .thenReturn("HASH");

        assertTrue(service.resetPassword(
                "admin-token", "NewPassword123"));

        assertEquals("HASH", admin.getPassword());
        assertEquals(0, admin.getFailedLoginAttempts());
        assertNull(admin.getLockedUntil());
        assertTrue(token.isUsed());

        verify(admins).save(admin);
        verify(tokens).save(token);
    }

    @Test
    void resetRejectsMissingUsedAndExpiredTokens() {
        assertFalse(service.resetPassword(
                null, "NewPassword123"));

        assertFalse(service.resetPassword(
                "x", "short"));

        when(tokens.findByToken("missing"))
                .thenReturn(Optional.empty());

        assertFalse(service.resetPassword(
                "missing", "NewPassword123"));

        PasswordResetToken used = new PasswordResetToken(
                "used", "CLIENT", 1L,
                LocalDateTime.now().plusMinutes(5));

        used.setUsed(true);

        when(tokens.findByToken("used"))
                .thenReturn(Optional.of(used));

        assertFalse(service.resetPassword(
                "used", "NewPassword123"));

        PasswordResetToken expired = new PasswordResetToken(
                "expired", "CLIENT", 1L,
                LocalDateTime.now().minusMinutes(1));

        when(tokens.findByToken("expired"))
                .thenReturn(Optional.of(expired));

        assertFalse(service.resetPassword(
                "expired", "NewPassword123"));

        verifyNoInteractions(encoder);
        verify(clients, never()).save(any());
        verify(admins, never()).save(any());
    }

    @Test
    void resetRejectsUnknownUserType() {
        PasswordResetToken unknown = new PasswordResetToken(
                "unknown", "OTHER", 1L,
                LocalDateTime.now().plusMinutes(5));

        when(tokens.findByToken("unknown"))
                .thenReturn(Optional.of(unknown));

        when(encoder.encode("NewPassword123"))
                .thenReturn("HASH");

        assertFalse(service.resetPassword(
                "unknown", "NewPassword123"));

        verify(encoder).encode("NewPassword123");

        verify(clients, never()).save(any());
        verify(admins, never()).save(any());
        verify(tokens, never()).save(any());
    }
}
