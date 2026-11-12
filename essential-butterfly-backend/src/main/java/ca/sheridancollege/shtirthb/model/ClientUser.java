
package ca.sheridancollege.shtirthb.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_users")
public class ClientUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /*
     * Existing accounts remain verified.
     * New signups explicitly set this value to false.
     */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean emailVerified = true;

    @Column(length = 100)
    private String emailVerificationCodeHash;

    private LocalDateTime emailVerificationCodeExpiry;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int emailVerificationAttempts = 0;

    private LocalDateTime emailVerificationLastSentAt;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int emailVerificationResendCount = 0;

    // Phone verification fields
    @Column(length = 20)
    private String phoneNumber;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean phoneVerified = false;

    /*
     * The pending number is separate from the verified number.
     * This prevents an unsuccessful number change from replacing
     * the customer's existing verified phone number.
     */
    @Column(length = 20)
    private String pendingPhoneNumber;

    @Column(length = 100)
    private String phoneVerificationCodeHash;

    private LocalDateTime phoneVerificationCodeExpiry;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int phoneVerificationAttempts = 0;

    private LocalDateTime phoneVerificationLastSentAt;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int phoneVerificationResendCount = 0;

    public ClientUser() {
    }

    public ClientUser(String fullName, String email, String password) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.createdAt = LocalDateTime.now();
        this.emailVerified = true;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public boolean isEmailVerified() {
        return Boolean.TRUE.equals(emailVerified);
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getEmailVerificationCodeHash() {
        return emailVerificationCodeHash;
    }

    public void setEmailVerificationCodeHash(String hash) {
        this.emailVerificationCodeHash = hash;
    }

    public LocalDateTime getEmailVerificationCodeExpiry() {
        return emailVerificationCodeExpiry;
    }

    public void setEmailVerificationCodeExpiry(LocalDateTime expiry) {
        this.emailVerificationCodeExpiry = expiry;
    }

    public int getEmailVerificationAttempts() {
        return emailVerificationAttempts;
    }

    public void setEmailVerificationAttempts(int attempts) {
        this.emailVerificationAttempts = attempts;
    }

    public LocalDateTime getEmailVerificationLastSentAt() {
        return emailVerificationLastSentAt;
    }

    public void setEmailVerificationLastSentAt(LocalDateTime sentAt) {
        this.emailVerificationLastSentAt = sentAt;
    }

    public int getEmailVerificationResendCount() {
        return emailVerificationResendCount;
    }

    public void setEmailVerificationResendCount(int resendCount) {
        this.emailVerificationResendCount = resendCount;
    }

    // Phone number getters and setters

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public boolean isPhoneVerified() {
        return Boolean.TRUE.equals(phoneVerified);
    }

    public void setPhoneVerified(boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }

    public String getPendingPhoneNumber() {
        return pendingPhoneNumber;
    }

    public void setPendingPhoneNumber(String pendingPhoneNumber) {
        this.pendingPhoneNumber = pendingPhoneNumber;
    }

    public String getPhoneVerificationCodeHash() {
        return phoneVerificationCodeHash;
    }

    public void setPhoneVerificationCodeHash(String hash) {
        this.phoneVerificationCodeHash = hash;
    }

    public LocalDateTime getPhoneVerificationCodeExpiry() {
        return phoneVerificationCodeExpiry;
    }

    public void setPhoneVerificationCodeExpiry(LocalDateTime expiry) {
        this.phoneVerificationCodeExpiry = expiry;
    }

    public int getPhoneVerificationAttempts() {
        return phoneVerificationAttempts;
    }

    public void setPhoneVerificationAttempts(int attempts) {
        this.phoneVerificationAttempts = attempts;
    }

    public LocalDateTime getPhoneVerificationLastSentAt() {
        return phoneVerificationLastSentAt;
    }

    public void setPhoneVerificationLastSentAt(LocalDateTime sentAt) {
        this.phoneVerificationLastSentAt = sentAt;
    }

    public int getPhoneVerificationResendCount() {
        return phoneVerificationResendCount;
    }

    public void setPhoneVerificationResendCount(int resendCount) {
        this.phoneVerificationResendCount = resendCount;
    }
}
