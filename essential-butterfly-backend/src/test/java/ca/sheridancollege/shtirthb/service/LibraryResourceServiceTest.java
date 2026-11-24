package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import ca.sheridancollege.shtirthb.dto.LibraryResourceAdminResponse;
import ca.sheridancollege.shtirthb.dto.LibraryResourcePublicResponse;
import ca.sheridancollege.shtirthb.dto.LibraryResourceRequest;
import ca.sheridancollege.shtirthb.dto.ResourceDownload;
import ca.sheridancollege.shtirthb.dto.UploadRulesResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.LibraryResource;
import ca.sheridancollege.shtirthb.model.LibraryResourceFile;
import ca.sheridancollege.shtirthb.repository.LibraryResourceFileRepository;
import ca.sheridancollege.shtirthb.repository.LibraryResourceRepository;
import ca.sheridancollege.shtirthb.service.ResourceUploadValidator.ValidatedUpload;

/*
 * Unit tests for LibraryResourceService (the Resource Library).
 *
 * The repositories, the upload validator and the audit trail are fakes
 * (Mockito). The upload validator has its own test file, so here we only check
 * how the service uses it.
 *
 * Rules tested:
 *   - Visitors see published resources only and can download published files.
 *   - A resource needs a title, description, category and a link or document.
 *   - Text lengths and the number of links are limited.
 *   - An image needs alternative text.
 *   - A duplicate title or link is refused (editing yourself is fine).
 *   - Anything refused saves nothing and writes no audit entry.
 *   - Removing a resource marks it deleted and deletes its file.
 */
@ExtendWith(MockitoExtension.class)
class LibraryResourceServiceTest {

    private static final String ADMIN = "admin@mail.com";
    private static final String LINK_A = "https://example.org/a";
    private static final String LINK_B = "https://example.org/b";

    @Mock
    private LibraryResourceRepository resources;

    @Mock
    private LibraryResourceFileRepository files;

    @Mock
    private ResourceUploadValidator validator;

    @Mock
    private AuditTrailService auditTrail;

    private LibraryResourceService service;

    @BeforeEach
    void setUp() {
        service = new LibraryResourceService(resources, files, validator, auditTrail);
    }

    // ------------------------------------------------------------- helpers

    private LibraryResourceRequest request(
            String title, String linkUrls, String altText,
            boolean publish, boolean removeFile) {
        return new LibraryResourceRequest(
                title, "A short description", "Parenting", linkUrls,
                altText, publish, removeFile);
    }

    private LibraryResourceRequest linkRequest(String title, String linkUrls, boolean publish) {
        return request(title, linkUrls, null, publish, false);
    }

    /** A resource that already exists in the database. */
    private LibraryResource existing(long id, String title, String link) {
        LibraryResource resource = new LibraryResource(title, "Old description", "Parenting", "");
        ReflectionTestUtils.setField(resource, "id", id);
        if (link != null) {
            resource.setLinks(List.of(link));
        }
        return resource;
    }

    private LibraryResource existingFile(long id, String title, String fileName, String type) {
        LibraryResource resource = existing(id, title, null);
        resource.setFile(fileName, type, 10);
        return resource;
    }

