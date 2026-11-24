package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ca.sheridancollege.shtirthb.dto.ContentPageAdminResponse;
import ca.sheridancollege.shtirthb.dto.ContentPagePublicResponse;
import ca.sheridancollege.shtirthb.dto.ContentPageRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.ContentPage;
import ca.sheridancollege.shtirthb.repository.ContentPageRepository;

/*
 * Unit tests for ContentPageService (editable website pages).
 *
 * The page repository and the audit trail are fakes (Mockito).
 * The body validator is the real one, so the saved JSON is really checked.
 *
 * Rules tested:
 *   - Visitors only see the PUBLISHED copy.
 *   - "Save draft" never changes the published copy.
 *   - "Publish" changes both the draft and the published copy.
 *   - Missing, too long or unsafe content is refused and nothing is saved.
 */
@ExtendWith(MockitoExtension.class)
class ContentPageServiceTest {

    private static final String ADMIN = "admin@mail.com";
    private static final String BODY_V1 = "{\"fields\":{\"intro\":\"First version\"}}";
    private static final String BODY_V2 = "{\"fields\":{\"intro\":\"Second version\"}}";

    @Mock
    private ContentPageRepository pages;

    @Mock
    private AuditTrailService auditTrail;

    private ContentPageService service;
    private ContentPage about;

    @BeforeEach
    void setUp() {
        service = new ContentPageService(pages, auditTrail, new ContentBodyValidator());
        about = new ContentPage("about");
    }

    // ------------------------------------------------------------- helpers

    private void aboutExists() {
        when(pages.findByPageKey("about")).thenReturn(Optional.of(about));
    }

