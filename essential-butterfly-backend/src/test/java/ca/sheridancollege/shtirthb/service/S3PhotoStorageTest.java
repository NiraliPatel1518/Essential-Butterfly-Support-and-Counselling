package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ca.sheridancollege.shtirthb.exception.AppExceptions;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3PhotoStorageTest {

    private S3Client s3;
    private S3PhotoStorage storage;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        storage = new S3PhotoStorage(s3, " test-bucket ");
    }

    /*
     * Checks: A photo is uploaded to the configured bucket under the same key the database keeps.
     * How:    Uses a mocked S3 client. We store under memory-wall/a.png and capture the request.
     */
    @Test void storesUnderTheSameKeyInTheBucket() {
        storage.store("memory-wall/a.png", new byte[] {1, 2, 3}, "image/png");

        ArgumentCaptor<PutObjectRequest> request =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertEquals("test-bucket", request.getValue().bucket());
        assertEquals("memory-wall/a.png", request.getValue().key());
        assertEquals("image/png", request.getValue().contentType());
    }

    /*
     * Checks: A failed upload gives the friendly "could not save" error, not an AWS error.
     * How:    The mocked client throws an SdkClientException on putObject.
     */
    @Test void failedUploadBecomesServiceUnavailable() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("network down"));

        assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> storage.store("memory-wall/a.png", new byte[] {1}, "image/png"));
    }

    /*
     * Checks: A stored photo is loaded back with the same bytes.
     * How:    The mocked client returns 3 bytes for getObjectAsBytes.
     */
    @Test void loadsTheBytesBack() {
        byte[] bytes = {4, 5, 6};
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(), bytes));

        assertArrayEquals(bytes, storage.load("memory-wall/a.png"));
    }

    /*
     * Checks: A missing object is reported as "Photo not found", like the folder version.
     * How:    The mocked client throws NoSuchKeyException.
     */
    @Test void missingPhotoIsNotFound() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("gone").build());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> storage.load("memory-wall/missing.png"));
    }

    /*
     * Checks: Any other S3 problem while loading gives the friendly "could not open" error.
     * How:    The mocked client throws an SdkClientException.
     */
    @Test void failedLoadBecomesServiceUnavailable() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.create("network down"));

        assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> storage.load("memory-wall/a.png"));
    }

    /*
     * Checks: A failed delete is only logged; the database record stays the truth.
     * How:    The mocked client throws on deleteObject; no exception must escape.
     */
    @Test void failedDeleteIsNotAnError() {
        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("network down"));

        assertDoesNotThrow(() -> storage.delete("memory-wall/a.png"));
    }

    /*
     * Checks: Starting in S3 mode without a bucket name fails at start-up with a clear message.
     * How:    Creates the storage with a blank bucket.
     */
    @Test void missingBucketFailsAtStartUp() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new S3PhotoStorage(s3, "  "));
        assertTrue(error.getMessage().contains("MEMORY_WALL_S3_BUCKET"));
    }
}
