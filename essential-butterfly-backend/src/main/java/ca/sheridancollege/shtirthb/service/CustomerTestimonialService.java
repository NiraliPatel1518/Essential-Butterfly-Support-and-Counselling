
package ca.sheridancollege.shtirthb.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.dto.TestimonialDecisionRequest;
import ca.sheridancollege.shtirthb.dto.TestimonialRequest;
import ca.sheridancollege.shtirthb.dto.TestimonialResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.CustomerTestimonial;
import ca.sheridancollege.shtirthb.model.FeatureVisibility;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.CustomerTestimonialRepository;
import ca.sheridancollege.shtirthb.repository.FeatureVisibilityRepository;

@Service
public class CustomerTestimonialService {

    private final CustomerTestimonialRepository testimonials;
    private final ClientUserRepository clients;
    private final FeatureVisibilityRepository features;

    public CustomerTestimonialService(
            CustomerTestimonialRepository testimonials,
            ClientUserRepository clients,
            FeatureVisibilityRepository features) {

        this.testimonials = testimonials;
        this.clients = clients;
        this.features = features;
    }

    @Transactional
    public TestimonialResponse submit(
            String email,
            TestimonialRequest request) {

        if (request == null
                || request.getDisplayName() == null
                || request.getDisplayName().trim().isEmpty()
                || request.getDisplayName().trim().length() > 80) {

            throw new AppExceptions.ValidationException(
                    "Enter a public display name (maximum 80 characters)."
            );
        }

        if (request.getContent() == null
                || request.getContent().trim().length() < 10
                || request.getContent().trim().length() > 5000) {

            throw new AppExceptions.ValidationException(
                    "Your testimonial must contain 10 to 5000 characters."
            );
        }

        if (!Boolean.TRUE.equals(request.getPublicationConsent())) {
            throw new AppExceptions.ValidationException(
                    "Publication consent is required."
            );
        }

        List<String> photos = request.getPhotos() == null
                ? List.of()
                : request.getPhotos();

        if (photos.size() > 3) {
            throw new AppExceptions.ValidationException(
                    "You can upload up to 3 photos."
            );
        }

        for (String photo : photos) {

            if (photo == null || !photo.matches(
                    "^data:image/(jpeg|jpg|png|webp);base64,[A-Za-z0-9+/=]+$")) {

                throw new AppExceptions.ValidationException(
                        "Photos must be JPG, PNG or WEBP images."
                );
            }

            if (photo.length() > 4_000_000) {
                throw new AppExceptions.ValidationException(
                        "Each photo must be smaller than 3 MB."
                );
            }
        }

        ClientUser client = clients.findByEmail(email)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Client account"
                        )
                );

        CustomerTestimonial saved = testimonials.save(
                new CustomerTestimonial(
                        client,
                        request.getDisplayName().trim(),
                        request.getContent().trim(),
                        true,
                        photos
                )
        );

        return TestimonialResponse.from(saved, false);
    }

    @Transactional(readOnly = true)
    public List<TestimonialResponse> mine(String email) {

        return testimonials
                .findAllByClientEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(t -> TestimonialResponse.from(t, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TestimonialResponse> adminList() {

        return testimonials.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(t -> TestimonialResponse.from(t, true))
                .toList();
    }

    @Transactional
    public TestimonialResponse review(Long id, String decision) {

        CustomerTestimonial testimonial = testimonials.findById(id)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Testimonial"
                        )
                );

        if (!List.of("APPROVED", "REJECTED").contains(decision)) {
            throw new AppExceptions.ValidationException(
                    "Invalid review decision."
            );
        }

        testimonial.setStatus(decision);

        if (decision.equals("REJECTED")) {
            testimonial.setDisplayOnPublic(false);
            testimonial.setFeaturedOnHome(false);
        }

        return TestimonialResponse.from(testimonial, true);
    }

    @Transactional
    public TestimonialResponse settings(
            Long id,
            TestimonialDecisionRequest request) {

        CustomerTestimonial testimonial = testimonials.findById(id)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Testimonial"
                        )
                );

        if (!"APPROVED".equals(testimonial.getStatus())
                || !testimonial.isPublicationConsent()) {

            throw new AppExceptions.BusinessRuleException(
                    "Only approved testimonials with consent can be displayed."
            );
        }

        boolean show = Boolean.TRUE.equals(
                request.getDisplayOnPublic());

        boolean featured = Boolean.TRUE.equals(
                request.getFeaturedOnHome());

        if (featured && !show) {
            throw new AppExceptions.BusinessRuleException(
                    "A homepage testimonial must also be public."
            );
        }

        if (featured
                && testimonials.countByFeaturedOnHomeTrueAndStatusAndPublicationConsentTrue(
                        "APPROVED") >= 2
                && !testimonial.isFeaturedOnHome()) {

            throw new AppExceptions.ConflictException(
                    "Only two testimonials can be featured on the homepage."
            );
        }

        testimonial.setDisplayOnPublic(show);
        testimonial.setFeaturedOnHome(featured);

        return TestimonialResponse.from(testimonial, true);
    }

    @Transactional
    public TestimonialResponse remove(Long id) {

        CustomerTestimonial testimonial = testimonials.findById(id)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Testimonial"
                        )
                );

        testimonial.setDisplayOnPublic(false);
        testimonial.setFeaturedOnHome(false);
        testimonial.setStatus("REMOVED");

        return TestimonialResponse.from(testimonial, true);
    }

    @Transactional(readOnly = true)
    public List<TestimonialResponse> publicList() {

        if (!enabled()) {
            return List.of();
        }

        return testimonials
                .findAllByStatusAndPublicationConsentTrueAndDisplayOnPublicTrueOrderByReviewedAtDesc(
                        "APPROVED")
                .stream()
                .map(t -> TestimonialResponse.from(t, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TestimonialResponse> featured() {

        if (!enabled()) {
            return List.of();
        }

        return testimonials
                .findTop2ByStatusAndPublicationConsentTrueAndFeaturedOnHomeTrueOrderByReviewedAtDesc(
                        "APPROVED")
                .stream()
                .map(t -> TestimonialResponse.from(t, false))
                .toList();
    }

    private boolean enabled() {

        return features.findByFeatureKey("TESTIMONIALS")
                .map(FeatureVisibility::isEnabled)
                .orElse(false);
    }
}
