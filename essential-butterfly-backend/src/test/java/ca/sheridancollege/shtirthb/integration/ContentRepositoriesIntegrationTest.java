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

    /*
     * Checks: A client's testimonial query returns only their own, newest first.
     * How:    Ann has two testimonials and Bob has one. findAllByClientEmailOrderByCreatedAtDesc for
     *   Ann returns Ann second, then Ann first.
     */
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

    /*
     * Checks: The pending testimonial query lists only PENDING ones, oldest first.
     * How:    Two pending and one approved are saved. The query for PENDING returns the two pending,
     *   oldest first.
     */
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

    /*
     * Checks: The public query returns only approved testimonials with consent and display turned on.
     * How:    We save five testimonials, four of which fail one rule (not displayed, no consent, or
     *   pending). The query returns only the two valid ones, newest first.
     */
    @Test
    void publicListShowsOnlyApprovedConsentedAndDisplayedTestimonials() {
        ClientUser owner = client("public@mail.com");

        testimonial(owner, "Shown first", true, "APPROVED", true, false);
        pause();
        testimonial(owner, "Shown second", true, "APPROVED", true, false);

        testimonial(owner, "Not displayed", true, "APPROVED", false, false);
        testimonial(owner, "No consent", false, "APPROVED", false, false);

        // Pending testimonials must never be displayed publicly.
        testimonial(owner, "Still pending", true, null, false, false);

        List<String> result = names(testimonials
            .findAllByStatusAndPublicationConsentTrueAndDisplayOnPublicTrueOrderByReviewedAtDesc("APPROVED"));

        assertEquals(List.of("Shown second", "Shown first"), result);
}

    /*
     * Checks: The home page query gives at most two featured testimonials, newest approved first.
     * How:    We save featured A and B, one not featured and one without consent. The top-2 query
     *   returns B then A, and the featured count is 2.
     */
    @Test
    void homepageGetsAtMostTwoFeaturedTestimonialsNewestApprovedFirst() {
        ClientUser owner = client("home@mail.com");

        testimonial(owner, "Featured A", true, "APPROVED", true, true);
        pause();

        testimonial(owner, "Featured B", true, "APPROVED", true, true);
        pause();

        // Approved, but not featured.
        testimonial(owner, "Featured C", true, "APPROVED", true, false);

        // No consent: cannot be publicly displayed or featured.
        testimonial(owner, "Featured no consent", false, "APPROVED", false, false);

        List<String> top = names(testimonials
            .findTop2ByStatusAndPublicationConsentTrueAndFeaturedOnHomeTrueOrderByReviewedAtDesc("APPROVED"));

        assertEquals(List.of("Featured B", "Featured A"), top);

        assertEquals(2, testimonials
            .countByFeaturedOnHomeTrueAndStatusAndPublicationConsentTrue("APPROVED"));
}

    /*
     * Checks: The photos of a testimonial are saved and loaded back.
     * How:    We save two photo names, flush and clear the entity manager (so data is read from the
     *   database), and load it again.
     */
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

    /*
     * Checks: Features are listed in display order.
     * How:    We save three TEST_ features in a mixed order. The query returns them as A, B, C by
     *   their display order number.
     */
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

    /*
     * Checks: A feature can be found by its key.
     * How:    We save TEST_FIND and read its label and enabled flag. A missing key gives an empty
     *   result.
     */
    @Test
    void findsAFeatureByItsKey() {
        features.save(new FeatureVisibility("TEST_FIND", "Find me", false, 110));

        FeatureVisibility found = features.findByFeatureKey("TEST_FIND").orElseThrow();

        assertEquals("Find me", found.getLabel());
        assertFalse(found.isEnabled());
        assertTrue(features.findByFeatureKey("TEST_MISSING").isEmpty());
    }

    /*
     * Checks: The database refuses two features with the same key.
     * How:    The second save with TEST_DUP throws DataIntegrityViolationException.
     */
    @Test
    void databaseRejectsTwoFeaturesWithTheSameKey() {
        features.save(new FeatureVisibility("TEST_DUP", "First", true, 120));

        assertThrows(DataIntegrityViolationException.class, () ->
                features.saveAndFlush(new FeatureVisibility("TEST_DUP", "Second", true, 121)));
    }

    // ---------- website updates ----------

    /*
     * Checks: The public updates query shows only published updates, newest first.
     * How:    We save old (published), draft, and new (published). The query returns new, then old.
     */
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

    /*
     * Checks: The admin updates query shows both published and unpublished updates.
     * How:    We save one visible and one hidden update. The admin query returns 2.
     */
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

    /*
     * Checks: An update can be found by id only when it is published.
     * How:    findByIdAndPublishedTrue finds the published one and returns empty for the draft.
     */
    @Test
    void findsAnUpdateByIdOnlyWhenItIsPublished() {
        WebsiteUpdate published = updates.save(new WebsiteUpdate("Test update live", "Content", true));
        WebsiteUpdate draft = updates.save(new WebsiteUpdate("Test update unpublished", "Content", false));

        assertTrue(updates.findByIdAndPublishedTrue(published.getId()).isPresent());
        assertTrue(updates.findByIdAndPublishedTrue(draft.getId()).isEmpty());
    }

    /*
     * Checks: The photos and social links of an update are saved and loaded back.
     * How:    We save two photos and two links, clear the entity manager, load it again, and compare
     *   the lists.
     */
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