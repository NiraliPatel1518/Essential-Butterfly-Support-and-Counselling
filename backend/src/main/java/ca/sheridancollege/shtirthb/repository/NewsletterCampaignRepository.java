
package ca.sheridancollege.shtirthb.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.NewsletterCampaign;

public interface NewsletterCampaignRepository
        extends JpaRepository<NewsletterCampaign, Long> {
}
