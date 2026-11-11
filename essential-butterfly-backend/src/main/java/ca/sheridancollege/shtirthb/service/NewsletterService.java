
package ca.sheridancollege.shtirthb.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.FeatureVisibility;
import ca.sheridancollege.shtirthb.model.NewsletterCampaign;
import ca.sheridancollege.shtirthb.model.NewsletterDelivery;
import ca.sheridancollege.shtirthb.model.NewsletterSubscriber;
import ca.sheridancollege.shtirthb.repository.FeatureVisibilityRepository;
import ca.sheridancollege.shtirthb.repository.NewsletterCampaignRepository;
import ca.sheridancollege.shtirthb.repository.NewsletterDeliveryRepository;
import ca.sheridancollege.shtirthb.repository.NewsletterSubscriberRepository;

@Service
public class NewsletterService {

    private final NewsletterSubscriberRepository subscribers;
    private final NewsletterCampaignRepository campaigns;
    private final NewsletterDeliveryRepository deliveries;
    private final FeatureVisibilityRepository features;
    private final NewsletterMailService mailService;

    public NewsletterService(
            NewsletterSubscriberRepository subscribers,
            NewsletterCampaignRepository campaigns,
            NewsletterDeliveryRepository deliveries,
            FeatureVisibilityRepository features,
            NewsletterMailService mailService) {

        this.subscribers = subscribers;
        this.campaigns = campaigns;
        this.deliveries = deliveries;
        this.features = features;
        this.mailService = mailService;
    }

    // PUBLIC: subscribe to newsletter
    @Transactional
    public Map<String, Object> subscribe(
            String email,
            Boolean consent) {

        if (!newsletterEnabled()) {
            throw new AppExceptions.ResourceNotFoundException(
                    "Newsletter subscriptions"
            );
        }

        if (email == null || !email.trim().matches(
                "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$")
                || email.length() > 254) {

            throw new AppExceptions.ValidationException(
                    "Enter a valid email address."
            );
        }

        if (!Boolean.TRUE.equals(consent)) {
            throw new AppExceptions.ValidationException(
                    "Consent is required to subscribe."
            );
        }

        String normalized = email.trim().toLowerCase();

        NewsletterSubscriber existing = subscribers
                .findByEmailIgnoreCase(normalized)
                .orElse(null);

        if (existing != null) {

            if (!existing.isActive()) {
                existing.resubscribe();
                subscribers.save(existing);
            }

            return Map.of(
                    "success", true,
                    "message",
                    "If this address is eligible, it is subscribed to the newsletter."
            );
        }

        subscribers.save(new NewsletterSubscriber(normalized));

        return Map.of(
                "success", true,
                "message",
                "You have successfully subscribed to the newsletter."
        );
    }

