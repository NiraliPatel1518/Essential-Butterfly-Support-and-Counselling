package ca.sheridancollege.shtirthb.repository;

import ca.sheridancollege.shtirthb.model.CustomerTestimonial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CustomerTestimonialRepository extends JpaRepository<CustomerTestimonial, Long> {
    // A client's own testimonials, found through their sign-in address.
    @Query("""
            select t
              from CustomerTestimonial t
              join t.client c
              join c.emails e
             where e.primaryEmail = true
               and e.email = lower(:email)
             order by t.createdAt desc
            """)
    List<CustomerTestimonial> findAllByClientEmailOrderByCreatedAtDesc(
            @Param("email") String email);
    List<CustomerTestimonial> findAllByOrderByCreatedAtDesc();
    List<CustomerTestimonial> findAllByStatusOrderByCreatedAtAsc(String status);
    List<CustomerTestimonial> findAllByStatusAndPublicationConsentTrueAndDisplayOnPublicTrueOrderByReviewedAtDesc(String status);
    List<CustomerTestimonial> findTop2ByStatusAndPublicationConsentTrueAndFeaturedOnHomeTrueOrderByReviewedAtDesc(String status);
    long countByFeaturedOnHomeTrueAndStatusAndPublicationConsentTrue(String status);
}
