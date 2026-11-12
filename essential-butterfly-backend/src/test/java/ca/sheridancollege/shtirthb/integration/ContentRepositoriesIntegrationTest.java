package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.CustomerTestimonial;
import ca.sheridancollege.shtirthb.model.FeatureVisibility;
import ca.sheridancollege.shtirthb.model.WebsiteUpdate;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.CustomerTestimonialRepository;
import ca.sheridancollege.shtirthb.repository.FeatureVisibilityRepository;
import ca.sheridancollege.shtirthb.repository.WebsiteUpdateRepository;

class ContentRepositoriesIntegrationTest extends RepositoryIntegrationTestBase {

    @Autowired
    private ClientUserRepository clients;

    @Autowired
    private CustomerTestimonialRepository testimonials;

    @Autowired
    private FeatureVisibilityRepository features;

    @Autowired
    private WebsiteUpdateRepository updates;

    @PersistenceContext
    private EntityManager entityManager;

    // Timestamps decide the order, so a short pause keeps them different.
    private void pause() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ClientUser client(String email) {
        return clients.save(new ClientUser("Client", email, "hash"));
    }

    private CustomerTestimonial testimonial(
            ClientUser owner, String name, boolean consent,
            String status, boolean display, boolean featured) {
        CustomerTestimonial item =
                new CustomerTestimonial(owner, name, "A testimonial text.", consent, List.of());
        if (status != null) {
            item.setStatus(status);
        }
        item.setDisplayOnPublic(display);
        item.setFeaturedOnHome(featured);
        return testimonials.save(item);
    }

    private List<String> names(List<CustomerTestimonial> items) {
        return items.stream().map(CustomerTestimonial::getDisplayName).toList();
    }

    // ---------- testimonials ----------

    @Test
    void aClientSeesOnlyTheirOwnTestimonialsNewestFirst() {
        ClientUser ann = client("ann@mail.com");
        ClientUser bob = client("bob@mail.com");
        testimonial(ann, "Ann first", true, null, false, false);
        pause();
        testimonial(ann, "Ann second", true, null, false, false);
        testimonial(bob, "Bob only", true, null, false, false);

        List<CustomerTestimonial> result =
                testimonials.findAllByClientEmailOrderByCreatedAtDesc("ann@mail.com");

        assertEquals(List.of("Ann second", "Ann first"), names(result));
    }

    @Test
    void listsPendingTestimonialsOldestFirst() {
        ClientUser owner = client("pending@mail.com");
        testimonial(owner, "Pending one", true, null, false, false);
        pause();
        testimonial(owner, "Pending two", true, null, false, false);
        testimonial(owner, "Approved one", true, "APPROVED", false, false);

        List<String> pending = names(testimonials.findAllByStatusOrderByCreatedAtAsc("PENDING"));

        assertEquals(List.of("Pending one", "Pending two"), pending);
    }

    @Test
    void publicListShowsOnlyApprovedConsentedAndDisplayedTestimonials() {
        ClientUser owner = client("public@mail.com");
        testimonial(owner, "Shown first", true, "APPROVED", true, false);
        pause();
        testimonial(owner, "Shown second", true, "APPROVED", true, false);
        testimonial(owner, "Not displayed", true, "APPROVED", false, false);
        testimonial(owner, "No consent", false, "APPROVED", true, false);
        testimonial(owner, "Still pending", true, null, true, false);

        List<String> result = names(testimonials
                .findAllByStatusAndPublicationConsentTrueAndDisplayOnPublicTrueOrderByReviewedAtDesc("APPROVED"));

        assertEquals(List.of("Shown second", "Shown first"), result);
    }

    @Test
    void homepageGetsAtMostTwoFeaturedTestimonialsNewestApprovedFirst() {
        ClientUser owner = client("home@mail.com");
        testimonial(owner, "Featured A", true, "APPROVED", true, true);
        pause();
        testimonial(owner, "Featured B", true, "APPROVED", true, true);
        pause();
        testimonial(owner, "Featured C", true, "APPROVED", true, true);
        pause();
        testimonial(owner, "Featured no consent", false, "APPROVED", true, true);

        List<String> top = names(testimonials
                .findTop2ByStatusAndPublicationConsentTrueAndFeaturedOnHomeTrueOrderByReviewedAtDesc("APPROVED"));

        assertEquals(List.of("Featured C", "Featured B"), top);
        assertEquals(3, testimonials
                .countByFeaturedOnHomeTrueAndStatusAndPublicationConsentTrue("APPROVED"));
    }

