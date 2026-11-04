package ca.sheridancollege.shtirthb.service;

import ca.sheridancollege.shtirthb.dto.*;
import ca.sheridancollege.shtirthb.model.*;
import ca.sheridancollege.shtirthb.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CustomerTestimonialService {
    private final CustomerTestimonialRepository testimonials;
    private final ClientUserRepository clients;
    private final FeatureVisibilityRepository features;

    public CustomerTestimonialService(CustomerTestimonialRepository testimonials,
            ClientUserRepository clients, FeatureVisibilityRepository features) {
        this.testimonials = testimonials;
        this.clients = clients;
        this.features = features;
    }

    @Transactional
    public TestimonialResponse submit(String email, TestimonialRequest request) {
        if (request == null || request.getDisplayName() == null ||
                request.getDisplayName().trim().isEmpty() ||
                request.getDisplayName().trim().length() > 80)
            throw new IllegalArgumentException("Enter a public display name (maximum 80 characters).");
        if (request.getContent() == null || request.getContent().trim().length() < 10 ||
                request.getContent().trim().length() > 5000)
            throw new IllegalArgumentException("Your testimonial must contain 10 to 5000 characters.");
        if (!Boolean.TRUE.equals(request.getPublicationConsent()))
            throw new IllegalArgumentException("Publication consent is required.");
        List<String> photos = request.getPhotos() == null ? List.of() : request.getPhotos();
        if (photos.size() > 3) throw new IllegalArgumentException("You can upload up to 3 photos.");
        for (String photo : photos) {
            if (photo == null || !photo.matches("^data:image/(jpeg|jpg|png|webp);base64,[A-Za-z0-9+/=]+$"))
                throw new IllegalArgumentException("Photos must be JPG, PNG or WEBP images.");
            if (photo.length() > 4_000_000) throw new IllegalArgumentException("Each photo must be smaller than 3 MB.");
        }
        ClientUser client = clients.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Client account not found."));
        CustomerTestimonial saved = testimonials.save(new CustomerTestimonial(
            client, request.getDisplayName().trim(), request.getContent().trim(), true, photos));
        return TestimonialResponse.from(saved, false);
    }

    @Transactional(readOnly = true)
    public List<TestimonialResponse> mine(String email) {
        return testimonials.findAllByClientEmailOrderByCreatedAtDesc(email)
            .stream().map(t -> TestimonialResponse.from(t, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<TestimonialResponse> adminList() {
        return testimonials.findAllByOrderByCreatedAtDesc()
            .stream().map(t -> TestimonialResponse.from(t, true)).toList();
    }

    @Transactional
    public TestimonialResponse review(Long id, String decision) {
        CustomerTestimonial t = testimonials.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Testimonial not found."));
        if (!List.of("APPROVED", "REJECTED").contains(decision))
            throw new IllegalArgumentException("Invalid review decision.");
        t.setStatus(decision);
        if (decision.equals("REJECTED")) {
            t.setDisplayOnPublic(false);
            t.setFeaturedOnHome(false);
        }
        return TestimonialResponse.from(t, true);
    }

    @Transactional
    public TestimonialResponse settings(Long id, TestimonialDecisionRequest request) {
        CustomerTestimonial t = testimonials.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Testimonial not found."));
        if (!"APPROVED".equals(t.getStatus()) || !t.isPublicationConsent())
            throw new IllegalArgumentException("Only approved testimonials with consent can be displayed.");
        boolean show = Boolean.TRUE.equals(request.getDisplayOnPublic());
        boolean featured = Boolean.TRUE.equals(request.getFeaturedOnHome());
        if (featured && !show) throw new IllegalArgumentException("A homepage testimonial must also be public.");
        if (featured && testimonials.countByFeaturedOnHomeTrueAndStatusAndPublicationConsentTrue("APPROVED") >= 2
                && !t.isFeaturedOnHome())
            throw new IllegalArgumentException("Only two testimonials can be featured on the homepage.");
        t.setDisplayOnPublic(show);
        t.setFeaturedOnHome(featured);
        return TestimonialResponse.from(t, true);
    }

    @Transactional
    public TestimonialResponse remove(Long id) {
        CustomerTestimonial t = testimonials.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Testimonial not found."));
        t.setDisplayOnPublic(false);
        t.setFeaturedOnHome(false);
        t.setStatus("REMOVED");
        return TestimonialResponse.from(t, true);
    }

    @Transactional(readOnly = true)
    public List<TestimonialResponse> publicList() {
        if (!enabled()) return List.of();
        return testimonials.findAllByStatusAndPublicationConsentTrueAndDisplayOnPublicTrueOrderByReviewedAtDesc("APPROVED")
            .stream().map(t -> TestimonialResponse.from(t, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<TestimonialResponse> featured() {
        if (!enabled()) return List.of();
        return testimonials.findTop2ByStatusAndPublicationConsentTrueAndFeaturedOnHomeTrueOrderByReviewedAtDesc("APPROVED")
            .stream().map(t -> TestimonialResponse.from(t, false)).toList();
    }

    private boolean enabled() {
        return features.findByFeatureKey("TESTIMONIALS")
            .map(FeatureVisibility::isEnabled).orElse(false);
    }
}
