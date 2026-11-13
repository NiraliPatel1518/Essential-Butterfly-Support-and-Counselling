
package ca.sheridancollege.shtirthb.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ca.sheridancollege.shtirthb.model.NewsletterDelivery;

public interface NewsletterDeliveryRepository
        extends JpaRepository<NewsletterDelivery, Long> {

    List<NewsletterDelivery> findAllByCampaignId(Long campaignId);

    /*
     * Campaign statistics are always calculated from the delivery rows.
     * Each row is [status, count].
     */
    @Query("""
            select d.status, count(d)
              from NewsletterDelivery d
             where d.campaign.id = :campaignId
             group by d.status
            """)
    List<Object[]> countStatusesForCampaign(
            @Param("campaignId") Long campaignId);

    // Each row is [campaignId, status, count].
    @Query("""
            select d.campaign.id, d.status, count(d)
              from NewsletterDelivery d
             group by d.campaign.id, d.status
            """)
    List<Object[]> countStatusesForAllCampaigns();

    List<NewsletterDelivery> findAllByCampaignIdAndStatus(
        Long campaignId, String status
    );
}
