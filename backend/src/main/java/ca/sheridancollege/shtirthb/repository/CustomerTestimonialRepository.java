package ca.sheridancollege.shtirthb.repository;

import ca.sheridancollege.shtirthb.model.CustomerTestimonial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CustomerTestimonialRepository extends JpaRepository<CustomerTestimonial, Long> {
    List<CustomerTestimonial> findAllByClientEmailOrderByCreatedAtDesc(String email);
    List<CustomerTestimonial> findAllByOrderByCreatedAtDesc();
    List<CustomerTestimonial> findAllByStatusOrderByCreatedAtAsc(String status);
    List<CustomerTestimonial> findAllByStatusAndPublicationConsentTrueAndDisplayOnPublicTrueOrderByReviewedAtDesc(String status);
    List<CustomerTestimonial> findTop2ByStatusAndPublicationConsentTrueAndFeaturedOnHomeTrueOrderByReviewedAtDesc(String status);
    long countByFeaturedOnHomeTrueAndStatusAndPublicationConsentTrue(String status);
}
