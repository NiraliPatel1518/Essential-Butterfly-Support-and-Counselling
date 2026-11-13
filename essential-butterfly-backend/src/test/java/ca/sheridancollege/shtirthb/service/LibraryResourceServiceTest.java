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

    @Test
    void getPublicDownload_downloadsWordFilesInsteadOfShowingThem() {
        String docx = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        LibraryResource word = existingFile(6, "Worksheet", "worksheet.docx", docx);
        when(resources.findByIdAndStatus(6L, "PUBLISHED")).thenReturn(Optional.of(word));
        when(files.findById(6L)).thenReturn(
                Optional.of(new LibraryResourceFile(6L, new byte[] {9})));

        assertFalse(service.getPublicDownload(6L).inline());
    }

    @Test
    void getPublicDownload_throwsError_whenTheResourceIsNotPublished() {
        when(resources.findByIdAndStatus(5L, "PUBLISHED")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.getPublicDownload(5L));

        verifyNoInteractions(files);
    }

    @Test
    void getPublicDownload_throwsError_whenTheResourceIsOnlyALink() {
        LibraryResource link = existing(5, "Guide", LINK_A);
        when(resources.findByIdAndStatus(5L, "PUBLISHED")).thenReturn(Optional.of(link));

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.getPublicDownload(5L));

        verifyNoInteractions(files);
    }

    @Test
    void getPublicDownload_throwsError_whenTheStoredFileIsMissing() {
        LibraryResource pdf = existingFile(5, "Guide", "guide.pdf", "application/pdf");
        when(resources.findByIdAndStatus(5L, "PUBLISHED")).thenReturn(Optional.of(pdf));
        when(files.findById(5L)).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.getPublicDownload(5L));
    }

    // --------------------------------------------------------------- admin

    @Test
    void getAdminResources_listsEverythingThatWasNotRemoved() {
        LibraryResource draft = existing(1, "Draft guide", LINK_A);
        when(resources.findAllByStatusNotOrderByUpdatedAtDesc("DELETED"))
                .thenReturn(List.of(draft));

        List<LibraryResourceAdminResponse> result = service.getAdminResources();

        assertEquals(1, result.size());
        assertEquals("DRAFT", result.get(0).status());
    }

    @Test
    void getUploadRules_comesFromTheValidator() {
        UploadRulesResponse rules = new UploadRulesResponse(
                1024, "1 KB", List.of("pdf"), "PDF");
        when(validator.rules()).thenReturn(rules);

        assertSame(rules, service.getUploadRules());
    }

    // -------------------------------------------------------------- create

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

    @Test
    void create_withPublish_makesTheResourcePublic() {
        linksAreSafe();
        saveReturnsTheSameResource();

        LibraryResourceAdminResponse result = service.create(
                linkRequest("Guide", LINK_A, true), null, ADMIN);

        assertEquals("PUBLISHED", result.status());
        assertNotNull(result.publishedAt());
    }

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

    @Test
    void create_namesEveryMissingPart_beforeCheckingAnyFile() {
        String message = createError(
                new LibraryResourceRequest("", " ", null, null, null, true, false), null);

        assertEquals("Required content is missing: title, short description, category, link or document.",
                message);
        verifyNoInteractions(validator);
        assertNothingWasSaved();
    }

    @Test
    void create_refusesAMissingRequest() {
        assertThrows(AppExceptions.ValidationException.class,
                () -> service.create(null, null, ADMIN));

        assertNothingWasSaved();
    }

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

    @Test
    void create_acceptsTheMaximumLengths() {
        linksAreSafe();
        saveReturnsTheSameResource();

        assertDoesNotThrow(() -> service.create(new LibraryResourceRequest(
                "t".repeat(80), "d".repeat(300), "c".repeat(40), LINK_A, null, true, false),
                null, ADMIN));
    }

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

    @Test
    void create_savesNothing_whenALinkIsNotSafe() {
        when(validator.validateLink(anyString(), eq(ADMIN)))
                .thenThrow(new AppExceptions.ValidationException("This link was rejected."));

        assertEquals("This link was rejected.",
                createError(linkRequest("Guide", "javascript:alert(1)", true), null));

        assertNothingWasSaved();
    }

    @Test
    void create_savesNothing_whenTheFileIsNotSafe() {
        MockMultipartFile file = upload("virus.exe", new byte[] {1});
        when(validator.validateFile(file, ADMIN))
                .thenThrow(new AppExceptions.ValidationException("This file was rejected."));

        assertEquals("This file was rejected.",
                createError(request("Guide", null, null, true, false), file));

        assertNothingWasSaved();
    }

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

    @Test
    void update_throwsError_whenTheResourceDoesNotExist() {
        when(resources.findByIdAndStatusNot(5L, "DELETED")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.update(5L, linkRequest("Guide", LINK_A, true), null, ADMIN));

        assertNothingWasSaved();
    }

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