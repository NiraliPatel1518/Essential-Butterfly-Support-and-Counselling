package ca.sheridancollege.shtirthb.dto;

import ca.sheridancollege.shtirthb.model.CustomerTestimonial;
import java.time.LocalDateTime;
import java.util.List;

public record TestimonialResponse(Long id, String displayName, String content,
        List<String> photos, String status, boolean publicationConsent,
        boolean displayOnPublic, boolean featuredOnHome, LocalDateTime createdAt,
        String ownerEmail) {
    public static TestimonialResponse from(CustomerTestimonial t, boolean includePrivate) {
        return new TestimonialResponse(t.getId(), t.getDisplayName(), t.getContent(),
            t.getPhotos(), includePrivate ? t.getStatus() : "SUBMITTED", includePrivate && t.isPublicationConsent(),
            includePrivate && t.isDisplayOnPublic(), includePrivate && t.isFeaturedOnHome(),
            t.getCreatedAt(), includePrivate ? ownerEmail(t) : null);
    }

    /*
     * The submitter's current sign-in address while the account exists;
     * otherwise the address recorded when the testimonial was submitted
     * (the account may have been deleted since).
     */
    private static String ownerEmail(CustomerTestimonial t) {
        return t.getClient() != null
                ? t.getClient().getEmail()
                : t.getSubmittedByEmail();
    }
}
