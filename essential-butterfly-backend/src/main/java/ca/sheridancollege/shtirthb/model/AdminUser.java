package ca.sheridancollege.shtirthb.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/*
 * An administrator account.
 *
 * E-mail addresses live in account_emails (AccountEmail):
 *   - the primary row is the sign-in address (always verified)
 *   - the single non-primary row is a PENDING e-mail change, together with
 *     its verification code state
 *
 * The accessor methods keep the original API (getEmail(),
 * getPendingEmail(), getEmailChangeOtp...) so services do not need to know
 * where the data is stored.
 *
 * The two-factor sign-in challenge columns stay on this table: they are
 * one-to-one attributes of the account and are cleared after each login.
 */
@Entity
@Table(name = "admin_users")
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int failedLoginAttempts = 0;

    @Column
    private LocalDateTime lockedUntil;

    @OneToMany(
            mappedBy = "adminUser",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<AccountEmail> emails = new ArrayList<>();

    // ==========================================
    // ADMIN LOGIN TWO-FACTOR AUTHENTICATION
    // ==========================================

    @Column(length = 100)
    private String loginChallengeTokenHash;

    @Column(length = 100)
    private String loginOtpHash;

    private LocalDateTime loginOtpExpiry;

    private LocalDateTime loginOtpLastSentAt;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int loginOtpAttempts = 0;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int loginOtpResendCount = 0;

    public AdminUser() {
    }

    public AdminUser(
            String fullName,
            String email,
            String password) {

        this.fullName = fullName;
        this.password = password;
        this.createdAt = LocalDateTime.now();
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;

        AccountEmail primary = new AccountEmail(this, email, true);
        primary.setVerifiedAt(LocalDateTime.now());
        this.emails.add(primary);
    }

    // ==========================================
    // BASIC GETTERS AND SETTERS
    // ==========================================

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    // ==========================================
    // LOGIN OTP GETTERS AND SETTERS
    // ==========================================

    public String getLoginChallengeTokenHash() {
        return loginChallengeTokenHash;
    }

    public void setLoginChallengeTokenHash(String value) {
        this.loginChallengeTokenHash = value;
    }

    public String getLoginOtpHash() {
        return loginOtpHash;
    }

    public void setLoginOtpHash(String value) {
        this.loginOtpHash = value;
    }

    public LocalDateTime getLoginOtpExpiry() {
        return loginOtpExpiry;
    }

    public void setLoginOtpExpiry(LocalDateTime value) {
        this.loginOtpExpiry = value;
    }

    public LocalDateTime getLoginOtpLastSentAt() {
        return loginOtpLastSentAt;
    }

    public void setLoginOtpLastSentAt(LocalDateTime value) {
        this.loginOtpLastSentAt = value;
    }

    public int getLoginOtpAttempts() {
        return loginOtpAttempts;
    }

    public void setLoginOtpAttempts(int value) {
        this.loginOtpAttempts = value;
    }

    public int getLoginOtpResendCount() {
        return loginOtpResendCount;
    }

    public void setLoginOtpResendCount(int value) {
        this.loginOtpResendCount = value;
    }

    // ==========================================
    // E-MAIL (account_emails)
    // ==========================================

    public List<AccountEmail> getEmails() {
        return emails;
    }

    private AccountEmail primaryRecord() {

        for (AccountEmail record : emails) {
            if (record.isPrimaryEmail()) {
                return record;
            }
        }

        return null;
    }

    private AccountEmail pendingRecord() {

        for (AccountEmail record : emails) {
            if (!record.isPrimaryEmail()) {
                return record;
            }
        }

        return null;
    }

    public String getEmail() {

        AccountEmail primary = primaryRecord();

        return primary == null ? null : primary.getEmail();
    }

    /*
     * Replaces the sign-in address. An administrator's sign-in address is
     * always verified, so callers must only use this after the new address
     * has been verified (the e-mail change flow does).
     *
     * If the same address is currently held as the pending change, remove
     * the pending change first (setPendingEmail(null)) and flush, otherwise
     * the database's global uniqueness rule is violated for a moment.
     */
    public void setEmail(String email) {

        AccountEmail primary = primaryRecord();

        if (primary == null) {

            AccountEmail created = new AccountEmail(this, email, true);
            created.setVerifiedAt(LocalDateTime.now());
            emails.add(created);
            return;
        }

        String normalized = AccountEmail.normalize(email);

        if (normalized != null && !normalized.equals(primary.getEmail())) {
            primary.setEmail(normalized);
            primary.setVerifiedAt(LocalDateTime.now());
        }
    }

    public String getPendingEmail() {

        AccountEmail pending = pendingRecord();

        return pending == null ? null : pending.getEmail();
    }

    /*
     * Sets (or, with null, cancels) the pending e-mail change.
     */
    public void setPendingEmail(String value) {

        AccountEmail pending = pendingRecord();

        if (value == null) {

            if (pending != null) {
                emails.remove(pending);
            }

            return;
        }

        if (pending == null) {
            emails.add(new AccountEmail(this, value, false));
        } else {
            pending.setEmail(value);
        }
    }

    public String getEmailChangeOtpHash() {

        AccountEmail pending = pendingRecord();

        return pending == null ? null : pending.getCodeHash();
    }

    public void setEmailChangeOtpHash(String value) {

        AccountEmail pending = pendingRecord();

        if (pending != null) {
            pending.setCodeHash(value);
        }
    }

    public LocalDateTime getEmailChangeOtpExpiry() {

        AccountEmail pending = pendingRecord();

        return pending == null ? null : pending.getCodeExpiresAt();
    }

    public void setEmailChangeOtpExpiry(LocalDateTime value) {

        AccountEmail pending = pendingRecord();

        if (pending != null) {
            pending.setCodeExpiresAt(value);
        }
    }

    public LocalDateTime getEmailChangeOtpLastSentAt() {

        AccountEmail pending = pendingRecord();

        return pending == null ? null : pending.getCodeLastSentAt();
    }

    public void setEmailChangeOtpLastSentAt(LocalDateTime value) {

        AccountEmail pending = pendingRecord();

        if (pending != null) {
            pending.setCodeLastSentAt(value);
        }
    }

    public int getEmailChangeOtpAttempts() {

        AccountEmail pending = pendingRecord();

        return pending == null ? 0 : pending.getCodeAttempts();
    }

    public void setEmailChangeOtpAttempts(int value) {

        AccountEmail pending = pendingRecord();

        if (pending != null) {
            pending.setCodeAttempts(value);
        }
    }

    public int getEmailChangeOtpResendCount() {

        AccountEmail pending = pendingRecord();

        return pending == null ? 0 : pending.getCodeResendCount();
    }

    public void setEmailChangeOtpResendCount(int value) {

        AccountEmail pending = pendingRecord();

        if (pending != null) {
            pending.setCodeResendCount(value);
        }
    }
}