    private void saveReturnsTheSameResource() {
        when(resources.save(any(LibraryResource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void linksAreSafe() {
        when(validator.validateLink(anyString(), eq(ADMIN)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MockMultipartFile upload(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private ValidatedUpload validUpload(String name, String type, byte[] content, boolean image) {
        return new ValidatedUpload(name, type, content, image);
    }

    private String createError(LibraryResourceRequest request, MockMultipartFile file) {
        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.create(request, file, ADMIN));
        return error.getMessage();
    }

    private void assertNothingWasSaved() {
        verify(resources, never()).save(any());
        verify(files, never()).save(any());
        verify(auditTrail, never()).actAs(any());
    }

    // -------------------------------------------------------------- public

    /*
     * Checks: The public list returns the published resources.
     * How:    Mockito fake repositories. One published link resource comes back with its title, type
     *   LINK, the link list and a published date.
     */
    @Test
    void getPublicResources_returnsThePublishedResources() {
        LibraryResource guide = existing(1, "Guide", LINK_A);
        guide.changeStatus(LibraryResource.STATUS_PUBLISHED);
        when(resources.findAllByStatusOrderByTitleAsc("PUBLISHED")).thenReturn(List.of(guide));

        List<LibraryResourcePublicResponse> result = service.getPublicResources();

        assertEquals(1, result.size());
        assertEquals("Guide", result.get(0).title());
        assertEquals("LINK", result.get(0).resourceType());
        assertEquals(List.of(LINK_A), result.get(0).links());
        assertNotNull(result.get(0).publishedAt());
    }

    /*
     * Checks: A published file resource can be downloaded.
     * How:    The download has the file name, application/pdf, the stored bytes, and inline true (a
     *   PDF opens in the browser).
     */
    @Test
    void getPublicDownload_returnsTheFileOfAPublishedResource() {
        LibraryResource pdf = existingFile(5, "Guide", "guide.pdf", "application/pdf");
        when(resources.findByIdAndStatus(5L, "PUBLISHED")).thenReturn(Optional.of(pdf));
        when(files.findById(5L)).thenReturn(
                Optional.of(new LibraryResourceFile(5L, new byte[] {1, 2, 3})));

        ResourceDownload download = service.getPublicDownload(5L);

        assertEquals("guide.pdf", download.fileName());
        assertEquals("application/pdf", download.contentType());
        assertArrayEquals(new byte[] {1, 2, 3}, download.content());
        assertTrue(download.inline());
    }

    /*
     * Checks: Word files are downloaded instead of shown in the browser.
     * How:    For a DOCX, inline is false.
     */
    @Test
    void getPublicDownload_downloadsWordFilesInsteadOfShowingThem() {
        String docx = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        LibraryResource word = existingFile(6, "Worksheet", "worksheet.docx", docx);
        when(resources.findByIdAndStatus(6L, "PUBLISHED")).thenReturn(Optional.of(word));
        when(files.findById(6L)).thenReturn(
                Optional.of(new LibraryResourceFile(6L, new byte[] {9})));

        assertFalse(service.getPublicDownload(6L).inline());
    }

    /*
     * Checks: A download of an unpublished resource gives not found.
     * How:    It throws ResourceNotFoundException and the file repository is never used.
     */
    @Test
    void getPublicDownload_throwsError_whenTheResourceIsNotPublished() {
        when(resources.findByIdAndStatus(5L, "PUBLISHED")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.getPublicDownload(5L));

        verifyNoInteractions(files);
    }

    /*
     * Checks: A download of a link-only resource gives not found.
     * How:    It throws ResourceNotFoundException and the file repository is never used.
     */
    @Test
    void getPublicDownload_throwsError_whenTheResourceIsOnlyALink() {
        LibraryResource link = existing(5, "Guide", LINK_A);
        when(resources.findByIdAndStatus(5L, "PUBLISHED")).thenReturn(Optional.of(link));

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.getPublicDownload(5L));

        verifyNoInteractions(files);
    }

    /*
     * Checks: A missing stored file gives not found.
     * How:    The file repository returns nothing, so it throws ResourceNotFoundException.
     */
    @Test
    void getPublicDownload_throwsError_whenTheStoredFileIsMissing() {
        LibraryResource pdf = existingFile(5, "Guide", "guide.pdf", "application/pdf");
        when(resources.findByIdAndStatus(5L, "PUBLISHED")).thenReturn(Optional.of(pdf));
        when(files.findById(5L)).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.getPublicDownload(5L));
    }

    // --------------------------------------------------------------- admin

    /*
     * Checks: The admin list shows everything that was not removed.
     * How:    The list has 1 item with status DRAFT.
     */
    @Test
    void getAdminResources_listsEverythingThatWasNotRemoved() {
        LibraryResource draft = existing(1, "Draft guide", LINK_A);
        when(resources.findAllByStatusNotOrderByUpdatedAtDesc("DELETED"))
                .thenReturn(List.of(draft));

        List<LibraryResourceAdminResponse> result = service.getAdminResources();

        assertEquals(1, result.size());
        assertEquals("DRAFT", result.get(0).status());
    }

    /*
     * Checks: The upload rules come from the validator.
     * How:    getUploadRules() returns the same object as the validator.
     */
    @Test
    void getUploadRules_comesFromTheValidator() {
        UploadRulesResponse rules = new UploadRulesResponse(
                1024, "1 KB", List.of("pdf"), "PDF");
        when(validator.rules()).thenReturn(rules);

        assertSame(rules, service.getUploadRules());
    }

    // -------------------------------------------------------------- create

    /*
     * Checks: A new link resource is saved as a DRAFT.
     * How:    The result has the title, status DRAFT, type LINK, the link, and no published date.
     */
    @Test
    void create_savesALinkResourceAsADraft() {
        linksAreSafe();
        saveReturnsTheSameResource();

        LibraryResourceAdminResponse result = service.create(
                linkRequest("Guide", LINK_A, false), null, ADMIN);

        assertEquals("Guide", result.title());
        assertEquals("DRAFT", result.status());
        assertEquals("LINK", result.resourceType());
        assertEquals(LINK_A, result.linkUrl());
        assertNull(result.publishedAt());
        verify(auditTrail).actAs(ADMIN);
        verifyNoInteractions(files);
    }

    /*
     * Checks: Creating with publish makes the resource public.
     * How:    The status is PUBLISHED and there is a published date.
     */
    @Test
    void create_withPublish_makesTheResourcePublic() {
        linksAreSafe();
        saveReturnsTheSameResource();

        LibraryResourceAdminResponse result = service.create(
                linkRequest("Guide", LINK_A, true), null, ADMIN);

        assertEquals("PUBLISHED", result.status());
        assertNotNull(result.publishedAt());
    }

    /*
     * Checks: Spaces around the text fields are removed.
     * How:    Title, description and category are trimmed.
     */
    @Test
    void create_trimsTheTextFields() {
        linksAreSafe();
        saveReturnsTheSameResource();
        LibraryResourceRequest spaced = new LibraryResourceRequest(
                "  Guide  ", "  Short text  ", "  Parenting  ", LINK_A, null, false, false);

        LibraryResourceAdminResponse result = service.create(spaced, null, ADMIN);

        assertEquals("Guide", result.title());
        assertEquals("Short text", result.description());
        assertEquals("Parenting", result.category());
    }

    /*
     * Checks: Links are read one per line, ignoring blank lines and repeats.
     * How:    The result has the two different links. The validator checked each link once (2 calls).
     */
    @Test
    void create_readsOneLinkPerLine_andIgnoresBlankLinesAndRepeats() {
        linksAreSafe();
        saveReturnsTheSameResource();

        LibraryResourceAdminResponse result = service.create(
                linkRequest("Guide", LINK_A + "\n\n  " + LINK_B + " \r\n" + LINK_A, false),
                null, ADMIN);

        assertEquals(List.of(LINK_A, LINK_B), result.links());
        assertEquals(LINK_A, result.linkUrl());
        verify(validator, times(2)).validateLink(anyString(), eq(ADMIN));
    }

    /*
     * Checks: An uploaded file is saved together with the resource.
     * How:    The result has type FILE, the file name, the content type and the size. The file
     *   repository saved the file.
     */
    @Test
    void create_savesAnUploadedFileTogetherWithTheResource() {
        saveReturnsTheSameResource();
        byte[] content = {1, 2, 3, 4};
        MockMultipartFile file = upload("guide.pdf", content);
        when(validator.validateFile(file, ADMIN))
                .thenReturn(validUpload("guide.pdf", "application/pdf", content, false));

        LibraryResourceAdminResponse result = service.create(
                request("Guide", null, null, true, false), file, ADMIN);

        assertEquals("FILE", result.resourceType());
        assertEquals("guide.pdf", result.fileName());
        assertEquals("application/pdf", result.fileContentType());
        assertEquals(4L, result.fileSize());

        ArgumentCaptor<LibraryResourceFile> saved = ArgumentCaptor.forClass(LibraryResourceFile.class);
        verify(files).save(saved.capture());
        assertArrayEquals(content, saved.getValue().getContent());
    }

    /*
     * Checks: An image with alternative text is accepted.
     * How:    The result has the alt text.
     */
    @Test
    void create_acceptsAnImageThatHasAlternativeText() {
        saveReturnsTheSameResource();
        byte[] content = {1, 2};
        MockMultipartFile file = upload("photo.png", content);
        when(validator.validateFile(file, ADMIN))
                .thenReturn(validUpload("photo.png", "image/png", content, true));

        LibraryResourceAdminResponse result = service.create(
                request("Poster", null, "A butterfly on a flower", false, false), file, ADMIN);

        assertEquals("A butterfly on a flower", result.altText());
    }

    /*
     * Checks: An image without alternative text is refused.
     * How:    The message starts with "Required content is missing: alternative text." and nothing is
     *   saved.
     */
    @Test
    void create_refusesAnImageWithoutAlternativeText() {
        byte[] content = {1, 2};
        MockMultipartFile file = upload("photo.png", content);
        when(validator.validateFile(file, ADMIN))
                .thenReturn(validUpload("photo.png", "image/png", content, true));

        String message = createError(request("Poster", null, "  ", false, false), file);

        assertTrue(message.startsWith("Required content is missing: alternative text."));
        assertNothingWasSaved();
    }

    /*
     * Checks: The error names every missing part before any file is checked.
     * How:    The message lists title, short description, category and link or document. The validator
     *   is not used and nothing is saved.
     */
    @Test
    void create_namesEveryMissingPart_beforeCheckingAnyFile() {
        String message = createError(
                new LibraryResourceRequest("", " ", null, null, null, true, false), null);

        assertEquals("Required content is missing: title, short description, category, link or document.",
                message);
        verifyNoInteractions(validator);
        assertNothingWasSaved();
    }

    /*
     * Checks: A missing request is refused.
     * How:    A null request throws ValidationException and nothing is saved.
     */
    @Test
    void create_refusesAMissingRequest() {
        assertThrows(AppExceptions.ValidationException.class,
                () -> service.create(null, null, ADMIN));

        assertNothingWasSaved();
    }

    /*
     * Checks: Text that is too long is refused.
     * How:    A title over 80, a description over 300, a category over 40 and an alt text over 150
     *   characters each give their own message. Nothing is saved.
     */
    @Test
    void create_refusesTextThatIsTooLong() {
        assertEquals("The title must be 80 characters or fewer.",
                createError(linkRequest("t".repeat(81), LINK_A, true), null));
        assertEquals("The short description must be 300 characters or fewer.",
                createError(new LibraryResourceRequest(
                        "Guide", "d".repeat(301), "Parenting", LINK_A, null, true, false), null));
        assertEquals("The category must be 40 characters or fewer.",
                createError(new LibraryResourceRequest(
                        "Guide", "Short", "c".repeat(41), LINK_A, null, true, false), null));
        assertEquals("The alternative text must be 150 characters or fewer.",
                createError(request("Guide", LINK_A, "a".repeat(151), true, false), null));

        assertNothingWasSaved();
    }

    /*
     * Checks: The maximum lengths are accepted.
     * How:    Fields at exactly the limits do not throw.
     */
    @Test
    void create_acceptsTheMaximumLengths() {
        linksAreSafe();
        saveReturnsTheSameResource();

        assertDoesNotThrow(() -> service.create(new LibraryResourceRequest(
                "t".repeat(80), "d".repeat(300), "c".repeat(40), LINK_A, null, true, false),
                null, ADMIN));
    }

    /*
     * Checks: More than five links are refused.
     * How:    The message is "You can add up to 5 links to one resource." The validator is not used
     *   and nothing is saved.
     */
    @Test
    void create_refusesMoreThanFiveLinks() {
        String sixLinks = String.join("\n",
                "https://example.org/1", "https://example.org/2", "https://example.org/3",
                "https://example.org/4", "https://example.org/5", "https://example.org/6");

        assertEquals("You can add up to 5 links to one resource.",
                createError(linkRequest("Guide", sixLinks, true), null));

        verifyNoInteractions(validator);
        assertNothingWasSaved();
    }

    /*
     * Checks: Nothing is saved when a link is not safe.
     * How:    The validator throws for the link ("This link was rejected."). The error is passed up
     *   and nothing is saved.
     */
    @Test
    void create_savesNothing_whenALinkIsNotSafe() {
        when(validator.validateLink(anyString(), eq(ADMIN)))
                .thenThrow(new AppExceptions.ValidationException("This link was rejected."));

        assertEquals("This link was rejected.",
                createError(linkRequest("Guide", "javascript:alert(1)", true), null));

        assertNothingWasSaved();
    }

    /*
     * Checks: Nothing is saved when the file is not safe.
     * How:    The validator throws for the file ("This file was rejected."). Nothing is saved.
     */
    @Test
    void create_savesNothing_whenTheFileIsNotSafe() {
        MockMultipartFile file = upload("virus.exe", new byte[] {1});
        when(validator.validateFile(file, ADMIN))
                .thenThrow(new AppExceptions.ValidationException("This file was rejected."));

        assertEquals("This file was rejected.",
                createError(request("Guide", null, null, true, false), file));

        assertNothingWasSaved();
    }

    /*
     * Checks: A duplicate title is refused, whatever the capital letters.
     * How:    The repository already has the title. It throws ConflictException ("same title or link
     *   already exists") and nothing is saved.
     */
    @Test
    void create_refusesADuplicateTitle_inAnyCapitalisation() {
        linksAreSafe();
        when(resources.findAllByTitleIgnoreCaseAndStatusNot("GUIDE", "DELETED"))
                .thenReturn(List.of(existing(9, "Guide", LINK_B)));

        AppExceptions.ConflictException error = assertThrows(
                AppExceptions.ConflictException.class,
                () -> service.create(linkRequest("GUIDE", LINK_A, true), null, ADMIN));

        assertTrue(error.getMessage().contains("same title or link already exists (\"Guide\")"));
        assertNothingWasSaved();
    }

    /*
     * Checks: A duplicate link is refused.
     * How:    Another resource already uses the link. It throws ConflictException that names that
     *   resource, and nothing is saved.
     */
    @Test
    void create_refusesADuplicateLink() {
        linksAreSafe();
        when(resources.findAllByLinkUrlAndStatusNot(LINK_A, "DELETED"))
                .thenReturn(List.of(existing(9, "Other guide", LINK_A)));

        AppExceptions.ConflictException error = assertThrows(
                AppExceptions.ConflictException.class,
                () -> service.create(linkRequest("New guide", LINK_A, true), null, ADMIN));

        assertTrue(error.getMessage().contains("(\"Other guide\")"));
        assertNothingWasSaved();
    }

    // -------------------------------------------------------------- update

    /*
     * Checks: Updating an unknown resource gives not found.
     * How:    It throws ResourceNotFoundException and nothing is saved.
     */
    @Test
    void update_throwsError_whenTheResourceDoesNotExist() {
        when(resources.findByIdAndStatusNot(5L, "DELETED")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.update(5L, linkRequest("Guide", LINK_A, true), null, ADMIN));

        assertNothingWasSaved();
    }

    /*
     * Checks: An update changes the text and the status.
     * How:    The result has the same id, the new title, the new description and status PUBLISHED. The
     *   admin is recorded.
     */
    @Test
    void update_changesTheTextAndStatus() {
        LibraryResource resource = existing(5, "Guide", LINK_A);
        when(resources.findByIdAndStatusNot(5L, "DELETED")).thenReturn(Optional.of(resource));
        linksAreSafe();
        saveReturnsTheSameResource();

        LibraryResourceAdminResponse result = service.update(
                5L, linkRequest("New title", LINK_A, true), null, ADMIN);

        assertEquals(5L, result.id());
        assertEquals("New title", result.title());
        assertEquals("A short description", result.description());
        assertEquals("PUBLISHED", result.status());
        verify(auditTrail).actAs(ADMIN);
    }

    /*
     * Checks: A resource may keep its own title and link on update.
     * How:    The duplicate check ignores the resource itself, so it does not throw.
     */
    @Test
    void update_allowsAResourceToKeepItsOwnTitleAndLink() {
        LibraryResource resource = existing(5, "Guide", LINK_A);
        when(resources.findByIdAndStatusNot(5L, "DELETED")).thenReturn(Optional.of(resource));
        when(resources.findAllByTitleIgnoreCaseAndStatusNot("Guide", "DELETED"))
                .thenReturn(List.of(resource));
        when(resources.findAllByLinkUrlAndStatusNot(LINK_A, "DELETED"))
                .thenReturn(List.of(resource));
        linksAreSafe();
        saveReturnsTheSameResource();

        assertDoesNotThrow(() ->
                service.update(5L, linkRequest("Guide", LINK_A, true), null, ADMIN));
    }

    /*
     * Checks: An update cannot use the title of a different resource.
     * How:    It throws ConflictException and nothing is saved.
     */
    @Test
    void update_refusesTheTitleOfADifferentResource() {
        LibraryResource resource = existing(5, "Guide", LINK_A);
        when(resources.findByIdAndStatusNot(5L, "DELETED")).thenReturn(Optional.of(resource));
        when(resources.findAllByTitleIgnoreCaseAndStatusNot("Other", "DELETED"))
                .thenReturn(List.of(existing(9, "Other", LINK_B)));
        linksAreSafe();

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.update(5L, linkRequest("Other", LINK_A, true), null, ADMIN));

        assertNothingWasSaved();
    }

    /*
     * Checks: The links stay as they are when no links are sent.
     * How:    The result keeps the old link and the validator is never asked to check a link.
     */
    @Test
    void update_leavesTheLinksAsTheyAre_whenNoLinksAreSent() {
        LibraryResource resource = existing(5, "Guide", LINK_A);
        when(resources.findByIdAndStatusNot(5L, "DELETED")).thenReturn(Optional.of(resource));
        saveReturnsTheSameResource();

        LibraryResourceAdminResponse result = service.update(
                5L, linkRequest("Guide", null, true), null, ADMIN);

        assertEquals(List.of(LINK_A), result.links());
        verify(validator, never()).validateLink(any(), any());
    }

    /*
     * Checks: The last link cannot be removed when there is no document.
     * How:    The message is "Required content is missing: link or document." and nothing is saved.
     */
    @Test
    void update_refusesToRemoveTheLastLink_whenThereIsNoDocument() {
        LibraryResource resource = existing(5, "Guide", LINK_A);
        when(resources.findByIdAndStatusNot(5L, "DELETED")).thenReturn(Optional.of(resource));

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.update(5L, linkRequest("Guide", "", true), null, ADMIN));

        assertEquals("Required content is missing: link or document.", error.getMessage());
        assertNothingWasSaved();
    }

    /*
     * Checks: The existing file is kept when no new file is sent.
     * How:    The result still shows guide.pdf and type FILE. The file repository and the file
     *   validator are not used.
     */
    @Test
    void update_keepsTheExistingFile_whenNoNewFileIsSent() {
        LibraryResource resource = existingFile(5, "Guide", "guide.pdf", "application/pdf");
        when(resources.findByIdAndStatusNot(5L, "DELETED")).thenReturn(Optional.of(resource));
        saveReturnsTheSameResource();

        LibraryResourceAdminResponse result = service.update(
                5L, linkRequest("Guide", null, true), null, ADMIN);

        assertEquals("guide.pdf", result.fileName());
        assertEquals("FILE", result.resourceType());
        verifyNoInteractions(files);
        verify(validator, never()).validateFile(any(), any());
    }

    /*
     * Checks: A new file replaces the old one.
     * How:    The result shows new.pdf with size 3. The new file is saved and the old one is not
     *   deleted separately.
     */
    @Test
    void update_replacesTheFileWithANewOne() {
        LibraryResource resource = existingFile(5, "Guide", "old.pdf", "application/pdf");
        when(resources.findByIdAndStatusNot(5L, "DELETED")).thenReturn(Optional.of(resource));
        saveReturnsTheSameResource();
        byte[] content = {7, 7, 7};
        MockMultipartFile file = upload("new.pdf", content);
        when(validator.validateFile(file, ADMIN))
                .thenReturn(validUpload("new.pdf", "application/pdf", content, false));

        LibraryResourceAdminResponse result = service.update(
                5L, linkRequest("Guide", null, true), file, ADMIN);

        assertEquals("new.pdf", result.fileName());
        assertEquals(3L, result.fileSize());
        verify(files).save(any(LibraryResourceFile.class));
        verify(files, never()).deleteById(any());
    }

    /*
     * Checks: The file is removed when the admin asks.
     * How:    The result has no file name and type LINK. deleteById(5) is called.
     */
    @Test
    void update_removesTheFile_whenTheAdminAsksForIt() {
        LibraryResource resource = existingFile(5, "Guide", "guide.pdf", "application/pdf");
        when(resources.findByIdAndStatusNot(5L, "DELETED")).thenReturn(Optional.of(resource));
        linksAreSafe();
        saveReturnsTheSameResource();

        LibraryResourceAdminResponse result = service.update(
                5L, request("Guide", LINK_A, null, true, true), null, ADMIN);

        assertNull(result.fileName());
        assertEquals("LINK", result.resourceType());
        assertEquals(LINK_A, result.linkUrl());
        verify(files).deleteById(5L);
    }

    /*
     * Checks: The only document cannot be removed when there is no link.
     * How:    The message is "Required content is missing: link or document." and nothing is saved.
     */
    @Test
    void update_refusesToRemoveTheOnlyDocument_whenThereIsNoLink() {
        LibraryResource resource = existingFile(5, "Guide", "guide.pdf", "application/pdf");
        when(resources.findByIdAndStatusNot(5L, "DELETED")).thenReturn(Optional.of(resource));

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.update(5L, request("Guide", null, null, true, true), null, ADMIN));

        assertEquals("Required content is missing: link or document.", error.getMessage());
        assertNothingWasSaved();
    }

    /*
     * Checks: An existing image cannot lose its alternative text.
     * How:    The message mentions "alternative text" and nothing is saved.
     */
    @Test
    void update_refusesAnExistingImageThatLostItsAlternativeText() {
        LibraryResource resource = existingFile(5, "Poster", "photo.png", "image/png");
        when(resources.findByIdAndStatusNot(5L, "DELETED")).thenReturn(Optional.of(resource));

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.update(5L, request("Poster", null, "", true, false), null, ADMIN));

        assertTrue(error.getMessage().contains("alternative text"));
        assertNothingWasSaved();
    }

    // -------------------------------------------------------------- remove

    /*
     * Checks: Removing marks the resource DELETED and deletes its file.
     * How:    The status is DELETED. The admin is recorded, the resource is saved and deleteById(5) is
     *   called.
     */
    @Test
    void remove_marksTheResourceAsDeletedAndDeletesItsFile() {
        LibraryResource resource = existingFile(5, "Guide", "guide.pdf", "application/pdf");
        when(resources.findByIdAndStatusNot(5L, "DELETED")).thenReturn(Optional.of(resource));

        service.remove(5L, ADMIN);

        assertEquals("DELETED", resource.getStatus());
        verify(auditTrail).actAs(ADMIN);
        verify(resources).save(resource);
        verify(files).deleteById(5L);
    }

    /*
     * Checks: Removing an unknown resource gives not found.
     * How:    It throws ResourceNotFoundException and nothing is saved, deleted or recorded.
     */
    @Test
    void remove_throwsError_whenTheResourceDoesNotExist() {
        when(resources.findByIdAndStatusNot(5L, "DELETED")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.remove(5L, ADMIN));

        verify(resources, never()).save(any());
        verify(files, never()).deleteById(any());
        verify(auditTrail, never()).actAs(any());
    }
}