    @Test
    void savesTestimonialPhotos() {
        ClientUser owner = client("photos@mail.com");
        CustomerTestimonial saved = testimonials.save(
                new CustomerTestimonial(owner, "With photos", "Some text here.", true, List.of("photo-one", "photo-two")));
        entityManager.flush();
        entityManager.clear();

        CustomerTestimonial loaded = testimonials.findById(saved.getId()).orElseThrow();

        assertEquals(List.of("photo-one", "photo-two"), loaded.getPhotos());
    }

    // ---------- feature visibility ----------

    @Test
    void listsFeaturesInDisplayOrder() {
        features.save(new FeatureVisibility("TEST_C", "C", true, 102));
        features.save(new FeatureVisibility("TEST_A", "A", true, 100));
        features.save(new FeatureVisibility("TEST_B", "B", false, 101));

        List<String> keys = features.findAllByOrderByDisplayOrderAsc().stream()
                .map(FeatureVisibility::getFeatureKey)
                .filter(key -> key.startsWith("TEST_"))
                .toList();

        assertEquals(List.of("TEST_A", "TEST_B", "TEST_C"), keys);
    }

    @Test
    void findsAFeatureByItsKey() {
        features.save(new FeatureVisibility("TEST_FIND", "Find me", false, 110));

        FeatureVisibility found = features.findByFeatureKey("TEST_FIND").orElseThrow();

        assertEquals("Find me", found.getLabel());
        assertFalse(found.isEnabled());
        assertTrue(features.findByFeatureKey("TEST_MISSING").isEmpty());
    }

    @Test
    void databaseRejectsTwoFeaturesWithTheSameKey() {
        features.save(new FeatureVisibility("TEST_DUP", "First", true, 120));

        assertThrows(DataIntegrityViolationException.class, () ->
                features.saveAndFlush(new FeatureVisibility("TEST_DUP", "Second", true, 121)));
    }

    // ---------- website updates ----------

    @Test
    void publicListShowsOnlyPublishedUpdatesNewestFirst() {
        updates.save(new WebsiteUpdate("Test update old", "Content", true));
        pause();
        updates.save(new WebsiteUpdate("Test update draft", "Content", false));
        pause();
        updates.save(new WebsiteUpdate("Test update new", "Content", true));

        List<String> titles = updates.findAllByPublishedTrueOrderByUpdatedAtDesc().stream()
                .map(WebsiteUpdate::getTitle)
                .filter(title -> title.startsWith("Test update"))
                .toList();

        assertEquals(List.of("Test update new", "Test update old"), titles);
    }

    @Test
    void adminListShowsPublishedAndUnpublishedUpdates() {
        updates.save(new WebsiteUpdate("Test update visible", "Content", true));
        updates.save(new WebsiteUpdate("Test update hidden", "Content", false));

        List<String> titles = updates.findAllByOrderByUpdatedAtDesc().stream()
                .map(WebsiteUpdate::getTitle)
                .filter(title -> title.startsWith("Test update"))
                .toList();

        assertEquals(2, titles.size());
    }

    @Test
    void findsAnUpdateByIdOnlyWhenItIsPublished() {
        WebsiteUpdate published = updates.save(new WebsiteUpdate("Test update live", "Content", true));
        WebsiteUpdate draft = updates.save(new WebsiteUpdate("Test update unpublished", "Content", false));

        assertTrue(updates.findByIdAndPublishedTrue(published.getId()).isPresent());
        assertTrue(updates.findByIdAndPublishedTrue(draft.getId()).isEmpty());
    }

    @Test
    void savesUpdatePhotosAndSocialLinks() {
        WebsiteUpdate update = new WebsiteUpdate("Test update media", "Content", true);
        update.setPhotos(List.of("photo-one", "photo-two"));
        update.setSocialLinks(List.of("https://instagram.com/casey", "https://facebook.com/casey"));
        WebsiteUpdate saved = updates.save(update);
        entityManager.flush();
        entityManager.clear();

        WebsiteUpdate loaded = updates.findById(saved.getId()).orElseThrow();

        assertEquals(List.of("photo-one", "photo-two"), loaded.getPhotos());
        assertEquals(List.of("https://instagram.com/casey", "https://facebook.com/casey"),
                loaded.getSocialLinks());
    }
}