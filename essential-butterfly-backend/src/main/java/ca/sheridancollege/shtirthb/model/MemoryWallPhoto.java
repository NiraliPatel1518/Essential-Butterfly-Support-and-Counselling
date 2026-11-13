package ca.sheridancollege.shtirthb.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/*
 * One photo that can appear on the public Memory Wall.
 *
 * ADMIN_UPLOAD  the file is in the photo store under storageKey.
 * TESTIMONIAL   a photo a client attached to a testimonial. The database adds
 *               these rows itself (a trigger), and the picture stays with the
 *               testimonial; nothing is copied.
 */
@Entity
@Table(name = "memory_wall_photos")
public class MemoryWallPhoto {

    public static final String ADMIN_UPLOAD = "ADMIN_UPLOAD";
    public static final String TESTIMONIAL_PHOTO = "TESTIMONIAL";

    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String UNPUBLISHED = "UNPUBLISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, updatable = false)
    private String source;

    @Column(length = 120, updatable = false)
    private String storageKey;

    @Column(length = 255, updatable = false)
    private String originalFilename;

    @Column(name = "testimonial_id", insertable = false, updatable = false)
    private Long testimonialId;

    // Read only: the testimonial a TESTIMONIAL photo belongs to.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "testimonial_id", insertable = false, updatable = false)
    private CustomerTestimonial testimonial;

    @Column(insertable = false, updatable = false)
    private Integer photoOrder;

    @Column(nullable = false, length = 30, updatable = false)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private int sizeBytes;

    @Column(length = 200)
    private String description;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(updatable = false)
    private Long uploadedByAdminId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected MemoryWallPhoto() {
    }

    // A photo uploaded by the administrator. It always starts pending.
    public MemoryWallPhoto(
            String storageKey,
            String originalFilename,
            String contentType,
            int sizeBytes,
            String description,
            Long uploadedByAdminId,
            LocalDateTime now) {

        this.source = ADMIN_UPLOAD;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.description = description;
        this.uploadedByAdminId = uploadedByAdminId;
        this.status = PENDING_REVIEW;
        this.uploadedAt = now;
        this.updatedAt = now;
    }

    public void publish(LocalDateTime now) {
        this.status = PUBLISHED;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void unpublish(LocalDateTime now) {
        this.status = UNPUBLISHED;
        this.publishedAt = null;
        this.updatedAt = now;
    }

    public void describe(String description, LocalDateTime now) {
        this.description = description;
        this.updatedAt = now;
    }

    public boolean isPublished() {
        return PUBLISHED.equals(status);
    }

    public boolean isFromTestimonial() {
        return TESTIMONIAL_PHOTO.equals(source);
    }

    public Long getId() { return id; }
    public String getSource() { return source; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public Long getTestimonialId() { return testimonialId; }
    public CustomerTestimonial getTestimonial() { return testimonial; }
    public Integer getPhotoOrder() { return photoOrder; }
    public String getContentType() { return contentType; }
    public int getSizeBytes() { return sizeBytes; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public Long getUploadedByAdminId() { return uploadedByAdminId; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
