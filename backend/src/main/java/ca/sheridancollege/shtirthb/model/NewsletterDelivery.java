
package ca.sheridancollege.shtirthb.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(
    name = "newsletter_deliveries",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"campaign_id", "subscriber_id"}
    )
)
public class NewsletterDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private NewsletterCampaign campaign;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private NewsletterSubscriber subscriber;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(length = 1000)
    private String errorMessage;

    private LocalDateTime sentAt;

    protected NewsletterDelivery() {}

    public NewsletterDelivery(
            NewsletterCampaign campaign,
            NewsletterSubscriber subscriber) {
        this.campaign = campaign;
        this.subscriber = subscriber;
        this.status = "PENDING";
    }

    public void markSent() {
        this.status = "SENT";
        this.errorMessage = null;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailed(String message) {
        this.status = "FAILED";
        this.errorMessage = message == null
            ? "Email delivery failed."
            : message.substring(0, Math.min(message.length(), 1000));
        this.sentAt = null;
    }

    public Long getId() { return id; }
    public NewsletterCampaign getCampaign() { return campaign; }
    public NewsletterSubscriber getSubscriber() { return subscriber; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getSentAt() { return sentAt; }
}
