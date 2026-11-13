package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import ca.sheridancollege.shtirthb.config.AppProperties;
import ca.sheridancollege.shtirthb.dto.UploadRulesResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;

/*
 * Unit tests for ResourceUploadValidator.
 *
 * The admin can upload files and add links to the Resource Library. This class
 * decides if a file or a link is safe. These are security tests:
 *   - Only PDF, DOCX, PNG and JPEG files are accepted.
 *   - The file contents must match the file type (a text file named .pdf is refused).
 *   - Programs and web pages are refused, even hidden in a double extension.
 *   - Links must be https and must not point to local or private addresses.
 *   - Every unsafe file or link is written to the audit trail.
 *
 * No real files are used. The files are small fake byte arrays.
 */
@ExtendWith(MockitoExtension.class)
class ResourceUploadValidatorTest {

    private static final String ADMIN = "admin@mail.com";
    private static final String AUDIT_ENTITY = "library_resources";

    private static final String INVALID_LINK =
            "Enter a valid web address that starts with https://, "
            + "for example https://example.org/guide.";

    @Mock
    private AuditTrailService auditTrail;

    private AppProperties properties;
    private ResourceUploadValidator validator;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        validator = new ResourceUploadValidator(properties, auditTrail);
    }

    // ------------------------------------------------------------- helpers

    private static byte[] pdfBytes() {
        return "%PDF-1.4 a small test document".getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] pngBytes() {
        return new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 1
        };
    }

    private static byte[] jpegBytes() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    }

    private static byte[] docxBytes() {
        byte[] start = {'P', 'K', 0x03, 0x04};
        byte[] rest = "[Content_Types].xml word/document.xml"
                .getBytes(StandardCharsets.US_ASCII);
        byte[] all = new byte[start.length + rest.length];
        System.arraycopy(start, 0, all, 0, start.length);
        System.arraycopy(rest, 0, all, start.length, rest.length);
        return all;
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private AppExceptions.ValidationException fileError(MultipartFile file) {
        return assertThrows(AppExceptions.ValidationException.class,
                () -> validator.validateFile(file, ADMIN));
    }

    private void assertUnsafeFile(MultipartFile file, String reason) {
        AppExceptions.ValidationException error = fileError(file);

        assertEquals(ResourceUploadValidator.UNSAFE_FILE_MESSAGE, error.getMessage());
        verify(auditTrail).recordSecurityEvent(
                ADMIN, ResourceUploadValidator.UNSAFE_FILE_EVENT, AUDIT_ENTITY, reason);
    }

    private AppExceptions.ValidationException linkError(String link) {
        return assertThrows(AppExceptions.ValidationException.class,
                () -> validator.validateLink(link, ADMIN));
    }

    private void assertUnsafeLink(String link, String reason) {
        AppExceptions.ValidationException error = linkError(link);

        assertEquals(ResourceUploadValidator.UNSAFE_LINK_MESSAGE, error.getMessage());
        verify(auditTrail).recordSecurityEvent(
                ADMIN, ResourceUploadValidator.UNSAFE_LINK_EVENT, AUDIT_ENTITY, reason);
    }

    private void assertInvalidLink(String link) {
        assertEquals(INVALID_LINK, linkError(link).getMessage());
        verify(auditTrail, never()).recordSecurityEvent(any(), any(), any(), any());
    }

    // -------------------------------------------------------- good files

    @Test
    void validateFile_acceptsAPdf() {
        ResourceUploadValidator.ValidatedUpload result =
                validator.validateFile(file("guide.pdf", pdfBytes()), ADMIN);

        assertEquals("guide.pdf", result.displayName());
        assertEquals("application/pdf", result.contentType());
        assertFalse(result.image());
        assertArrayEquals(pdfBytes(), result.content());
        verifyNoInteractions(auditTrail);
    }

    @Test
    void validateFile_acceptsAPngAndMarksItAsAnImage() {
        ResourceUploadValidator.ValidatedUpload result =
                validator.validateFile(file("photo.png", pngBytes()), ADMIN);

        assertEquals("image/png", result.contentType());
        assertTrue(result.image());
    }

    @Test
    void validateFile_acceptsJpgAndJpeg() {
        assertEquals("image/jpeg",
                validator.validateFile(file("a.jpg", jpegBytes()), ADMIN).contentType());
        assertEquals("image/jpeg",
                validator.validateFile(file("b.jpeg", jpegBytes()), ADMIN).contentType());
    }

    @Test
    void validateFile_acceptsADocx() {
        ResourceUploadValidator.ValidatedUpload result =
                validator.validateFile(file("worksheet.docx", docxBytes()), ADMIN);

        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                result.contentType());
        assertFalse(result.image());
    }

    @Test
    void validateFile_acceptsCapitalLettersInTheExtension() {
        assertEquals("application/pdf",
                validator.validateFile(file("REPORT.PDF", pdfBytes()), ADMIN).contentType());
    }

    // --------------------------------------------------- unsafe file names

    @Test
    void validateFile_throwsError_whenTheFileHasNoName() {
        AppExceptions.ValidationException error = fileError(file("", pdfBytes()));

        assertTrue(error.getMessage().contains("has no name"));
        verifyNoInteractions(auditTrail);
    }

    @Test
    void validateFile_refusesNamesThatTryToChangeFolder() {
        assertUnsafeFile(file("../secret.pdf", pdfBytes()), "Unsafe file name");
    }

    @Test
    void validateFile_refusesNamesWithSlashes() {
        assertUnsafeFile(file("folder/guide.pdf", pdfBytes()), "Unsafe file name");
    }

    @Test
    void validateFile_refusesNamesWithBackslashes() {
        assertUnsafeFile(file("folder\\guide.pdf", pdfBytes()), "Unsafe file name");
    }

    @Test
    void validateFile_refusesNamesWithControlCharacters() {
        assertUnsafeFile(file("guide\u0001.pdf", pdfBytes()), "Unsafe file name");
    }

    @Test
    void validateFile_refusesNamesLongerThan255Characters() {
        String longName = "a".repeat(252) + ".pdf";

        assertUnsafeFile(file(longName, pdfBytes()), "Unsafe file name");
    }

    @Test
    void validateFile_refusesAProgramHiddenInADoubleExtension() {
        assertUnsafeFile(file("report.exe.pdf", pdfBytes()),
                "Executable or web-page extension in file name");
    }

    @Test
    void validateFile_refusesAWebPageHiddenInADoubleExtension() {
        assertUnsafeFile(file("photo.html.png", pngBytes()),
                "Executable or web-page extension in file name");
    }

    @Test
    void validateFile_refusesProgramAndScriptFiles() {
        for (String name : List.of("setup.exe", "run.bat", "run.sh", "page.svg", "app.js", "x.php")) {
            AppExceptions.ValidationException error = fileError(file(name, pdfBytes()));

            assertEquals(ResourceUploadValidator.UNSAFE_FILE_MESSAGE, error.getMessage(),
                    name + " should be refused as unsafe");
        }

        verify(auditTrail, times(6)).recordSecurityEvent(
                eq(ADMIN), eq(ResourceUploadValidator.UNSAFE_FILE_EVENT),
                eq(AUDIT_ENTITY), anyString());
    }

    // ------------------------------------------------ unsupported / size

    @Test
    void validateFile_refusesAnUnsupportedType_withoutASecurityEvent() {
        AppExceptions.ValidationException error = fileError(file("notes.txt", pdfBytes()));

        assertEquals("This file type is not supported. Please upload a PDF, DOCX, PNG or JPEG file.",
                error.getMessage());
        verifyNoInteractions(auditTrail);
    }

    @Test
    void validateFile_refusesAFileWithoutAnExtension() {
        assertEquals("This file type is not supported. Please upload a PDF, DOCX, PNG or JPEG file.",
                fileError(file("README", pdfBytes())).getMessage());
        assertEquals("This file type is not supported. Please upload a PDF, DOCX, PNG or JPEG file.",
                fileError(file(".pdf", pdfBytes())).getMessage());
    }

    @Test
    void validateFile_followsTheConfiguredFileTypes() {
        properties.getContent().setAllowedFileExtensions(List.of("pdf"));

        AppExceptions.ValidationException error = fileError(file("photo.png", pngBytes()));

        assertEquals("This file type is not supported. Please upload a PDF file.",
                error.getMessage());
        assertDoesNotThrow(() -> validator.validateFile(file("guide.pdf", pdfBytes()), ADMIN));
    }

    @Test
    void validateFile_refusesAFileThatIsTooLarge() {
        properties.getContent().setMaxFileSizeBytes(100);
        byte[] big = new byte[101];
        System.arraycopy(pdfBytes(), 0, big, 0, 5);

        AppExceptions.PayloadTooLargeException error = assertThrows(
                AppExceptions.PayloadTooLargeException.class,
                () -> validator.validateFile(file("big.pdf", big), ADMIN));

        assertEquals("This file is too large. The maximum size is 1 KB.", error.getMessage());
    }

    @Test
    void validateFile_acceptsAFileOfExactlyTheMaximumSize() {
        properties.getContent().setMaxFileSizeBytes(pdfBytes().length);

        assertDoesNotThrow(() -> validator.validateFile(file("ok.pdf", pdfBytes()), ADMIN));
    }

    @Test
    void validateFile_refusesAnEmptyFile() {
        AppExceptions.ValidationException error = fileError(file("empty.pdf", new byte[0]));

        assertTrue(error.getMessage().contains("is empty"));
        verifyNoInteractions(auditTrail);
    }

    // ------------------------------------------------- contents vs. type

    @Test
    void validateFile_refusesATextFileRenamedAsPdf() {
        assertUnsafeFile(file("fake.pdf", "just some text".getBytes(StandardCharsets.UTF_8)),
                "File contents do not match the .pdf type");
    }

    @Test
    void validateFile_refusesAPdfThatCanRunJavaScript() {
        byte[] bad = "%PDF-1.4 /JavaScript (app.alert(1))".getBytes(StandardCharsets.US_ASCII);

        assertUnsafeFile(file("active.pdf", bad), "File contents do not match the .pdf type");
    }

    @Test
    void validateFile_refusesAPdfThatCanLaunchAProgram() {
        byte[] bad = "%PDF-1.4 /Launch /Win cmd.exe".getBytes(StandardCharsets.US_ASCII);

        assertUnsafeFile(file("launch.pdf", bad), "File contents do not match the .pdf type");
    }

    @Test
    void validateFile_refusesAPdfRenamedAsPng() {
        assertUnsafeFile(file("fake.png", pdfBytes()), "File contents do not match the .png type");
    }

    @Test
    void validateFile_refusesAPngRenamedAsJpg() {
        assertUnsafeFile(file("fake.jpg", pngBytes()), "File contents do not match the .jpg type");
    }

    @Test
    void validateFile_refusesADocxThatIsNotAZipFile() {
        assertUnsafeFile(file("fake.docx", "not a zip".getBytes(StandardCharsets.UTF_8)),
                "File contents do not match the .docx type");
    }

    @Test
    void validateFile_refusesAZipFileThatIsNotAWordDocument() {
        byte[] zipOnly = {'P', 'K', 0x03, 0x04, 1, 2, 3, 4, 5, 6};

        assertUnsafeFile(file("fake.docx", zipOnly), "File contents do not match the .docx type");
    }

    @Test
    void validateFile_throwsError_whenTheFileCannotBeRead() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getOriginalFilename()).thenReturn("guide.pdf");
        when(broken.getSize()).thenReturn(10L);
        when(broken.getBytes()).thenThrow(new IOException("disk error"));

        AppExceptions.ServiceUnavailableException error = assertThrows(
                AppExceptions.ServiceUnavailableException.class,
                () -> validator.validateFile(broken, ADMIN));

        assertTrue(error.getMessage().contains("could not read the uploaded file"));
    }

    // ------------------------------------------------------------ good links

    @Test
    void validateLink_acceptsAnHttpsLinkAndTrimsIt() {
        assertEquals("https://example.org/guide",
                validator.validateLink("  https://example.org/guide \n", ADMIN));
        verifyNoInteractions(auditTrail);
    }

    @Test
    void validateLink_acceptsCapitalLettersInTheScheme() {
        assertEquals("HTTPS://Example.org/Path",
                validator.validateLink("HTTPS://Example.org/Path", ADMIN));
    }

    @Test
    void validateLink_acceptsAddressesWithAPortAndAQuery() {
        assertDoesNotThrow(() -> validator.validateLink(
                "https://www.example.org:8443/a/b?x=1&y=2#top", ADMIN));
    }

    @Test
    void validateLink_acceptsHttpOnlyWhenItIsConfigured() {
        assertInvalidLink("http://example.org/guide");

        properties.getContent().setAllowedLinkSchemes(List.of("https", "http"));

        assertDoesNotThrow(() -> validator.validateLink("http://example.org/guide", ADMIN));
    }

    // --------------------------------------------------------- bad links

    @Test
    void validateLink_refusesALinkThatIsTooLong() {
        String link = "https://example.org/" + "a".repeat(2000);

        AppExceptions.ValidationException error = linkError(link);

        assertTrue(error.getMessage().contains("too long"));
        verifyNoInteractions(auditTrail);
    }

    @Test
    void validateLink_refusesLinksWithControlCharacters() {
        assertUnsafeLink("https://exam\tple.org/guide", "Link contains control characters");
    }

    @Test
    void validateLink_refusesAnAddressThatIsNotWellFormed() {
        assertInvalidLink("https://exa mple.org/guide");
    }

    @Test
    void validateLink_refusesALinkWithoutAScheme() {
        assertInvalidLink("example.org/guide");
    }

    @Test
    void validateLink_refusesSchemesThatAreNotAllowed() {
        assertInvalidLink("ftp://example.org/file");
        assertInvalidLink("mailto:someone@example.org");
    }

    @Test
    void validateLink_refusesScriptAndDataLinks() {
        assertUnsafeLink("javascript:alert(1)", "Blocked link type: javascript");
        assertUnsafeLink("data:text/plain,hello", "Blocked link type: data");
        assertUnsafeLink("vbscript:msgbox(1)", "Blocked link type: vbscript");
        assertUnsafeLink("file:///etc/passwd", "Blocked link type: file");
        assertUnsafeLink("blob:https://example.org/id", "Blocked link type: blob");
    }

    @Test
    void validateLink_refusesLinksWithAUserNameAndPassword() {
        assertUnsafeLink("https://user:pass@example.org/guide",
                "Link contains embedded credentials");
    }

    @Test
    void validateLink_refusesALinkWithoutAHostName() {
        assertInvalidLink("https://");
        assertInvalidLink("https:///guide");
    }

    @Test
    void validateLink_refusesLocalAndPrivateAddresses() {
        String reason = "Link points to a local, private or numeric address";

        for (String link : List.of(
                "https://localhost/admin",
                "https://app.localhost/admin",
                "https://printer.local/status",
                "https://db.internal/data",
                "https://host.localdomain/x",
                "https://192.168.1.10/router",
                "https://127.0.0.1/admin",
                "https://intranet/page",
                "https://[::1]/admin")) {

            AppExceptions.ValidationException error = linkError(link);

            assertEquals(ResourceUploadValidator.UNSAFE_LINK_MESSAGE, error.getMessage(),
                    link + " should be refused as unsafe");
        }

        verify(auditTrail, times(9)).recordSecurityEvent(
                ADMIN, ResourceUploadValidator.UNSAFE_LINK_EVENT, AUDIT_ENTITY, reason);
    }

    // ----------------------------------------------------- rules and sizes

    @Test
    void rules_describesTheDefaultUploadRules() {
        UploadRulesResponse rules = validator.rules();

        assertEquals(10L * 1024 * 1024, rules.maxFileSizeBytes());
        assertEquals("10 MB", rules.maxFileSizeLabel());
        assertEquals(List.of("pdf", "docx", "png", "jpg", "jpeg"), rules.allowedExtensions());
        assertEquals("PDF, DOCX, PNG or JPEG", rules.acceptedFormats());
    }

    @Test
    void rules_ignoresFormatsThatTheValidatorCannotCheck() {
        properties.getContent().setAllowedFileExtensions(List.of("pdf", "exe", " PNG "));

        UploadRulesResponse rules = validator.rules();

        assertEquals(List.of("pdf", "png"), rules.allowedExtensions());
        assertEquals("PDF or PNG", rules.acceptedFormats());
    }

    @Test
    void formatSize_writesSizesInPlainWords() {
        assertEquals("1 KB", ResourceUploadValidator.formatSize(100));
        assertEquals("5 KB", ResourceUploadValidator.formatSize(5 * 1024));
        assertEquals("10 MB", ResourceUploadValidator.formatSize(10L * 1024 * 1024));
        assertEquals("1.5 MB", ResourceUploadValidator.formatSize((long) (1.5 * 1024 * 1024)));
    }

    @Test
    void sizeLimitMessage_namesTheLimit() {
        assertEquals("This file is too large. The maximum size is 10 MB.",
                ResourceUploadValidator.sizeLimitMessage(10L * 1024 * 1024));
    }
}