    // PUBLIC: unsubscribe using token
    @Transactional
    public Map<String, Object> unsubscribe(String token) {

        if (token == null || token.isBlank()) {
            throw new AppExceptions.ValidationException(
                    "Unsubscribe token is required."
            );
        }

        NewsletterSubscriber subscriber = subscribers
                .findByUnsubscribeToken(token)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Unsubscribe link"
                        )
                );

        subscriber.unsubscribe();

        return Map.of(
                "success", true,
                "message",
                "You have been unsubscribed and will not receive future newsletters."
        );
    }

    // ADMIN: overview and active subscribers
    @Transactional(readOnly = true)
    public Map<String, Object> getAdminOverview() {

        List<NewsletterSubscriber> active =
                subscribers.findAllByActiveTrueOrderByCreatedAtDesc();

        return Map.of(
                "activeSubscribers", active.size(),

                "subscribers", active.stream()
                        .map(s -> Map.of(
                                "id", s.getId(),
                                "email", s.getEmail(),
                                "createdAt", s.getCreatedAt()
                        ))
                        .toList(),

                "campaigns", campaigns.findAll()
                        .stream()
                        .map(this::campaignMap)
                        .toList()
        );
    }

    // ADMIN: create a newsletter draft
    @Transactional
    public Map<String, Object> createCampaign(
            String subject,
            String content) {

        validateCampaign(subject, content);

        NewsletterCampaign campaign = campaigns.save(
                new NewsletterCampaign(
                        subject.trim(),
                        content.trim()
                )
        );

        return campaignMap(campaign);
    }

    // ADMIN: update an existing draft
    @Transactional
    public Map<String, Object> updateCampaign(
            Long id,
            String subject,
            String content) {

        validateCampaign(subject, content);

        NewsletterCampaign campaign = getCampaign(id);

        try {
            campaign.update(subject.trim(), content.trim());

        } catch (IllegalStateException ex) {

            throw new AppExceptions.ConflictException(
                    ex.getMessage()
            );
        }

        return campaignMap(campaigns.save(campaign));
    }

    // ADMIN: preview a newsletter before sending
    @Transactional(readOnly = true)
    public Map<String, Object> preview(Long id) {

        NewsletterCampaign campaign = getCampaign(id);

        return Map.of(
                "campaign", campaignMap(campaign),
                "activeRecipientCount", subscribers.countByActiveTrue()
        );
    }

    // ADMIN: send newsletter to active subscribers
    @Transactional
    public Map<String, Object> send(Long id) {

        NewsletterCampaign campaign = getCampaign(id);

        if (!"DRAFT".equals(campaign.getStatus())) {
            throw new AppExceptions.ConflictException(
                    "This newsletter has already been processed."
            );
        }

        List<NewsletterSubscriber> active =
                subscribers.findAllByActiveTrueOrderByCreatedAtDesc();

        if (active.isEmpty()) {
            throw new AppExceptions.ConflictException(
                    "No active subscribers."
            );
        }

        int success = 0;
        int failed = 0;

        for (NewsletterSubscriber subscriber : active) {

            NewsletterDelivery delivery =
                    deliveries.save(
                            new NewsletterDelivery(campaign, subscriber)
                    );

            try {
                mailService.send(campaign, subscriber);
                delivery.markSent();
                success++;

            } catch (Exception ex) {
                delivery.markFailed(ex.getMessage());
                failed++;
            }

            deliveries.save(delivery);
        }

        campaign.recordResults(active.size(), success, failed);
        campaigns.save(campaign);

        return campaignMap(campaign);
    }

    // ADMIN: retry only failed deliveries
    @Transactional
    public Map<String, Object> retryFailed(Long id) {

        NewsletterCampaign campaign = getCampaign(id);

        List<NewsletterDelivery> failedDeliveries =
                deliveries.findAllByCampaignIdAndStatus(id, "FAILED");

        if (failedDeliveries.isEmpty()) {
            throw new AppExceptions.ConflictException(
                    "There are no failed recipients to retry."
            );
        }

        int success = 0;
        int failed = 0;

        for (NewsletterDelivery delivery : failedDeliveries) {

            if (!delivery.getSubscriber().isActive()) {
                delivery.markFailed("Subscriber has unsubscribed.");
                failed++;
                continue;
            }

            try {
                mailService.send(
                        campaign,
                        delivery.getSubscriber()
                );

                delivery.markSent();
                success++;

            } catch (Exception ex) {
                delivery.markFailed(ex.getMessage());
                failed++;
            }

            deliveries.save(delivery);
        }

        campaign.recordRetryResults(success, failed);
        campaigns.save(campaign);

        return campaignMap(campaign);
    }

    // ADMIN: get campaign and individual delivery results
    @Transactional(readOnly = true)
    public Map<String, Object> getCampaignDetails(Long id) {

        NewsletterCampaign campaign = getCampaign(id);

        List<Map<String, Object>> recipientResults =
                deliveries.findAllByCampaignId(id)
                        .stream()
                        .map(d -> {

                            Map<String, Object> item =
                                    new LinkedHashMap<>();

                            item.put(
                                    "email",
                                    d.getSubscriber().getEmail()
                            );

                            item.put("status", d.getStatus());
                            item.put("errorMessage", d.getErrorMessage());
                            item.put("sentAt", d.getSentAt());

                            return item;
                        })
                        .toList();

        return Map.of(
                "campaign", campaignMap(campaign),
                "deliveries", recipientResults
        );
    }

    private NewsletterCampaign getCampaign(Long id) {

        return campaigns.findById(id)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Newsletter"
                        )
                );
    }

    private boolean newsletterEnabled() {

        return features.findByFeatureKey("NEWSLETTER")
                .map(FeatureVisibility::isEnabled)
                .orElse(false);
    }

    private void validateCampaign(
            String subject,
            String content) {

        if (subject == null
                || subject.isBlank()
                || subject.length() > 200) {

            throw new AppExceptions.ValidationException(
                    "Subject is required and must be 200 characters or fewer."
            );
        }

        if (content == null || content.isBlank()) {
            throw new AppExceptions.ValidationException(
                    "Newsletter content is required."
            );
        }
    }

    private Map<String, Object> campaignMap(
            NewsletterCampaign campaign) {

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("id", campaign.getId());
        result.put("subject", campaign.getSubject());
        result.put("content", campaign.getContent());
        result.put("status", campaign.getStatus());
        result.put("recipientCount", campaign.getRecipientCount());
        result.put("successCount", campaign.getSuccessCount());
        result.put("failedCount", campaign.getFailedCount());
        result.put("createdAt", campaign.getCreatedAt());
        result.put("sentAt", campaign.getSentAt());

        return result;
    }
}
