package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import ca.sheridancollege.shtirthb.config.AppProperties;
import ca.sheridancollege.shtirthb.exception.AppExceptions;

/*
 * Unit tests for MemoryWallImageValidator.
 *
 * Every photo the admin uploads to the Memory Wall is checked here before it is
 * stored. These are security tests:
 *   - Only JPEG, PNG and WEBP photos are accepted.
 *   - The file contents must match the file type (a text file named .png is refused).
 *   - Programs and web pages are refused, even hidden in a double extension.
 *   - Unsafe files are written to the audit trail.
 *   - Photos over the size limit are refused.
 *
 * No real photos are used. The files are small fake byte arrays.
 */
@ExtendWith(MockitoExtension.class)
class MemoryWallImageValidatorTest {

    private static final String ADMIN = "admin@mail.com";
    private static final String EVENT = "SECURITY_UNSAFE_FILE_REJECTED";
    private static final String ENTITY = "memory_wall_photos";

    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };
    private static final byte[] JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0
    };
    private static final byte[] WEBP = {
            'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
    };

    @Mock
    private AuditTrailService auditTrail;

    private AppProperties properties;
    private MemoryWallImageValidator validator;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        validator = new MemoryWallImageValidator(properties, auditTrail);
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private AppExceptions.ValidationException rejected(String name, byte[] content) {
        return assertThrows(AppExceptions.ValidationException.class,
                () -> validator.validate(file(name, content), ADMIN));
    }

    // ------------------------------------------------------ accepted photos

    /*
     * Checks: A PNG photo is accepted.
     * How:    Unit test, no Spring. validate() gets a file named trip.png with real PNG bytes. The
     *   result keeps the file name and has extension png.
     */
    @Test
    void acceptsAPngPhoto() {
        MemoryWallImageValidator.ValidatedImage image =
                validator.validate(file("trip.png", PNG), ADMIN);

        assertEquals("trip.png", image.fileName());
        assertEquals("png", image.extension());
        assertEquals("image/png", image.contentType());
        assertArrayEquals(PNG, image.content());
        verifyNoInteractions(auditTrail);
    }

    /*
     * Checks: A JPG photo is accepted.
     * How:    Real JPEG bytes. The extension is jpg and the content type is image/jpeg.
     */
    @Test
    void acceptsAJpgPhoto() {
        MemoryWallImageValidator.ValidatedImage image =
                validator.validate(file("trip.jpg", JPEG), ADMIN);

        assertEquals("jpg", image.extension());
        assertEquals("image/jpeg", image.contentType());
    }

    /*
     * Checks: A .jpeg photo is stored as jpg.
     * How:    trip.jpeg gives extension jpg and image/jpeg.
     */
    @Test
    void storesAJpegPhotoAsJpg() {
        MemoryWallImageValidator.ValidatedImage image =
                validator.validate(file("trip.jpeg", JPEG), ADMIN);

        assertEquals("jpg", image.extension());
        assertEquals("image/jpeg", image.contentType());
        assertEquals("trip.jpeg", image.fileName());
    }

    /*
     * Checks: A WEBP photo is accepted.
     * How:    Real WEBP bytes. The extension is webp and the content type is image/webp.
     */
    @Test
    void acceptsAWebpPhoto() {
        MemoryWallImageValidator.ValidatedImage image =
                validator.validate(file("trip.webp", WEBP), ADMIN);

        assertEquals("webp", image.extension());
        assertEquals("image/webp", image.contentType());
    }

    /*
     * Checks: Capital letters in the extension do not matter.
     * How:    TRIP.PNG is accepted, the extension is png and the file name is kept.
     */
    @Test
    void ignoresCapitalsInTheExtension() {
        MemoryWallImageValidator.ValidatedImage image =
                validator.validate(file("TRIP.PNG", PNG), ADMIN);

        assertEquals("png", image.extension());
        assertEquals("TRIP.PNG", image.fileName());
    }

    /*
     * Checks: A dot inside the name is fine.
     * How:    summer.2026.trip.png does not throw.
     */
    @Test
    void acceptsADotInsideTheName() {
        validator.validate(file("summer.2026.trip.png", PNG), ADMIN);
    }

    // ----------------------------------------------------- missing or empty

    /*
     * Checks: No file is refused.
     * How:    validate(null) throws ValidationException.
     */
    @Test
    void refusesNoFile() {
        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> validator.validate(null, ADMIN));

        assertEquals("Choose a photo to upload.", error.getMessage());
    }

    /*
     * Checks: An empty file is refused.
     * How:    The message is "Choose a photo to upload."
     */
    @Test
    void refusesAnEmptyFile() {
        assertEquals("Choose a photo to upload.",
                rejected("trip.png", new byte[0]).getMessage());
    }

    /*
     * Checks: A file with no name is refused.
     * How:    Parameterized: several blank or missing names each give the same refusal.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void refusesAFileWithoutAName(String name) {
        assertEquals(
                "The selected file has no name. Please rename it and try again.",
                rejected(name, PNG).getMessage());
    }

    // ---------------------------------------------------------- file format

    /*
     * Checks: Formats that are not allowed are refused.
     * How:    Parameterized: names such as photo.gif give the UNSUPPORTED_MESSAGE.
     */
    @ParameterizedTest
    @ValueSource(strings = {"photo.gif", "photo.bmp", "photo.pdf", "photo.txt", "photo", "photo.", ".png"})
    void refusesFormatsThatAreNotAllowed(String name) {
        assertEquals(MemoryWallImageValidator.UNSUPPORTED_MESSAGE,
                rejected(name, PNG).getMessage());
        verifyNoInteractions(auditTrail);
    }

    /*
     * Checks: A format that was switched off in the settings is refused.
     * How:    The configuration only allows some formats. A switched-off format gives
     *   UNSUPPORTED_MESSAGE.
     */
    @Test
    void refusesAFormatThatTheAdminSwitchedOff() {
        properties.getMemoryWall().setAllowedFileExtensions(List.of("jpg"));

        assertEquals(MemoryWallImageValidator.UNSUPPORTED_MESSAGE,
                rejected("trip.png", PNG).getMessage());
    }

    /*
     * Checks: Configured formats the validator cannot check are ignored.
     * How:    The configuration lists an extra format. PNG still works, and the unknown format is
     *   refused.
     */
    @Test
    void ignoresConfiguredFormatsItCannotCheck() {
        properties.getMemoryWall().setAllowedFileExtensions(List.of("gif", " PNG "));

        validator.validate(file("trip.png", PNG), ADMIN);

        assertEquals(MemoryWallImageValidator.UNSUPPORTED_MESSAGE,
                rejected("trip.gif", PNG).getMessage());
    }

    // -------------------------------------------------- contents vs the name

    /*
     * Checks: A text file named .png is refused.
     * How:    The bytes are not a PNG, so the message is UNSUPPORTED_MESSAGE.
     */
    @Test
    void refusesATextFileNamedPng() {
        AppExceptions.ValidationException error = rejected(
                "fake.png", "not a picture".getBytes(StandardCharsets.UTF_8));

        assertEquals(MemoryWallImageValidator.UNSUPPORTED_MESSAGE, error.getMessage());
        verify(auditTrail).recordSecurityEvent(
                ADMIN, EVENT, ENTITY, "File contents do not match the .png type");
    }

    /*
     * Checks: A PNG named .jpg is refused.
     * How:    The bytes do not match the extension.
     */
    @Test
    void refusesAPngNamedJpg() {
        rejected("photo.jpg", PNG);

        verify(auditTrail).recordSecurityEvent(
                ADMIN, EVENT, ENTITY, "File contents do not match the .jpg type");
    }

    /*
     * Checks: A JPEG named .webp is refused.
     * How:    The bytes do not match the extension.
     */
    @Test
    void refusesAJpegNamedWebp() {
        rejected("photo.webp", JPEG);

        verify(auditTrail).recordSecurityEvent(
                ADMIN, EVENT, ENTITY, "File contents do not match the .webp type");
    }

    /*
     * Checks: A RIFF file that is not WEBP is refused.
     * How:    The file starts like a WEBP container but is not WEBP.
     */
    @Test
    void refusesARiffFileThatIsNotWebp() {
        byte[] wave = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'A', 'V', 'E', 0, 0};

        rejected("photo.webp", wave);

        verify(auditTrail).recordSecurityEvent(
                ADMIN, EVENT, ENTITY, "File contents do not match the .webp type");
    }

    /*
     * Checks: A file too short to check is refused.
     * How:    Only a few bytes, so the type cannot be confirmed.
     */
    @Test
    void refusesAFileTooShortToCheck() {
        rejected("photo.png", new byte[] {(byte) 0x89, 'P'});
        rejected("photo.webp", new byte[] {'R', 'I', 'F', 'F'});
    }

    // ------------------------------------------------------- unsafe names

    /*
     * Checks: Programs and web pages are refused and logged.
     * How:    Parameterized: names such as setup.exe or page.html give UNSUPPORTED_MESSAGE, and a
     *   security event is recorded in the (mock) audit trail.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "setup.exe", "run.bat", "script.js", "page.html", "drawing.svg",
            "tool.jar", "macro.ps1", "shell.sh", "app.php", "installer.msi"
    })
    void refusesProgramsAndWebPagesAndLogsThem(String name) {
        assertEquals(MemoryWallImageValidator.UNSUPPORTED_MESSAGE,
                rejected(name, PNG).getMessage());

        verify(auditTrail).recordSecurityEvent(
                ADMIN, EVENT, ENTITY, "Executable or web-page extension in file name");
    }

    /*
     * Checks: A dangerous extension hidden in the name is refused.
     * How:    Parameterized: names like photo.html.png are refused.
     */
    @ParameterizedTest
    @ValueSource(strings = {"photo.exe.png", "photo.png.exe", "photo.php.jpg", "photo.html.webp"})
    void refusesADangerousExtensionHiddenInTheName(String name) {
        rejected(name, PNG);

        verify(auditTrail).recordSecurityEvent(
                ADMIN, EVENT, ENTITY, "Executable or web-page extension in file name");
    }

    /*
     * Checks: Dangerous extensions in capital letters are refused.
     * How:    For example SETUP.EXE is refused.
     */
    @Test
    void refusesDangerousExtensionsInCapitals() {
        rejected("PHOTO.EXE.PNG", PNG);

        verify(auditTrail).recordSecurityEvent(
                ADMIN, EVENT, ENTITY, "Executable or web-page extension in file name");
    }

    /*
     * Checks: Path tricks and control characters in the name are refused.
     * How:    Parameterized: names with ../, slashes or control characters give UNSUPPORTED_MESSAGE.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "../photo.png", "folder/photo.png", "folder\\photo.png",
            "photo..png", "pho\u0000to.png", "pho\nto.png"
    })
    void refusesPathTricksAndControlCharactersInTheName(String name) {
        assertEquals(MemoryWallImageValidator.UNSUPPORTED_MESSAGE,
                rejected(name, PNG).getMessage());

        verify(auditTrail).recordSecurityEvent(
                ADMIN, EVENT, ENTITY, "Unsafe file name");
    }

    /*
     * Checks: A name longer than 255 characters is refused.
     * How:    A 256-character name is refused.
     */
    @Test
    void refusesANameLongerThan255Characters() {
        rejected("a".repeat(252) + ".png", PNG);

        verify(auditTrail).recordSecurityEvent(
                ADMIN, EVENT, ENTITY, "Unsafe file name");
    }

    /*
     * Checks: A name of exactly 255 characters is accepted.
     * How:    251 letters plus .png does not throw.
     */
    @Test
    void acceptsANameOfExactly255Characters() {
        validator.validate(file("a".repeat(251) + ".png", PNG), ADMIN);
    }

    // ----------------------------------------------------------------- size

    /*
     * Checks: A photo over the size limit is refused.
     * How:    A file bigger than the limit throws PayloadTooLargeException.
     */
    @Test
    void refusesAPhotoOverTheSizeLimit() {
        properties.getMemoryWall().setMaxFileSizeBytes(20);
        byte[] big = new byte[21];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        AppExceptions.PayloadTooLargeException error = assertThrows(
                AppExceptions.PayloadTooLargeException.class,
                () -> validator.validate(file("big.png", big), ADMIN));

        assertEquals(ResourceUploadValidator.sizeLimitMessage(20), error.getMessage());
        verifyNoInteractions(auditTrail);
    }

    /*
     * Checks: A photo exactly at the size limit is accepted.
     * How:    The limit is set to the size of the test file. It does not throw.
     */
    @Test
    void acceptsAPhotoExactlyAtTheSizeLimit() {
        properties.getMemoryWall().setMaxFileSizeBytes(PNG.length);

        validator.validate(file("ok.png", PNG), ADMIN);
    }

    /*
     * Checks: The size is checked before the contents.
     * How:    A big file with bad contents throws PayloadTooLargeException, not the content error.
     */
    @Test
    void checksTheSizeBeforeTheContents() {
        properties.getMemoryWall().setMaxFileSizeBytes(5);

        assertThrows(AppExceptions.PayloadTooLargeException.class,
                () -> validator.validate(
                        file("fake.png", "not a picture at all".getBytes(StandardCharsets.UTF_8)),
                        ADMIN));

        verifyNoInteractions(auditTrail);
    }

    // ------------------------------------------------------ unreadable file

    /*
     * Checks: A file that cannot be read gives a service error.
     * How:    A fake file whose getBytes() throws IOException gives ServiceUnavailableException.
     */
    @Test
    void reportsAFileThatCannotBeRead() {
        MockMultipartFile unreadable = new MockMultipartFile("file", "trip.png", "image/png", PNG) {
            @Override
            public byte[] getBytes() throws java.io.IOException {
                throw new java.io.IOException("disk error");
            }
        };

        AppExceptions.ServiceUnavailableException error = assertThrows(
                AppExceptions.ServiceUnavailableException.class,
                () -> validator.validate(unreadable, ADMIN));

        assertEquals("We could not read the uploaded file. Please try again.", error.getMessage());
    }

    // --------------------------------------------------------- format lists

    /*
     * Checks: The accepted formats are listed in plain words.
     * How:    acceptedFormats() is "JPEG, PNG or WEBP".
     */
    @Test
    void listsTheAcceptedFormatsInPlainWords() {
        assertEquals("JPEG, PNG or WEBP", validator.acceptedFormats());
    }

    /*
     * Checks: A single format is listed alone.
     * How:    The result is "PNG".
     */
    @Test
    void listsASingleFormatOnItsOwn() {
        properties.getMemoryWall().setAllowedFileExtensions(List.of("png"));

        assertEquals("PNG", validator.acceptedFormats());
    }

    /*
     * Checks: Two formats are joined with "or".
     * How:    The result is "PNG or WEBP".
     */
    @Test
    void listsTwoFormatsWithOr() {
        properties.getMemoryWall().setAllowedFileExtensions(List.of("png", "webp"));

        assertEquals("PNG or WEBP", validator.acceptedFormats());
    }

    /*
     * Checks: JPEG and JPG are not both listed.
     * How:    The result is only "JPEG".
     */
    @Test
    void doesNotRepeatJpegAndJpg() {
        properties.getMemoryWall().setAllowedFileExtensions(List.of("jpg", "jpeg"));

        assertEquals("JPEG", validator.acceptedFormats());
    }

    /*
     * Checks: The allowed extensions are listed.
     * How:    The list is jpg, jpeg, png, webp.
     */
    @Test
    void listsTheAllowedExtensions() {
        assertEquals(List.of("jpg", "jpeg", "png", "webp"), validator.allowedExtensionList());
    }

    /*
     * Checks: The extension list follows the configuration.
     * How:    With only png allowed, the list is [png].
     */
    @Test
    void theExtensionListFollowsTheConfiguration() {
        properties.getMemoryWall().setAllowedFileExtensions(List.of(" PNG ", "gif"));

        assertEquals(List.of("png"), validator.allowedExtensionList());
    }

    /*
     * Checks: A safe photo never writes to the audit trail.
     * How:    After a valid photo, the (mock) audit trail is never used.
     */
    @Test
    void neverWritesToTheAuditTrailForASafePhoto() {
        validator.validate(file("trip.png", PNG), ADMIN);

        verifyNoInteractions(auditTrail);
        org.mockito.Mockito.verify(auditTrail, org.mockito.Mockito.never())
                .recordSecurityEvent(anyString(), anyString(), anyString(), anyString());
    }
}