    private void saveReturnsTheSamePage() {
        when(pages.save(any(ContentPage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ContentPageRequest request(String title, String body, Boolean publish) {
        ContentPageRequest request = new ContentPageRequest();
        request.setTitle(title);
        request.setBody(body);
        request.setPublish(publish);
        return request;
    }

    private String errorOf(ContentPageRequest request) {
        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.save("about", request, ADMIN));
        return error.getMessage();
    }

    private void assertNothingWasSaved() {
        verify(pages, never()).save(any());
        verify(auditTrail, never()).actAs(any());
    }

    // ------------------------------------------------------- public reading

    /*
     * Checks: The public page returns the published title and body.
     * How:    Mockito fake repository. The about page is published. The result has the key, title,
     *   body and a published date.
     */
    @Test
    void getPublished_returnsThePublishedPage() {
        about.publish("About Casey", BODY_V1);
        aboutExists();

        Optional<ContentPagePublicResponse> result = service.getPublished("about");

        assertTrue(result.isPresent());
        assertEquals("about", result.get().pageKey());
        assertEquals("About Casey", result.get().title());
        assertEquals(BODY_V1, result.get().body());
        assertNotNull(result.get().publishedAt());
    }

    /*
     * Checks: A page that was never published returns nothing.
     * How:    The page only has a draft. The result is empty.
     */
    @Test
    void getPublished_returnsNothing_whenThePageWasNeverPublished() {
        about.saveDraft("Draft title", BODY_V1);
        aboutExists();

        assertTrue(service.getPublished("about").isEmpty());
    }

    /*
     * Checks: A saved draft never shows to visitors.
     * How:    The page is published as v1, then a draft v2 is saved. The public result still has the
     *   live title and body (v1).
     */
    @Test
    void getPublished_neverShowsTheDraft_afterAChangeWasSavedAsDraft() {
        about.publish("Live title", BODY_V1);
        about.saveDraft("Draft title", BODY_V2);
        aboutExists();

        ContentPagePublicResponse result = service.getPublished("about").orElseThrow();

        assertEquals("Live title", result.title());
        assertEquals(BODY_V1, result.body());
    }

    /*
     * Checks: An unknown page returns nothing.
     * How:    getPublished("missing") is empty.
     */
    @Test
    void getPublished_returnsNothing_whenThePageDoesNotExist() {
        when(pages.findByPageKey("missing")).thenReturn(Optional.empty());

        assertTrue(service.getPublished("missing").isEmpty());
    }

    /*
     * Checks: Capital letters and spaces in the page key are ignored.
     * How:    " ABOUT " finds the page.
     */
    @Test
    void getPublished_ignoresCapitalLettersAndSpacesInThePageKey() {
        about.publish("About Casey", BODY_V1);
        aboutExists();

        assertTrue(service.getPublished("  ABOUT ").isPresent());
    }

    /*
     * Checks: A null page key returns nothing.
     * How:    getPublished(null) is empty.
     */
    @Test
    void getPublished_returnsNothing_whenThePageKeyIsNull() {
        when(pages.findByPageKey("")).thenReturn(Optional.empty());

        assertTrue(service.getPublished(null).isEmpty());
    }

    // ------------------------------------------------------- admin reading

    /*
     * Checks: The admin list gives each page a readable label.
     * How:    There are 4 pages. The labels are Service page, About and Credentials page, Privacy
     *   Policy page and faq.
     */
    @Test
    void getAdminPages_givesEachPageAReadableLabel() {
        ContentPage services = new ContentPage("services");
        ContentPage privacy = new ContentPage("privacy");
        ContentPage other = new ContentPage("faq");
        when(pages.findAllByOrderByIdAsc())
                .thenReturn(List.of(services, about, privacy, other));

        List<ContentPageAdminResponse> result = service.getAdminPages();

        assertEquals(4, result.size());
        assertEquals("Service page", result.get(0).label());
        assertEquals("About and Credentials page", result.get(1).label());
        assertEquals("Privacy Policy page", result.get(2).label());
        assertEquals("faq", result.get(3).label());
    }

    /*
     * Checks: The admin view shows the draft, the published copy and the unpublished-changes flag.
     * How:    A published v1 with a draft v2 shows published true, hasUnpublishedChanges true, and
     *   both titles and bodies.
     */
    @Test
    void getAdminPage_showsDraftPublishedAndUnpublishedChanges() {
        about.publish("Live title", BODY_V1);
        about.saveDraft("Draft title", BODY_V2);
        aboutExists();

        ContentPageAdminResponse result = service.getAdminPage("about");

        assertTrue(result.published());
        assertTrue(result.hasUnpublishedChanges());
        assertEquals("Live title", result.publishedTitle());
        assertEquals(BODY_V1, result.publishedBody());
        assertEquals("Draft title", result.draftTitle());
        assertEquals(BODY_V2, result.draftBody());
    }

    /*
     * Checks: No unpublished changes when the draft equals the published page.
     * How:    hasUnpublishedChanges is false.
     */
    @Test
    void getAdminPage_saysThereAreNoChanges_whenTheDraftMatchesThePublishedPage() {
        about.publish("Live title", BODY_V1);
        aboutExists();

        assertFalse(service.getAdminPage("about").hasUnpublishedChanges());
    }

    /*
     * Checks: An unknown page gives not found for the admin.
     * How:    It throws ResourceNotFoundException.
     */
    @Test
    void getAdminPage_throwsError_whenThePageDoesNotExist() {
        when(pages.findByPageKey("missing")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.getAdminPage("missing"));
    }

    // ---------------------------------------------------------------- save

    /*
     * Checks: Saving as a draft keeps the published page unchanged.
     * How:    The published title and body stay the same, the draft has the new text,
     *   hasUnpublishedChanges is true, and the audit trail records the admin.
     */
    @Test
    void save_asDraft_keepsThePublishedPageUnchanged() {
        about.publish("Live title", BODY_V1);
        aboutExists();
        saveReturnsTheSamePage();

        ContentPageAdminResponse result = service.save(
                "about", request("New title", BODY_V2, false), ADMIN);

        assertEquals("Live title", result.publishedTitle());
        assertEquals(BODY_V1, result.publishedBody());
        assertEquals("New title", result.draftTitle());
        assertEquals(BODY_V2, result.draftBody());
        assertTrue(result.hasUnpublishedChanges());
        verify(auditTrail).actAs(ADMIN);
    }

    /*
     * Checks: A save without the publish flag is a draft.
     * How:    published is false and the draft title is saved.
     */
    @Test
    void save_withoutThePublishFlag_isTreatedAsADraft() {
        aboutExists();
        saveReturnsTheSamePage();

        ContentPageAdminResponse result = service.save(
                "about", request("Title", BODY_V1, null), ADMIN);

        assertFalse(result.published());
        assertEquals("Title", result.draftTitle());
    }

    /*
     * Checks: Saving with publish makes the draft the public page.
     * How:    The published title and body become the new ones, hasUnpublishedChanges is false, a
     *   published date is set, the page is saved and the admin is recorded.
     */
    @Test
    void save_withPublish_makesTheDraftThePublicPage() {
        about.publish("Live title", BODY_V1);
        aboutExists();
        saveReturnsTheSamePage();

        ContentPageAdminResponse result = service.save(
                "about", request("New title", BODY_V2, true), ADMIN);

        assertTrue(result.published());
        assertEquals("New title", result.publishedTitle());
        assertEquals(BODY_V2, result.publishedBody());
        assertFalse(result.hasUnpublishedChanges());
        assertNotNull(result.publishedAt());
        verify(pages).save(about);
        verify(auditTrail).actAs(ADMIN);
    }

    /*
     * Checks: Spaces around the title and body are trimmed.
     * How:    The saved title and body have no extra spaces.
     */
    @Test
    void save_trimsSpacesAroundTheTitleAndBody() {
        aboutExists();
        saveReturnsTheSamePage();

        ContentPageAdminResponse result = service.save(
                "about", request("  Spaced title  ", "  " + BODY_V1 + "\n", true), ADMIN);

        assertEquals("Spaced title", result.publishedTitle());
        assertEquals(BODY_V1, result.publishedBody());
    }

    /*
     * Checks: The admin is recorded before the page is changed.
     * How:    Mockito InOrder: actAs(admin) must run before pages.save().
     */
    @Test
    void save_recordsTheAdminBeforeChangingThePage() {
        aboutExists();
        saveReturnsTheSamePage();

        service.save("about", request("Title", BODY_V1, true), ADMIN);

        var order = inOrder(auditTrail, pages);
        order.verify(auditTrail).actAs(ADMIN);
        order.verify(pages).save(about);
    }

    /*
     * Checks: Saving an unknown page is refused.
     * How:    It throws ResourceNotFoundException and nothing is saved or recorded.
     */
    @Test
    void save_throwsError_whenThePageDoesNotExist() {
        when(pages.findByPageKey("missing")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.save("missing", request("T", BODY_V1, true), ADMIN));

        assertNothingWasSaved();
    }

    /*
     * Checks: The error names every missing part.
     * How:    No title and no body gives "Required content is missing: page title, page content." Only
     *   one missing gives only that part. Nothing is saved.
     */
    @Test
    void save_namesEveryMissingPart() {
        aboutExists();

        assertEquals("Required content is missing: page title, page content.",
                errorOf(request("", "", true)));
        assertEquals("Required content is missing: page title.",
                errorOf(request("   ", BODY_V1, true)));
        assertEquals("Required content is missing: page content.",
                errorOf(request("Title", null, true)));
        assertNothingWasSaved();
    }

    /*
     * Checks: A missing request is refused.
     * How:    A null request throws ValidationException and nothing is saved.
     */
    @Test
    void save_throwsError_whenTheRequestIsMissing() {
        aboutExists();

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.save("about", null, ADMIN));

        assertNothingWasSaved();
    }

    /*
     * Checks: A title that is too long is refused.
     * How:    The message is "The page title must be 180 characters or fewer." and nothing is saved.
     */
    @Test
    void save_throwsError_whenTheTitleIsTooLong() {
        aboutExists();
        String title = "t".repeat(ContentPageService.MAX_TITLE_LENGTH + 1);

        assertEquals("The page title must be 180 characters or fewer.",
                errorOf(request(title, BODY_V1, true)));
        assertNothingWasSaved();
    }

    /*
     * Checks: A title of exactly the maximum length is accepted.
     * How:    A title of the maximum length does not throw.
     */
    @Test
    void save_acceptsATitleOfExactlyTheMaximumLength() {
        aboutExists();
        saveReturnsTheSamePage();
        String title = "t".repeat(ContentPageService.MAX_TITLE_LENGTH);

        assertDoesNotThrow(() ->
                service.save("about", request(title, BODY_V1, true), ADMIN));
    }

    /*
     * Checks: A body that is too long is refused.
     * How:    The message is "The page content must be 50000 characters or fewer." and nothing is
     *   saved.
     */
    @Test
    void save_throwsError_whenTheBodyIsTooLong() {
        aboutExists();
        String body = "b".repeat(ContentPageService.MAX_BODY_LENGTH + 1);

        assertEquals("The page content must be 50000 characters or fewer.",
                errorOf(request("Title", body, true)));
        assertNothingWasSaved();
    }

    /*
     * Checks: A title with a null character is refused.
     * How:    The message is "The content contains characters that are not allowed." and nothing is
     *   saved.
     */
    @Test
    void save_throwsError_whenTheTitleHasANullCharacter() {
        aboutExists();

        assertEquals("The content contains characters that are not allowed.",
                errorOf(request("Bad\u0000title", BODY_V1, true)));
        assertNothingWasSaved();
    }

    /*
     * Checks: A body in the wrong format is refused.
     * How:    A body with a script tag is refused and nothing is saved.
     */
    @Test
    void save_throwsError_whenTheBodyIsNotInTheExpectedFormat() {
        aboutExists();

        assertTrue(errorOf(request("Title", "<script>alert(1)</script>", true))
                .startsWith("The page content is not in the expected format"));
        assertNothingWasSaved();
    }

    /*
     * Checks: A refused save leaves the page unchanged.
     * How:    After a refused save, the published and draft titles and bodies are the same as before.
     */
    @Test
    void save_keepsThePageUnchanged_whenTheContentIsRefused() {
        about.publish("Live title", BODY_V1);
        aboutExists();

        errorOf(request("", BODY_V2, true));

        assertEquals("Live title", about.getPublishedTitle());
        assertEquals(BODY_V1, about.getPublishedBody());
        assertEquals("Live title", about.getDraftTitle());
    }
}