package ca.sheridancollege.shtirthb.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/*
 * A phone number that is still being verified.
 *
 * A client account stores ONLY a verified phone number
 * (client_users.phone_number). While a new number is being verified it
 * lives here, with its one-time-code state, and is deleted as soon as the
 * verification succeeds. There is at most one row per client.
 */
@Entity
@Table(name = "phone_verifications")
public class PhoneVerification {

    @Id
    @Column(name = "client_user_id")
    private Long clientUserId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_user_id", insertable = false, updatable = false)
    private ClientUser clientUser;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected PhoneVerification() {
    }

    PhoneVerification(ClientUser clientUser, String phoneNumber) {
        this.clientUserId = clientUser.getId();
        this.clientUser = clientUser;
        this.phoneNumber = phoneNumber;
    }

    public Long getClientUserId() {
        return clientUserId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
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
