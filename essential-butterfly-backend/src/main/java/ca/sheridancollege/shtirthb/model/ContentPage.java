package ca.sheridancollege.shtirthb.model;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.*;

/*
 * One editable public page (Services, About and Credentials, Privacy Policy).
 *
 * The PUBLISHED copy is what visitors read. The DRAFT copy is what the
 * administrator is working on. Publishing copies the draft over the published
 * copy; saving a draft never touches what visitors see.
 */
@Entity
@Table(name = "website_content_pages")
public class ContentPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String pageKey;

    @Column(length = 180)
    private String publishedTitle;

    @Column(columnDefinition = "TEXT")
    private String publishedBody;

    @Column(length = 180)
    private String draftTitle;

    @Column(columnDefinition = "TEXT")
    private String draftBody;

    private LocalDateTime publishedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ContentPage() {
    }

    public ContentPage(String pageKey) {
        this.pageKey = pageKey;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /* Keeps the work in progress without changing the public page. */
    public void saveDraft(String title, String body) {
        this.draftTitle = title;
        this.draftBody = body;
    }

    /* Saves the work in progress and makes it the public page. */
    public void publish(String title, String body) {
        this.draftTitle = title;
        this.draftBody = body;
        this.publishedTitle = title;
        this.publishedBody = body;
        this.publishedAt = LocalDateTime.now();
    }

    public boolean isPublished() {
        return publishedTitle != null && publishedBody != null;
    }

    public boolean hasUnpublishedChanges() {
        if (draftTitle == null && draftBody == null) {
            return false;
        }

        return !Objects.equals(draftTitle, publishedTitle)
                || !Objects.equals(draftBody, publishedBody);
    }

    public Long getId() { return id; }
    public String getPageKey() { return pageKey; }
    public String getPublishedTitle() { return publishedTitle; }
    public String getPublishedBody() { return publishedBody; }
    public String getDraftTitle() { return draftTitle; }
    public String getDraftBody() { return draftBody; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
