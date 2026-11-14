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

    @Test void storesAFileAndLoadsTheSameBytesBack() {
        byte[] bytes = {1, 2, 3, 4};

        storage.store("memory-wall/a.png", bytes, "image/png");

        assertArrayEquals(bytes, storage.load("memory-wall/a.png"));
        assertTrue(Files.exists(folder.resolve("memory-wall").resolve("a.png")));
    }

    @Test void createsTheSubfolderOnFirstUse() {
        assertFalse(Files.exists(folder.resolve("memory-wall")));

        storage.store("memory-wall/a.png", new byte[] {1}, "image/png");

        assertTrue(Files.isDirectory(folder.resolve("memory-wall")));
    }

    @Test void storingTheSameKeyAgainReplacesTheFile() {
        storage.store("a.png", new byte[] {1}, "image/png");
        storage.store("a.png", new byte[] {9, 9}, "image/png");

        assertArrayEquals(new byte[] {9, 9}, storage.load("a.png"));
    }

    @Test void keepsDifferentKeysApart() {
        storage.store("a.png", new byte[] {1}, "image/png");
        storage.store("b.png", new byte[] {2}, "image/png");

        assertArrayEquals(new byte[] {1}, storage.load("a.png"));
        assertArrayEquals(new byte[] {2}, storage.load("b.png"));
    }

    @Test void loadingAFileThatIsGoneSaysPhotoNotFound() {
        var thrown = assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> storage.load("missing.png"));

        assertEquals("Photo not found.", thrown.getMessage());
    }

    @Test void deleteRemovesTheFile() {
        storage.store("a.png", new byte[] {1}, "image/png");

        storage.delete("a.png");

        assertFalse(Files.exists(folder.resolve("a.png")));
        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> storage.load("a.png"));
    }

    @Test void deletingAFileThatIsAlreadyGoneIsNotAnError() {
        assertDoesNotThrow(() -> storage.delete("never-existed.png"));
    }

    @Test void deleteNeverThrowsEvenForABadKey() {
        assertDoesNotThrow(() -> storage.delete("../outside.png"));
    }

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

    @Test void refusesAKeyThatIsTheStorageFolderItself() {
        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> storage.load(""));
        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> storage.load("."));
    }

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

    @Test void saysTheSaveFailedWhenTheDiskCannotBeWritten() throws Exception {
        // A normal file where a folder is needed makes writing impossible.
        Files.write(folder.resolve("memory-wall"), new byte[] {1});

        var thrown = assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> storage.store("memory-wall/a.png", new byte[] {1}, "image/png"));

        assertEquals("We could not save the photo right now. Please try again.", thrown.getMessage());
        assertNotNull(thrown.getCause());
    }

    @Test void saysTheOpenFailedWhenTheKeyIsAFolder() throws Exception {
        Files.createDirectories(folder.resolve("folder-not-file"));

        var thrown = assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> storage.load("folder-not-file"));

        assertEquals("We could not open the photo right now. Please try again.", thrown.getMessage());
    }
}