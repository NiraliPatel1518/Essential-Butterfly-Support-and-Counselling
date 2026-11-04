
package ca.sheridancollege.shtirthb.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(
    name = "newsletter_subscribers",
    uniqueConstraints = @UniqueConstraint(columnNames = "email")
)
public class NewsletterSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, unique = true, length = 36)
    private String unsubscribeToken;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean consentGiven;

    private LocalDateTime consentAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime unsubscribedAt;

    protected NewsletterSubscriber() {}

    public NewsletterSubscriber(String email) {
        this.email = email;
        this.unsubscribeToken = UUID.randomUUID().toString();
        this.active = true;
        this.consentGiven = true;
        this.consentAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    public void resubscribe() {
        this.unsubscribeToken = UUID.randomUUID().toString();
        this.active = true;
        this.consentGiven = true;
        this.consentAt = LocalDateTime.now();
        this.unsubscribedAt = null;
    }

    public void unsubscribe() {
        this.active = false;
        this.unsubscribedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getUnsubscribeToken() { return unsubscribeToken; }
    public boolean isActive() { return active; }
    public boolean isConsentGiven() { return consentGiven; }
    public LocalDateTime getConsentAt() { return consentAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUnsubscribedAt() { return unsubscribedAt; }
}
