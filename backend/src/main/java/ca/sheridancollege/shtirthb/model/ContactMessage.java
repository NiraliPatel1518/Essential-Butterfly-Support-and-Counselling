package ca.sheridancollege.shtirthb.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "contact_messages")
public class ContactMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_user_id", nullable = true)
    private ClientUser clientUser;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public ContactMessage() {
    }

    public ContactMessage(
            String fullName,
            String email,
            String subject,
            String message
    ) {
        this.clientUser = null;
        this.fullName = fullName;
        this.email = email;
        this.subject = subject;
        this.message = message;
        this.status = "NEW";
        this.createdAt = LocalDateTime.now();
    }

    public ContactMessage(
            ClientUser clientUser,
            String fullName,
            String email,
            String subject,
            String message
    ) {
        this.clientUser = clientUser;
        this.fullName = fullName;
        this.email = email;
        this.subject = subject;
        this.message = message;
        this.status = "NEW";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public ClientUser getClientUser() {
        return clientUser;
    }

    public void setClientUser(ClientUser clientUser) {
        this.clientUser = clientUser;
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

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}