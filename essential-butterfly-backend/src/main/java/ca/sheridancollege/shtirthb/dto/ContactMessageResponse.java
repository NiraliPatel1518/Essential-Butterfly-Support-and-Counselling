package ca.sheridancollege.shtirthb.dto;

import java.time.LocalDateTime;

public class ContactMessageResponse {

    private Long id;
    private String fullName;
    private String email;
    private String subject;
    private String message;
    private String status;
    private LocalDateTime createdAt;

    public ContactMessageResponse(
            Long id,
            String fullName,
            String email,
            String subject,
            String message,
            String status,
            LocalDateTime createdAt) {

        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.subject = subject;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}