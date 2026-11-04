
package ca.sheridancollege.shtirthb.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.NewsletterDelivery;

public interface NewsletterDeliveryRepository
        extends JpaRepository<NewsletterDelivery, Long> {

    List<NewsletterDelivery> findAllByCampaignId(Long campaignId);

    List<NewsletterDelivery> findAllByCampaignIdAndStatus(
        Long campaignId, String status
    );
}
