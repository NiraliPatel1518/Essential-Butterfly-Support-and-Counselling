package ca.sheridancollege.shtirthb.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import ca.sheridancollege.shtirthb.config.AppProperties;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/*
 * Keeps the Memory Wall pictures in a private AWS S3 bucket. Used only while
 * app.memory-wall.storage is "s3" (MEMORY_WALL_STORAGE=s3); otherwise
 * LocalPhotoStorage is used and this class is never created
 * (see docs/operations/memory-wall-s3.md).
 *
 * The database keeps only the key (for example "memory-wall/3f2c....jpg"),
 * and the same key is used as the S3 object name, so switching between the
 * folder and S3 needs no database change.
 *
 * Credentials are found by the AWS SDK itself: the EC2 instance profile
 * (LabInstanceProfile in AWS Academy), the ECS task role, or the
 * AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / AWS_SESSION_TOKEN environment
 * variables. No key is ever stored in the code.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.memory-wall",
        name = "storage",
        havingValue = "s3")
public class S3PhotoStorage implements PhotoStorage {

    private static final Logger logger =
            LoggerFactory.getLogger(S3PhotoStorage.class);

    private final S3Client s3;
    private final String bucket;

    // Bucket and region come from app.memory-wall.s3-bucket / s3-region
    // (MEMORY_WALL_S3_BUCKET / MEMORY_WALL_S3_REGION).
    @Autowired
    public S3PhotoStorage(AppProperties properties) {

        this(
                S3Client.builder()
                        .region(Region.of(properties.getMemoryWall().getS3Region().trim()))
                        .httpClient(UrlConnectionHttpClient.create())
                        .build(),
                properties.getMemoryWall().getS3Bucket());
    }

    // Used by the tests with a mocked client.
    S3PhotoStorage(S3Client s3, String bucket) {

        if (bucket == null || bucket.isBlank()) {
            // Fail at start-up, not on the first upload.
            throw new IllegalStateException(
                    "MEMORY_WALL_STORAGE is s3 but MEMORY_WALL_S3_BUCKET is not set.");
        }

        this.s3 = s3;
        this.bucket = bucket.trim();
        logger.info("Memory Wall photos are stored in S3 bucket {}", this.bucket);
    }

    @Override
    public void store(String key, byte[] content, String contentType) {

        /*
         * The SDK reports problems with its own unchecked exception. It is
         * turned into the project's ServiceUnavailableException, which the
         * global handler answers with a friendly message.
         */
        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException exception) {
            throw new AppExceptions.ServiceUnavailableException(
                    "We could not save the photo right now. Please try again.",
                    exception);
        }
    }

    @Override
    public byte[] load(String key) {

        try {
            ResponseBytes<GetObjectResponse> object = s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());

            return object.asByteArray();
        } catch (NoSuchKeyException exception) {
            throw new AppExceptions.ResourceNotFoundException("Photo");
        } catch (SdkException exception) {
            throw new AppExceptions.ServiceUnavailableException(
                    "We could not open the photo right now. Please try again.",
                    exception);
        }
    }

    @Override
    public void delete(String key) {

        try {
            s3.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException exception) {
            logger.warn("Could not remove stored photo {}", key, exception);
        }
    }
}
