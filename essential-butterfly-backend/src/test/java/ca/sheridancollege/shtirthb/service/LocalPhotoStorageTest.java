package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ca.sheridancollege.shtirthb.config.AppProperties;
import ca.sheridancollege.shtirthb.exception.AppExceptions;

class LocalPhotoStorageTest {

    @TempDir Path folder;

    private LocalPhotoStorage storage;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getMemoryWall().setStorageDirectory(folder.toString());
        storage = new LocalPhotoStorage(properties);
    }

    /*
     * Checks: A stored photo can be loaded back with the same bytes.
     * How:    Uses a temporary folder. We store 4 bytes under memory-wall/a.png, load them, compare,
     *   and check the file exists on disk.
     */
    @Test void storesAFileAndLoadsTheSameBytesBack() {
        byte[] bytes = {1, 2, 3, 4};

        storage.store("memory-wall/a.png", bytes, "image/png");

        assertArrayEquals(bytes, storage.load("memory-wall/a.png"));
        assertTrue(Files.exists(folder.resolve("memory-wall").resolve("a.png")));
    }

    /*
     * Checks: The subfolder is created the first time it is needed.
     * How:    The memory-wall folder does not exist before store() and is a directory after it.
     */
    @Test void createsTheSubfolderOnFirstUse() {
        assertFalse(Files.exists(folder.resolve("memory-wall")));

        storage.store("memory-wall/a.png", new byte[] {1}, "image/png");

        assertTrue(Files.isDirectory(folder.resolve("memory-wall")));
    }

    /*
     * Checks: Storing the same name again replaces the file.
     * How:    We store {1}, then {9,9} under a.png. load() returns {9,9}.
     */
    @Test void storingTheSameKeyAgainReplacesTheFile() {
        storage.store("a.png", new byte[] {1}, "image/png");
        storage.store("a.png", new byte[] {9, 9}, "image/png");

        assertArrayEquals(new byte[] {9, 9}, storage.load("a.png"));
    }

    /*
     * Checks: Different names keep their own files.
     * How:    a.png and b.png are stored with different bytes, and each loads its own bytes.
     */
    @Test void keepsDifferentKeysApart() {
        storage.store("a.png", new byte[] {1}, "image/png");
        storage.store("b.png", new byte[] {2}, "image/png");

        assertArrayEquals(new byte[] {1}, storage.load("a.png"));
        assertArrayEquals(new byte[] {2}, storage.load("b.png"));
    }

    /*
     * Checks: Loading a missing file gives "Photo not found."
     * How:    load("missing.png") throws ResourceNotFoundException with that message.
     */
    @Test void loadingAFileThatIsGoneSaysPhotoNotFound() {
        var thrown = assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> storage.load("missing.png"));

        assertEquals("Photo not found.", thrown.getMessage());
    }

    /*
     * Checks: Delete removes the file.
     * How:    After delete() the file is not on disk and load() throws ResourceNotFoundException.
     */
    @Test void deleteRemovesTheFile() {
        storage.store("a.png", new byte[] {1}, "image/png");

        storage.delete("a.png");

        assertFalse(Files.exists(folder.resolve("a.png")));
        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> storage.load("a.png"));
    }

    /*
     * Checks: Deleting a file that is already gone is not an error.
     * How:    delete() of a name that never existed does not throw.
     */
    @Test void deletingAFileThatIsAlreadyGoneIsNotAnError() {
        assertDoesNotThrow(() -> storage.delete("never-existed.png"));
    }

    /*
     * Checks: Delete never throws, even for a bad name.
     * How:    delete("../outside.png") does not throw.
     */
    @Test void deleteNeverThrowsEvenForABadKey() {
        assertDoesNotThrow(() -> storage.delete("../outside.png"));
    }

    /*
     * Checks: A name that points outside the storage folder is refused.
     * How:    A key like ../file (a real temp file outside the folder) makes both store() and load()
     *   throw ResourceNotFoundException. This blocks path traversal attacks.
     */
    @Test void refusesAKeyThatPointsOutsideTheStorageFolder() throws Exception {
        Path outside = Files.createTempFile("outside", ".png");
        try {
            String key = "../" + outside.getFileName();

            assertThrows(AppExceptions.ResourceNotFoundException.class,
                    () -> storage.store(key, new byte[] {1}, "image/png"));
            assertThrows(AppExceptions.ResourceNotFoundException.class, () -> storage.load(key));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    /*
     * Checks: A name that is the storage folder itself is refused.
     * How:    load("") and load(".") throw ResourceNotFoundException.
     */
    @Test void refusesAKeyThatIsTheStorageFolderItself() {
        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> storage.load(""));
        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> storage.load("."));
    }

    /*
     * Checks: A bad name never touches files outside the folder.
     * How:    We create a real file outside the folder and call delete() with a ../ key. The file must
     *   still exist.
     */
    @Test void aBadKeyNeverTouchesFilesOutsideTheFolder() throws Exception {
        Path outside = Files.createTempFile("keep", ".png");
        try {
            Files.write(outside, new byte[] {7});
            String key = "../" + outside.getFileName();

            storage.delete(key);

            assertTrue(Files.exists(outside));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    /*
     * Checks: A write failure gives a friendly error.
     * How:    We make memory-wall a file instead of a folder, so writing fails. store() throws
     *   ServiceUnavailableException with "We could not save the photo right now. Please try
     *   again." and keeps the original cause.
     */
    @Test void saysTheSaveFailedWhenTheDiskCannotBeWritten() throws Exception {
        // A normal file where a folder is needed makes writing impossible.
        Files.write(folder.resolve("memory-wall"), new byte[] {1});

        var thrown = assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> storage.store("memory-wall/a.png", new byte[] {1}, "image/png"));

        assertEquals("We could not save the photo right now. Please try again.", thrown.getMessage());
        assertNotNull(thrown.getCause());
    }

    /*
     * Checks: A read failure gives a friendly error.
     * How:    The key is a folder. load() throws ServiceUnavailableException with "We could not open
     *   the photo right now. Please try again."
     */
    @Test void saysTheOpenFailedWhenTheKeyIsAFolder() throws Exception {
        Files.createDirectories(folder.resolve("folder-not-file"));

        var thrown = assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> storage.load("folder-not-file"));

        assertEquals("We could not open the photo right now. Please try again.", thrown.getMessage());
    }
}