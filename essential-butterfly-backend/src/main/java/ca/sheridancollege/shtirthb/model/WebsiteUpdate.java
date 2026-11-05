package ca.sheridancollege.shtirthb.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "website_updates")
public class WebsiteUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean published = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "website_update_photos",
        joinColumns = @JoinColumn(name = "update_id")
    )
    @OrderColumn(name = "photo_order")
    @Column(name = "photo_data", nullable = false, columnDefinition = "TEXT")
    private List<String> photos = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "website_update_social_links",
        joinColumns = @JoinColumn(name = "update_id")
    )
    @OrderColumn(name = "link_order")
    @Column(name = "social_link", nullable = false, length = 1000)
    private List<String> socialLinks = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected WebsiteUpdate() {}

    public WebsiteUpdate(String title, String content, boolean published) {
        this.title = title;
        this.content = content;
        this.published = published;
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

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isPublished() { return published; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<String> getPhotos() { return photos; }
    public List<String> getSocialLinks() { return socialLinks; }

    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void setPublished(boolean published) { this.published = published; }

    public void setPhotos(List<String> photos) {
        this.photos = photos == null ? new ArrayList<>() : new ArrayList<>(photos);
    }

    public void setSocialLinks(List<String> socialLinks) {
        this.socialLinks = socialLinks == null
            ? new ArrayList<>()
            : new ArrayList<>(socialLinks);
    }
}