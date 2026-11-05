package ca.sheridancollege.shtirthb.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @Column(nullable = false)
    private String userType;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used;

    public PasswordResetToken() {
    }

    public PasswordResetToken(
            String token,
            String userType,
            Long userId,
            LocalDateTime expiresAt) {

        this.token = token;
        this.userType = userType;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.used = false;
    }

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public String getUserType() {
        return userType;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }
}