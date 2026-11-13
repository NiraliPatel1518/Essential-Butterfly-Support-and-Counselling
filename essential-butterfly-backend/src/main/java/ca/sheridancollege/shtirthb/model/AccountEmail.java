package ca.sheridancollege.shtirthb.model;

import java.time.LocalDateTime;
import java.util.Locale;

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
 * One e-mail address that belongs to exactly one account (an administrator
 * OR a client). The database enforces:
 *
 *   - the address is globally unique (admins, clients and pending changes)
 *   - exactly one primary (sign-in) address per account
 *   - at most one pending (non-primary, unverified) address per account
 *
 * The verification state of the address (code hash, expiry, attempts,
 * resends) lives here, next to the address it verifies.
 */
@Entity
@Table(name = "account_emails")
public class AccountEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id")
    private AdminUser adminUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_user_id")
    private ClientUser clientUser;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "code_hash", length = 100)
    private String codeHash;

    @Column(name = "code_expires_at")
    private LocalDateTime codeExpiresAt;

    @Column(name = "code_last_sent_at")
    private LocalDateTime codeLastSentAt;

    @Column(name = "code_attempts", nullable = false)
    private int codeAttempts = 0;

    @Column(name = "code_resend_count", nullable = false)
    private int codeResendCount = 0;

    protected AccountEmail() {
    }

    AccountEmail(AdminUser owner, String email, boolean primaryEmail) {
        this.adminUser = owner;
        this.email = normalize(email);
        this.primaryEmail = primaryEmail;
        this.createdAt = LocalDateTime.now();
    }

    AccountEmail(ClientUser owner, String email, boolean primaryEmail) {
        this.clientUser = owner;
        this.email = normalize(email);
        this.primaryEmail = primaryEmail;
        this.createdAt = LocalDateTime.now();
    }

    static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public AdminUser getAdminUser() {
        return adminUser;
    }

    public ClientUser getClientUser() {
        return clientUser;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalize(email);
    }

    public boolean isPrimaryEmail() {
        return primaryEmail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public LocalDateTime getCodeExpiresAt() {
        return codeExpiresAt;
    }

    public void setCodeExpiresAt(LocalDateTime codeExpiresAt) {
        this.codeExpiresAt = codeExpiresAt;
    }

    public LocalDateTime getCodeLastSentAt() {
        return codeLastSentAt;
    }

    public void setCodeLastSentAt(LocalDateTime codeLastSentAt) {
        this.codeLastSentAt = codeLastSentAt;
    }

    public int getCodeAttempts() {
        return codeAttempts;
    }

    public void setCodeAttempts(int codeAttempts) {
        this.codeAttempts = codeAttempts;
    }

    public int getCodeResendCount() {
        return codeResendCount;
    }

    public void setCodeResendCount(int codeResendCount) {
        this.codeResendCount = codeResendCount;
    }
}
