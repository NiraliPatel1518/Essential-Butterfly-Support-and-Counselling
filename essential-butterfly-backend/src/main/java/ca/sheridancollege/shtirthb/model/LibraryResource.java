package ca.sheridancollege.shtirthb.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.*;

/*
 * One entry of the public Resource Library: either a web link or an uploaded
 * document / image (the file bytes live in LibraryResourceFile).
 *
 * Status DRAFT      - saved, not visible to visitors
 * Status PUBLISHED  - visible in the public Resource Library
 * Status DELETED    - removed by an administrator; kept only for the audit
 *                     history and never shown to anyone
 */
@Entity
@Table(name = "library_resources")
public class LibraryResource {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_DELETED = "DELETED";

    public static final String TYPE_LINK = "LINK";
    public static final String TYPE_FILE = "FILE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 10)
    private String resourceType;

    @Column(length = 2000)
    private String linkUrl;

    // Links two to five, one per line (the first link is linkUrl).
    @Column(columnDefinition = "text")
    private String extraLinks;

    @Column(length = 255)
    private String fileName;

    @Column(length = 100)
    private String fileContentType;

    private Long fileSize;

    @Column(length = 300)
    private String altText;

    @Column(nullable = false, length = 12)
    private String status;

    private LocalDateTime publishedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected LibraryResource() {
    }

    public LibraryResource(
            String title,
            String description,
            String category,
            String altText) {

        this.title = title;
        this.description = description;
        this.category = category;
        this.altText = altText;
        this.status = STATUS_DRAFT;
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

    /* The web links, first one first. An empty list means "no link". */
    public void setLinks(List<String> links) {
        this.linkUrl = links.isEmpty() ? null : links.get(0);
        this.extraLinks = links.size() > 1
                ? String.join("\n", links.subList(1, links.size()))
                : null;
        refreshType();
    }

    public List<String> getLinks() {
        List<String> links = new ArrayList<>();

        if (linkUrl != null) {
            links.add(linkUrl);
        }

        if (extraLinks != null && !extraLinks.isBlank()) {
            links.addAll(Arrays.asList(extraLinks.split("\n")));
        }

        return links;
    }

    public void setFile(String name, String contentType, long size) {
        this.fileName = name;
        this.fileContentType = contentType;
        this.fileSize = size;
        refreshType();
    }

    public void clearFile() {
        this.fileName = null;
        this.fileContentType = null;
        this.fileSize = null;
        refreshType();
    }

    // FILE when a document or image is attached, otherwise LINK.
    private void refreshType() {
        this.resourceType = fileName != null ? TYPE_FILE : TYPE_LINK;
    }

    public void changeStatus(String newStatus) {
        this.status = newStatus;

        if (STATUS_PUBLISHED.equals(newStatus) && publishedAt == null) {
            this.publishedAt = LocalDateTime.now();
        }
    }

    public boolean isFile() {
        return fileName != null;
    }

    public boolean isImage() {
        return fileContentType != null && fileContentType.startsWith("image/");
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getResourceType() { return resourceType; }
    public String getLinkUrl() { return linkUrl; }
    public String getFileName() { return fileName; }
    public String getFileContentType() { return fileContentType; }
    public Long getFileSize() { return fileSize; }
    public String getAltText() { return altText; }
    public String getStatus() { return status; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setCategory(String category) { this.category = category; }
    public void setAltText(String altText) { this.altText = altText; }
}
