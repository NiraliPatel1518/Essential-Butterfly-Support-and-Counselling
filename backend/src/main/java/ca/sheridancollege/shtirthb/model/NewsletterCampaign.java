
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
    private int recipientCount;

    @Column(nullable = false)
    private int successCount;

    @Column(nullable = false)
    private int failedCount;

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

    public void recordResults(int recipients, int successful, int failed) {
        this.recipientCount = recipients;
        this.successCount = successful;
        this.failedCount = failed;
        this.sentAt = LocalDateTime.now();

        if (failed == 0) {
            this.status = "SENT";
        } else if (successful == 0) {
            this.status = "FAILED";
        } else {
            this.status = "PARTIAL";
        }
    }

    public void recordRetryResults(int successful, int failed) {
        this.successCount += successful;
        this.failedCount = failed;
        this.sentAt = LocalDateTime.now();

        this.status = failed == 0 ? "SENT" : "PARTIAL";
    }

    public Long getId() { return id; }
    public String getSubject() { return subject; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
    public int getRecipientCount() { return recipientCount; }
    public int getSuccessCount() { return successCount; }
    public int getFailedCount() { return failedCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }
}
