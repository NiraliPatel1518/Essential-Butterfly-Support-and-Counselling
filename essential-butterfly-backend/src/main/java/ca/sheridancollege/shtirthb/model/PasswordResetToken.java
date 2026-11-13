package ca.sheridancollege.shtirthb.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/*
 * A password reset request.
 *
 *   - exactly one owner: an administrator or a client (real foreign keys,
 *     the row disappears with the account)
 *   - only the SHA-256 hash of the secret token is stored; the secret
 *     itself exists only in the link that was sent to the user
 *   - usedAt records when the token was consumed or revoked
 *   - the database allows at most one unused token per account
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id", updatable = false)
    private AdminUser adminUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_user_id", updatable = false)
    private ClientUser clientUser;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    protected PasswordResetToken() {
    }

    public static PasswordResetToken forAdmin(
            String tokenHash,
            AdminUser admin,
            LocalDateTime expiresAt) {

        PasswordResetToken token = new PasswordResetToken();
        token.adminUser = admin;
        token.tokenHash = tokenHash;
        token.createdAt = LocalDateTime.now();
        token.expiresAt = expiresAt;

        return token;
    }

    public static PasswordResetToken forClient(
            String tokenHash,
            ClientUser client,
            LocalDateTime expiresAt) {

        PasswordResetToken token = new PasswordResetToken();
        token.clientUser = client;
        token.tokenHash = tokenHash;
        token.createdAt = LocalDateTime.now();
        token.expiresAt = expiresAt;

        return token;
    }

    public Long getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public AdminUser getAdminUser() {
        return adminUser;
    }

    public ClientUser getClientUser() {
        return clientUser;
    }

    /* "ADMIN" or "CLIENT", derived from which owner is set. */
    public String getUserType() {
        return adminUser != null ? "ADMIN" : "CLIENT";
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    /* Marking a token as used is one-way; a used token stays used. */
    public void setUsed(boolean used) {

        if (used && usedAt == null) {
            usedAt = LocalDateTime.now();
        }
    }
}
