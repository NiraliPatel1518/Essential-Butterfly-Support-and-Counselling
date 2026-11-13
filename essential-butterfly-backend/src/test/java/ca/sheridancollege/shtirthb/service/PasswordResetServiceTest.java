package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
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

    @Mock private ClientUserRepository clients;
    @Mock private AdminUserRepository admins;
    @Mock private PasswordResetTokenRepository tokens;
    @Mock private PasswordEncoder encoder;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(clients, admins, tokens, encoder, "http://localhost:5173");
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void requestResetCreatesHashedClientTokenWithThirtyMinuteExpiry() {
        ClientUser client = new ClientUser("Jane", "jane@mail.com", "OLD");
        when(clients.findByEmail("jane@mail.com")).thenReturn(Optional.of(client));
        when(admins.findByEmail("jane@mail.com")).thenReturn(Optional.empty());
        LocalDateTime before = LocalDateTime.now();

        service.requestPasswordReset(" Jane@Mail.com ");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertEquals("CLIENT", saved.getUserType());
        assertEquals(client, saved.getClientUser());
        assertEquals(64, saved.getTokenHash().length());
        assertTrue(saved.getExpiresAt().isAfter(before.plusMinutes(29)));
        assertTrue(saved.getExpiresAt().isBefore(before.plusMinutes(31)));
        assertFalse(saved.isUsed());
        verify(tokens).revokeActiveForClient(eq(client), any(LocalDateTime.class));
    }

    @Test
    void requestResetCreatesAdminToken() {
        AdminUser admin = new AdminUser("Admin", "admin@mail.com", "OLD");
        when(clients.findByEmail("admin@mail.com")).thenReturn(Optional.empty());
        when(admins.findByEmail("admin@mail.com")).thenReturn(Optional.of(admin));

        service.requestPasswordReset("admin@mail.com");

        verify(tokens).save(argThat(token -> token.getAdminUser() == admin
                && token.getUserType().equals("ADMIN")
                && token.getTokenHash().length() == 64));
        verify(tokens).revokeActiveForAdmin(eq(admin), any(LocalDateTime.class));
    }

    @Test
    void requestResetDoesNothingForUnknownOrAmbiguousEmail() {
        when(clients.findByEmail("unknown@mail.com")).thenReturn(Optional.empty());
        when(admins.findByEmail("unknown@mail.com")).thenReturn(Optional.empty());
        service.requestPasswordReset("unknown@mail.com");
        ClientUser client = new ClientUser("Jane", "both@mail.com", "OLD");
        AdminUser admin = new AdminUser("Admin", "both@mail.com", "OLD");
        when(clients.findByEmail("both@mail.com")).thenReturn(Optional.of(client));
        when(admins.findByEmail("both@mail.com")).thenReturn(Optional.of(admin));
        service.requestPasswordReset("both@mail.com");
        verify(tokens, never()).save(any());
    }

    @Test
    void resetUpdatesClientPasswordAndConsumesMatchingHashedToken() {
        String rawToken = "client-reset-token";
        ClientUser client = new ClientUser("Jane", "jane@mail.com", "OLD");
        PasswordResetToken token = PasswordResetToken.forClient(hash(rawToken), client, LocalDateTime.now().plusMinutes(5));
        when(tokens.findByTokenHash(hash(rawToken))).thenReturn(Optional.of(token));
        when(encoder.encode("NewPassword123")).thenReturn("HASH");

        assertTrue(service.resetPassword(rawToken, "NewPassword123"));
        assertEquals("HASH", client.getPassword());
        assertTrue(token.isUsed());
        verify(clients).save(client);
        verify(tokens).save(token);
    }

    @Test
    void resetUpdatesAdminAndClearsLockout() {
        String rawToken = "admin-reset-token";
        AdminUser admin = new AdminUser("Admin", "admin@mail.com", "OLD");
        admin.setFailedLoginAttempts(5);
        admin.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        PasswordResetToken token = PasswordResetToken.forAdmin(hash(rawToken), admin, LocalDateTime.now().plusMinutes(5));
        when(tokens.findByTokenHash(hash(rawToken))).thenReturn(Optional.of(token));
        when(encoder.encode("NewPassword123")).thenReturn("HASH");

        assertTrue(service.resetPassword(rawToken, "NewPassword123"));
        assertEquals("HASH", admin.getPassword());
        assertEquals(0, admin.getFailedLoginAttempts());
        assertNull(admin.getLockedUntil());
        assertTrue(token.isUsed());
        verify(admins).save(admin);
        verify(tokens).save(token);
    }

    @Test
    void resetRejectsMissingUnknownUsedExpiredAndShortTokens() {
        assertFalse(service.resetPassword(null, "NewPassword123"));
        assertFalse(service.resetPassword("x", "short"));
        when(tokens.findByTokenHash(hash("missing"))).thenReturn(Optional.empty());
        assertFalse(service.resetPassword("missing", "NewPassword123"));

        ClientUser client = new ClientUser("Jane", "jane@mail.com", "OLD");
        PasswordResetToken used = PasswordResetToken.forClient(hash("used"), client, LocalDateTime.now().plusMinutes(5));
        used.setUsed(true);
        when(tokens.findByTokenHash(hash("used"))).thenReturn(Optional.of(used));
        assertFalse(service.resetPassword("used", "NewPassword123"));

        PasswordResetToken expired = PasswordResetToken.forClient(hash("expired"), client, LocalDateTime.now().minusMinutes(1));
        when(tokens.findByTokenHash(hash("expired"))).thenReturn(Optional.of(expired));
        assertFalse(service.resetPassword("expired", "NewPassword123"));
        verifyNoInteractions(encoder);
        verify(clients, never()).save(any());
        verify(admins, never()).save(any());
    }
}