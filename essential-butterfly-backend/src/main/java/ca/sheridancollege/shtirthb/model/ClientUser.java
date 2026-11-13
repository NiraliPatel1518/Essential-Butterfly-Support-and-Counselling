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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/*
 * A client account.
 *
 * Normalised storage:
 *   - e-mail addresses (and the verification state of the sign-in address)
 *     live in account_emails (AccountEmail)
 *   - client_users.phone_number holds ONLY a verified phone number
 *   - a number that is still being verified lives in phone_verifications
 *     (PhoneVerification)
 *
 * The accessor methods below keep the original API (getEmail(),
 * isEmailVerified(), getPendingPhoneNumber() ...) so services do not need
 * to know where the data is stored.
 */
@Entity
@Table(name = "client_users")
public class ClientUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // The verified phone number (international format), or null.
    @Column(length = 20)
    private String phoneNumber;

    @OneToMany(
            mappedBy = "clientUser",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<AccountEmail> emails = new ArrayList<>();

    // A phone number that is being verified (at most one).
    @OneToOne(
            mappedBy = "clientUser",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private PhoneVerification phoneVerification;

    public ClientUser() {
    }

    /*
     * Creates an account whose sign-in address is already verified.
     * New signups call setEmailVerified(false) afterwards.
     */
    public ClientUser(String fullName, String email, String password) {
        this.fullName = fullName;
        this.password = password;
        this.createdAt = LocalDateTime.now();

        AccountEmail primary = new AccountEmail(this, email, true);
        primary.setVerifiedAt(LocalDateTime.now());
        this.emails.add(primary);
    }

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

    public String getEmail() {

        AccountEmail primary = primaryRecord();

        return primary == null ? null : primary.getEmail();
    }

    public void setEmail(String email) {

        AccountEmail primary = primaryRecord();

        if (primary == null) {
            emails.add(new AccountEmail(this, email, true));
        } else {
            primary.setEmail(email);
        }
    }

    public boolean isEmailVerified() {

        AccountEmail primary = primaryRecord();

        return primary != null && primary.isVerified();
    }

    public void setEmailVerified(boolean emailVerified) {

        AccountEmail primary = primaryRecord();

        if (primary == null) {
            return;
        }

        if (!emailVerified) {
            primary.setVerifiedAt(null);
        } else if (primary.getVerifiedAt() == null) {
            primary.setVerifiedAt(LocalDateTime.now());
        }
    }

    public String getEmailVerificationCodeHash() {

        AccountEmail primary = primaryRecord();

        return primary == null ? null : primary.getCodeHash();
    }

    public void setEmailVerificationCodeHash(String hash) {

        AccountEmail primary = primaryRecord();

        if (primary != null) {
            primary.setCodeHash(hash);
        }
    }

    public LocalDateTime getEmailVerificationCodeExpiry() {

        AccountEmail primary = primaryRecord();

        return primary == null ? null : primary.getCodeExpiresAt();
    }

    public void setEmailVerificationCodeExpiry(LocalDateTime expiry) {

        AccountEmail primary = primaryRecord();

        if (primary != null) {
            primary.setCodeExpiresAt(expiry);
        }
    }

    public int getEmailVerificationAttempts() {

        AccountEmail primary = primaryRecord();

        return primary == null ? 0 : primary.getCodeAttempts();
    }

    public void setEmailVerificationAttempts(int attempts) {

        AccountEmail primary = primaryRecord();

        if (primary != null) {
            primary.setCodeAttempts(attempts);
        }
    }

    public LocalDateTime getEmailVerificationLastSentAt() {

        AccountEmail primary = primaryRecord();

        return primary == null ? null : primary.getCodeLastSentAt();
    }

    public void setEmailVerificationLastSentAt(LocalDateTime sentAt) {

        AccountEmail primary = primaryRecord();

        if (primary != null) {
            primary.setCodeLastSentAt(sentAt);
        }
    }

    public int getEmailVerificationResendCount() {

        AccountEmail primary = primaryRecord();

        return primary == null ? 0 : primary.getCodeResendCount();
    }

    public void setEmailVerificationResendCount(int resendCount) {

        AccountEmail primary = primaryRecord();

        if (primary != null) {
            primary.setCodeResendCount(resendCount);
        }
    }

    // ==========================================
    // PHONE (client_users.phone_number + phone_verifications)
    // ==========================================

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    /*
     * A phone number is verified if and only if it is stored on the
     * account, so there is no separate flag that could disagree with it.
     */
    public boolean isPhoneVerified() {
        return phoneNumber != null && !phoneNumber.isBlank();
    }

    /*
     * Kept for compatibility. true is implied by having a stored number;
     * false removes the stored number.
     */
    public void setPhoneVerified(boolean phoneVerified) {

        if (!phoneVerified) {
            this.phoneNumber = null;
        }
    }

    public String getPendingPhoneNumber() {

        return phoneVerification == null
                ? null
                : phoneVerification.getPhoneNumber();
    }

    /*
     * null cancels the verification (the row is deleted).
     * A number is stored on the account only by verify().
     */
    public void setPendingPhoneNumber(String pendingPhoneNumber) {

        if (pendingPhoneNumber == null) {
            this.phoneVerification = null;
            return;
        }

        if (phoneVerification == null) {
            this.phoneVerification =
                    new PhoneVerification(this, pendingPhoneNumber);

        } else if (!pendingPhoneNumber.equals(
                phoneVerification.getPhoneNumber())) {

            phoneVerification.setPhoneNumber(pendingPhoneNumber);
        }
    }

    public String getPhoneVerificationCodeHash() {

        return phoneVerification == null
                ? null
                : phoneVerification.getCodeHash();
    }

    public void setPhoneVerificationCodeHash(String hash) {

        if (phoneVerification != null) {
            phoneVerification.setCodeHash(hash);
        }
    }

    public LocalDateTime getPhoneVerificationCodeExpiry() {

        return phoneVerification == null
                ? null
                : phoneVerification.getCodeExpiresAt();
    }

    public void setPhoneVerificationCodeExpiry(LocalDateTime expiry) {

        if (phoneVerification != null) {
            phoneVerification.setCodeExpiresAt(expiry);
        }
    }

    public int getPhoneVerificationAttempts() {

        return phoneVerification == null
                ? 0
                : phoneVerification.getCodeAttempts();
    }

    public void setPhoneVerificationAttempts(int attempts) {

        if (phoneVerification != null) {
            phoneVerification.setCodeAttempts(attempts);
        }
    }

    public LocalDateTime getPhoneVerificationLastSentAt() {

        return phoneVerification == null
                ? null
                : phoneVerification.getCodeLastSentAt();
    }

    public void setPhoneVerificationLastSentAt(LocalDateTime sentAt) {

        if (phoneVerification != null) {
            phoneVerification.setCodeLastSentAt(sentAt);
        }
    }

    public int getPhoneVerificationResendCount() {

        return phoneVerification == null
                ? 0
                : phoneVerification.getCodeResendCount();
    }

    public void setPhoneVerificationResendCount(int resendCount) {

        if (phoneVerification != null) {
            phoneVerification.setCodeResendCount(resendCount);
        }
    }
}
