
package ca.sheridancollege.shtirthb.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_users")
public class AdminUser {

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

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int failedLoginAttempts = 0;

    @Column
    private LocalDateTime lockedUntil;

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

    // ==========================================
    // ADMIN EMAIL CHANGE VERIFICATION
    // ==========================================

    @Column(length = 254)
    private String pendingEmail;

    @Column(length = 100)
    private String emailChangeOtpHash;

    private LocalDateTime emailChangeOtpExpiry;

    private LocalDateTime emailChangeOtpLastSentAt;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int emailChangeOtpAttempts = 0;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int emailChangeOtpResendCount = 0;

    public AdminUser() {
    }

    public AdminUser(
            String fullName,
            String email,
            String password) {

        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.createdAt = LocalDateTime.now();
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
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
    // EMAIL CHANGE GETTERS AND SETTERS
    // ==========================================

    public String getPendingEmail() {
        return pendingEmail;
    }

    public void setPendingEmail(String value) {
        this.pendingEmail = value;
    }

    public String getEmailChangeOtpHash() {
        return emailChangeOtpHash;
    }

    public void setEmailChangeOtpHash(String value) {
        this.emailChangeOtpHash = value;
    }

    public LocalDateTime getEmailChangeOtpExpiry() {
        return emailChangeOtpExpiry;
    }

    public void setEmailChangeOtpExpiry(LocalDateTime value) {
        this.emailChangeOtpExpiry = value;
    }

    public LocalDateTime getEmailChangeOtpLastSentAt() {
        return emailChangeOtpLastSentAt;
    }

    public void setEmailChangeOtpLastSentAt(LocalDateTime value) {
        this.emailChangeOtpLastSentAt = value;
    }

    public int getEmailChangeOtpAttempts() {
        return emailChangeOtpAttempts;
    }

    public void setEmailChangeOtpAttempts(int value) {
        this.emailChangeOtpAttempts = value;
    }

    public int getEmailChangeOtpResendCount() {
        return emailChangeOtpResendCount;
    }

    public void setEmailChangeOtpResendCount(int value) {
        this.emailChangeOtpResendCount = value;
    }
}
