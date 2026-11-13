
package ca.sheridancollege.shtirthb.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "newsletter_campaigns")
public class NewsletterCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    protected NewsletterCampaign() {}

    public NewsletterCampaign(String subject, String content) {
        this.subject = subject;
        this.content = content;
        this.status = "DRAFT";
        this.createdAt = LocalDateTime.now();
    }

    public void update(String subject, String content) {
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("Only draft newsletters can be edited.");
        }
        this.subject = subject;
        this.content = content;
    }

    /*
     * Records the outcome of a send or a retry.
     *
     * The counts are NOT stored on the campaign. Callers pass the totals
     * they have just derived from the delivery rows; only the status and
     * the time of the last attempt are kept here.
     */
    public void recordOutcome(long successful, long failed) {
        this.sentAt = LocalDateTime.now();

        if (failed == 0) {
            this.status = "SENT";
        } else if (successful == 0) {
            this.status = "FAILED";
        } else {
            this.status = "PARTIAL";
        }
    }

    public Long getId() { return id; }
    public String getSubject() { return subject; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }
}
