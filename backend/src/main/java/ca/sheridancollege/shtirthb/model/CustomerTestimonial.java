package ca.sheridancollege.shtirthb.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_testimonials")
public class CustomerTestimonial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_user_id", nullable = false)
    private ClientUser client;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(nullable = false)
    private boolean publicationConsent;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(nullable = false)
    private boolean displayOnPublic = false;

    @Column(nullable = false)
    private boolean featuredOnHome = false;

    @ElementCollection
    @CollectionTable(name = "testimonial_photos", joinColumns = @JoinColumn(name = "testimonial_id"))
    @Column(name = "photo_data", columnDefinition = "TEXT")
    @OrderColumn(name = "photo_order")
    private List<String> photos = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime reviewedAt;

    protected CustomerTestimonial() {}

    public CustomerTestimonial(ClientUser client, String displayName, String content,
            boolean publicationConsent, List<String> photos) {
        this.client = client;
        this.displayName = displayName;
        this.content = content;
        this.publicationConsent = publicationConsent;
        this.photos = photos == null ? new ArrayList<>() : new ArrayList<>(photos);
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public ClientUser getClient() { return client; }
    public String getDisplayName() { return displayName; }
    public String getContent() { return content; }
    public boolean isPublicationConsent() { return publicationConsent; }
    public String getStatus() { return status; }
    public boolean isDisplayOnPublic() { return displayOnPublic; }
    public boolean isFeaturedOnHome() { return featuredOnHome; }
    public List<String> getPhotos() { return photos; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setStatus(String status) { this.status = status; this.reviewedAt = LocalDateTime.now(); }
    public void setDisplayOnPublic(boolean value) { this.displayOnPublic = value; }
    public void setFeaturedOnHome(boolean value) { this.featuredOnHome = value; }
}
