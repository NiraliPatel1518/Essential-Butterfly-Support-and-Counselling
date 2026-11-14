package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ca.sheridancollege.shtirthb.config.AppProperties;
import ca.sheridancollege.shtirthb.dto.MemoryWallAdminOverview;
import ca.sheridancollege.shtirthb.dto.MemoryWallDescriptionRequest;
import ca.sheridancollege.shtirthb.dto.MemoryWallImage;
import ca.sheridancollege.shtirthb.dto.MemoryWallMonth;
import ca.sheridancollege.shtirthb.dto.MemoryWallPhotoAdminResponse;
import ca.sheridancollege.shtirthb.dto.MemoryWallSelectionRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.model.CustomerTestimonial;
import ca.sheridancollege.shtirthb.model.FeatureVisibility;
import ca.sheridancollege.shtirthb.model.MemoryWallPhoto;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.FeatureVisibilityRepository;
import ca.sheridancollege.shtirthb.repository.MemoryWallPhotoRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemoryWallServiceTest {

    @Mock MemoryWallPhotoRepository photos;
    @Mock PhotoStorage storage;
    @Mock MemoryWallImageValidator validator;
    @Mock AuditTrailService auditTrail;
    @Mock AdminUserRepository adminUsers;
    @Mock AppointmentClock clock;
    @Mock FeatureVisibilityRepository features;

    private AppProperties properties;
    private MemoryWallService service;

    private static final String ADMIN = "casey@example.com";
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-11-14T09:00:00");

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.getMemoryWall().setMaxPhotos(3);
        properties.getMemoryWall().setMaxPublishedPhotos(2);

        when(clock.now()).thenReturn(NOW);
        when(features.findByFeatureKey("MEMORY_WALL"))
                .thenReturn(Optional.of(new FeatureVisibility("MEMORY_WALL", "Memory Wall", true, 1)));
        when(validator.allowedExtensionList()).thenReturn(List.of("png", "jpg"));
        when(validator.acceptedFormats()).thenReturn("PNG or JPEG");

        service = new MemoryWallService(
                photos, storage, validator, auditTrail, adminUsers, clock, properties, features);

        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static MemoryWallPhoto photo(long id, LocalDateTime uploaded, String description) {
        MemoryWallPhoto photo = new MemoryWallPhoto(
                "memory-wall/p" + id + ".png", "photo" + id + ".png", "image/png", 100,
                description, 5L, uploaded);
        ReflectionTestUtils.setField(photo, "id", id);
        return photo;
    }

    private static MemoryWallPhoto publishedPhoto(long id, LocalDateTime uploaded) {
        MemoryWallPhoto photo = photo(id, uploaded, null);
        photo.publish(uploaded);
        return photo;
    }

    private static MemoryWallPhoto testimonialPhoto(long id, String testimonialStatus, boolean consent, String name) {
        CustomerTestimonial testimonial = mock(CustomerTestimonial.class);
        when(testimonial.getStatus()).thenReturn(testimonialStatus);
        when(testimonial.isPublicationConsent()).thenReturn(consent);
        when(testimonial.getDisplayName()).thenReturn(name);
        when(testimonial.getPhotos()).thenReturn(List.of(
                "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[] {5, 6, 7})));

        MemoryWallPhoto photo = mock(MemoryWallPhoto.class);
        when(photo.getId()).thenReturn(id);
        when(photo.isFromTestimonial()).thenReturn(true);
        when(photo.getTestimonial()).thenReturn(testimonial);
        when(photo.getPhotoOrder()).thenReturn(0);
        when(photo.getContentType()).thenReturn("image/png");
        when(photo.getStatus()).thenReturn(MemoryWallPhoto.PENDING_REVIEW);
        when(photo.getUploadedAt()).thenReturn(NOW);
        when(photo.getUpdatedAt()).thenReturn(NOW);
        when(photo.getSizeBytes()).thenReturn(3);
        return photo;
    }

    private static void finishTransaction(int status) {
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                sync.afterCommit();
            }
            sync.afterCompletion(status);
        }
    }

    // ------------------------------------------------------- the public wall

    @Test void thePublicWallGroupsPhotosByUploadMonth() {
        when(photos.findAllPublic()).thenReturn(List.of(
                publishedPhoto(1, LocalDateTime.parse("2026-11-10T10:00:00")),
                publishedPhoto(2, LocalDateTime.parse("2026-11-02T10:00:00")),
                publishedPhoto(3, LocalDateTime.parse("2026-10-20T10:00:00"))));

        List<MemoryWallMonth> months = service.getPublicWall();

        assertEquals(2, months.size());
        assertEquals("2026-11", months.get(0).key());
        assertEquals("November 2026", months.get(0).label());
        assertEquals(2, months.get(0).photos().size());
        assertEquals("2026-10", months.get(1).key());
        assertEquals("October 2026", months.get(1).label());
        assertEquals(1, months.get(1).photos().size());
    }

    @Test void thePublicWallIsEmptyWhenNothingIsPublished() {
        when(photos.findAllPublic()).thenReturn(List.of());

        assertTrue(service.getPublicWall().isEmpty());
    }

    @Test void aPublicPhotoUsesItsDescriptionAsTheAlternativeText() {
        MemoryWallPhoto photo = photo(1, LocalDateTime.parse("2026-11-10T10:00:00"), "Casey at the lake");
        photo.publish(NOW);
        when(photos.findAllPublic()).thenReturn(List.of(photo));

        var shown = service.getPublicWall().get(0).photos().get(0);

        assertEquals("Casey at the lake", shown.altText());
        assertEquals("November 10, 2026", shown.uploadedOn());
    }

    @Test void aPhotoWithoutADescriptionGetsAPlainFallbackAlternativeText() {
        when(photos.findAllPublic()).thenReturn(List.of(publishedPhoto(1, LocalDateTime.parse("2026-11-10T10:00:00"))));

        assertEquals("Memory Wall photo from November 10, 2026",
                service.getPublicWall().get(0).photos().get(0).altText());
    }

    @Test void theWallIsNotFoundWhileTheMemoryWallSwitchIsOff() {
        when(features.findByFeatureKey("MEMORY_WALL"))
                .thenReturn(Optional.of(new FeatureVisibility("MEMORY_WALL", "Memory Wall", false, 1)));

        var thrown = assertThrows(AppExceptions.ResourceNotFoundException.class, () -> service.getPublicWall());

        assertEquals("Page not found.", thrown.getMessage());
        verifyNoInteractions(photos);
    }

    @Test void theWallIsNotFoundWhenThereIsNoSwitchRecordAtAll() {
        when(features.findByFeatureKey("MEMORY_WALL")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> service.getPublicWall());
    }

    // ------------------------------------------------------------- images

    @Test void aPublishedPhotosImageComesFromStorage() {
        MemoryWallPhoto photo = publishedPhoto(1, NOW);
        when(photos.findPublicById(1L)).thenReturn(Optional.of(photo));
        when(storage.load("memory-wall/p1.png")).thenReturn(new byte[] {1, 2});

        MemoryWallImage image = service.getPublicImage(1L);

        assertEquals("image/png", image.contentType());
        assertArrayEquals(new byte[] {1, 2}, image.content());
    }

    @Test void anUnpublishedPhotosImageIsNotFoundForVisitors() {
        when(photos.findPublicById(2L)).thenReturn(Optional.empty());

        var thrown = assertThrows(AppExceptions.ResourceNotFoundException.class, () -> service.getPublicImage(2L));

        assertEquals("Photo not found.", thrown.getMessage());
    }

    @Test void thePublicImageIsNotFoundWhileTheSwitchIsOff() {
        when(features.findByFeatureKey("MEMORY_WALL")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> service.getPublicImage(1L));
        verifyNoInteractions(photos, storage);
    }

    @Test void theAdminCanSeeAnyPhotosImage() {
        when(photos.findWithTestimonial(1L)).thenReturn(Optional.of(photo(1, NOW, null)));
        when(storage.load("memory-wall/p1.png")).thenReturn(new byte[] {3});

        assertArrayEquals(new byte[] {3}, service.getAdminImage(1L).content());
    }

    @Test void theAdminImageIsNotFoundForAnUnknownPhoto() {
        when(photos.findWithTestimonial(9L)).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> service.getAdminImage(9L));
    }

    @Test void aTestimonialPhotoIsReadFromItsTestimonial() {
        MemoryWallPhoto photo = testimonialPhoto(4, "APPROVED", true, "Alex");
        when(photos.findWithTestimonial(4L)).thenReturn(Optional.of(photo));

        MemoryWallImage image = service.getAdminImage(4L);

        assertEquals("image/png", image.contentType());
        assertArrayEquals(new byte[] {5, 6, 7}, image.content());
        verifyNoInteractions(storage);
    }

    @Test void aTestimonialPhotoWithABadPositionIsNotFound() {
        MemoryWallPhoto photo = testimonialPhoto(4, "APPROVED", true, "Alex");
        when(photo.getPhotoOrder()).thenReturn(5);
        when(photos.findWithTestimonial(4L)).thenReturn(Optional.of(photo));

        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> service.getAdminImage(4L));
    }

    // ----------------------------------------------------------- overview

    @Test void theOverviewListsPhotosAndSummarisesTheLimits() {
        MemoryWallPhoto pending = photo(1, NOW, null);
        MemoryWallPhoto published = publishedPhoto(2, NOW);
        when(photos.findAllForAdmin()).thenReturn(List.of(pending, published));

        MemoryWallAdminOverview overview = service.getOverview();

        assertEquals(2, overview.photos().size());
        var summary = overview.summary();
        assertEquals(2, summary.totalPhotos());
        assertEquals(3, summary.maxPhotos());
        assertEquals(1, summary.remainingUploads());
        assertEquals(1, summary.publishedPhotos());
        assertEquals(2, summary.maxPublishedPhotos());
        assertEquals(200, summary.storageUsedBytes());
        assertEquals(List.of("png", "jpg"), summary.allowedExtensions());
        assertEquals("PNG or JPEG", summary.acceptedFormats());
    }

    @Test void remainingUploadsNeverGoBelowZero() {
        when(photos.findAllForAdmin()).thenReturn(List.of(
                photo(1, NOW, null), photo(2, NOW, null), photo(3, NOW, null), photo(4, NOW, null)));

        assertEquals(0, service.getOverview().summary().remainingUploads());
    }

    @Test void anAdministratorPhotoIsLabelledAsUploadedByTheAdministrator() {
        when(photos.findAllForAdmin()).thenReturn(List.of(photo(1, NOW, null)));

        MemoryWallPhotoAdminResponse item = service.getOverview().photos().get(0);

        assertEquals("Administrator", item.uploadedBy());
        assertEquals("photo1.png", item.fileName());
        assertTrue(item.canPublish());
        assertNull(item.publishBlockedReason());
        assertNull(item.publishedAt());
    }

    @Test void aTestimonialPhotoThatIsApprovedMayBePublished() {
        MemoryWallPhoto wallPhoto = testimonialPhoto(4, "APPROVED", true, "Alex");
        when(photos.findAllForAdmin()).thenReturn(List.of(wallPhoto));

        MemoryWallPhotoAdminResponse item = service.getOverview().photos().get(0);

        assertEquals("Customer: Alex", item.uploadedBy());
        assertTrue(item.canPublish());
        assertEquals("Photo shared by Alex", item.altText());
        assertEquals("APPROVED", item.testimonialStatus());
    }

    @Test void aTestimonialPhotoThatIsNotApprovedIsBlocked() {
        MemoryWallPhoto wallPhoto = testimonialPhoto(4, "PENDING", true, "Alex");
        when(photos.findAllForAdmin()).thenReturn(List.of(wallPhoto));

        MemoryWallPhotoAdminResponse item = service.getOverview().photos().get(0);

        assertFalse(item.canPublish());
        assertEquals("This photo came with a testimonial that is not approved yet. Approve the testimonial first.",
                item.publishBlockedReason());
    }

    @Test void aTestimonialPhotoWithoutPublicationConsentIsBlocked() {
        MemoryWallPhoto wallPhoto = testimonialPhoto(4, "APPROVED", false, "Alex");
        when(photos.findAllForAdmin()).thenReturn(List.of(wallPhoto));

        assertFalse(service.getOverview().photos().get(0).canPublish());
    }

    // ------------------------------------------------------------- upload

    private MemoryWallImageValidator.ValidatedImage validImage() {
        return new MemoryWallImageValidator.ValidatedImage("new.png", "png", "image/png", new byte[] {1, 2, 3});
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "new.png", "image/png", new byte[] {1, 2, 3});
    }

    private void adminKnown() {
        AdminUser admin = mock(AdminUser.class);
        when(admin.getId()).thenReturn(5L);
        when(adminUsers.findByEmail(ADMIN)).thenReturn(Optional.of(admin));
    }

    @Test void uploadStoresTheFileAndSavesAPendingPhoto() {
        MockMultipartFile file = file();
        when(validator.validate(file, ADMIN)).thenReturn(validImage());
        when(photos.count()).thenReturn(0L);
        when(photos.saveAndFlush(any(MemoryWallPhoto.class))).thenAnswer(call -> call.getArgument(0));
        adminKnown();

        MemoryWallPhotoAdminResponse response = service.upload(file, "  A happy day  ", ADMIN);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).store(key.capture(), eq(new byte[] {1, 2, 3}), eq("image/png"));
        assertTrue(key.getValue().startsWith("memory-wall/"));
        assertTrue(key.getValue().endsWith(".png"));

        ArgumentCaptor<MemoryWallPhoto> saved = ArgumentCaptor.forClass(MemoryWallPhoto.class);
        verify(photos).saveAndFlush(saved.capture());
        assertEquals(MemoryWallPhoto.PENDING_REVIEW, saved.getValue().getStatus());
        assertEquals("A happy day", saved.getValue().getDescription());
        assertEquals(5L, saved.getValue().getUploadedByAdminId());
        assertEquals(key.getValue(), saved.getValue().getStorageKey());
        assertEquals(NOW, saved.getValue().getUploadedAt());
        assertEquals(MemoryWallPhoto.PENDING_REVIEW, response.status());
        verify(auditTrail).actAs(ADMIN);
        verify(photos).lockForChanges(anyLong());
    }

    @Test void uploadRefusesWhenTheWallIsFull() {
        MockMultipartFile file = file();
        when(validator.validate(file, ADMIN)).thenReturn(validImage());
        when(photos.count()).thenReturn(3L);

        var thrown = assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.upload(file, null, ADMIN));

        assertEquals("The Memory Wall already holds the maximum of 3 photos. "
                + "Delete some photos on this page before adding more.", thrown.getMessage());
        verifyNoInteractions(storage);
        verify(photos, never()).saveAndFlush(any());
    }

    @Test void uploadRefusesATooLongDescription() {
        MockMultipartFile file = file();
        when(validator.validate(file, ADMIN)).thenReturn(validImage());

        var thrown = assertThrows(AppExceptions.ValidationException.class,
                () -> service.upload(file, "x".repeat(201), ADMIN));

        assertEquals("Please keep the description under 200 characters.", thrown.getMessage());
        verifyNoInteractions(storage);
    }

    @Test void uploadTreatsABlankDescriptionAsNone() {
        MockMultipartFile file = file();
        when(validator.validate(file, ADMIN)).thenReturn(validImage());
        when(photos.count()).thenReturn(0L);
        when(photos.saveAndFlush(any(MemoryWallPhoto.class))).thenAnswer(call -> call.getArgument(0));

        service.upload(file, "   ", ADMIN);

        ArgumentCaptor<MemoryWallPhoto> saved = ArgumentCaptor.forClass(MemoryWallPhoto.class);
        verify(photos).saveAndFlush(saved.capture());
        assertNull(saved.getValue().getDescription());
    }

    @Test void uploadStopsWhenTheFileIsNotAcceptable() {
        MockMultipartFile file = file();
        when(validator.validate(file, ADMIN))
                .thenThrow(new AppExceptions.ValidationException("This file type is not supported."));

        assertThrows(AppExceptions.ValidationException.class, () -> service.upload(file, null, ADMIN));
        verifyNoInteractions(storage);
        verify(photos, never()).saveAndFlush(any());
    }

    @Test void aStoredFileIsRemovedAgainWhenTheTransactionRollsBack() {
        MockMultipartFile file = file();
        when(validator.validate(file, ADMIN)).thenReturn(validImage());
        when(photos.count()).thenReturn(0L);
        when(photos.saveAndFlush(any(MemoryWallPhoto.class))).thenAnswer(call -> call.getArgument(0));

        service.upload(file, null, ADMIN);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).store(key.capture(), any(), any());

        finishTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).delete(key.getValue());
    }

    @Test void aStoredFileIsKeptWhenTheTransactionCommits() {
        MockMultipartFile file = file();
        when(validator.validate(file, ADMIN)).thenReturn(validImage());
        when(photos.count()).thenReturn(0L);
        when(photos.saveAndFlush(any(MemoryWallPhoto.class))).thenAnswer(call -> call.getArgument(0));

        service.upload(file, null, ADMIN);

        finishTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(storage, never()).delete(anyString());
    }

    // -------------------------------------------------- change description

    @Test void changingTheDescriptionSavesTheTrimmedText() {
        MemoryWallPhoto photo = photo(1, NOW, "Old");
        when(photos.findWithTestimonial(1L)).thenReturn(Optional.of(photo));

        MemoryWallPhotoAdminResponse response =
                service.changeDescription(1L, new MemoryWallDescriptionRequest("  New words  "), ADMIN);

        assertEquals("New words", photo.getDescription());
        assertEquals("New words", response.description());
        verify(auditTrail).actAs(ADMIN);
        verify(photos).saveAndFlush(photo);
    }

    @Test void changingTheDescriptionToNothingRemovesIt() {
        MemoryWallPhoto photo = photo(1, NOW, "Old");
        when(photos.findWithTestimonial(1L)).thenReturn(Optional.of(photo));

        service.changeDescription(1L, new MemoryWallDescriptionRequest(""), ADMIN);
        assertNull(photo.getDescription());

        photo.describe("Again", NOW);
        service.changeDescription(1L, null, ADMIN);
        assertNull(photo.getDescription());
    }

    @Test void aTooLongDescriptionIsRefused() {
        when(photos.findWithTestimonial(1L)).thenReturn(Optional.of(photo(1, NOW, null)));

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.changeDescription(1L, new MemoryWallDescriptionRequest("x".repeat(201)), ADMIN));
    }

    @Test void changingTheDescriptionOfAnUnknownPhotoIsNotFound() {
        when(photos.findWithTestimonial(9L)).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.changeDescription(9L, new MemoryWallDescriptionRequest("x"), ADMIN));
    }

    // ------------------------------------------------------- save selection

    @Test void theSelectedPhotosArePublishedAndTheOthersAreTakenOff() {
        MemoryWallPhoto pending = photo(1, NOW, null);
        MemoryWallPhoto alreadyPublished = publishedPhoto(2, NOW);
        MemoryWallPhoto toHide = publishedPhoto(3, NOW);
        when(photos.findAllForAdmin()).thenReturn(List.of(pending, alreadyPublished, toHide));

        MemoryWallAdminOverview overview =
                service.saveSelection(new MemoryWallSelectionRequest(List.of(1L, 2L)), ADMIN);

        assertTrue(pending.isPublished());
        assertTrue(alreadyPublished.isPublished());
        assertFalse(toHide.isPublished());
        assertEquals(MemoryWallPhoto.UNPUBLISHED, toHide.getStatus());
        verify(photos).saveAllAndFlush(anyList());
        verify(auditTrail).actAs(ADMIN);
        assertEquals(3, overview.photos().size());
    }

    @Test void aPhotoThatWasNeverPublishedStaysPendingWhenNotSelected() {
        MemoryWallPhoto pending = photo(1, NOW, null);
        when(photos.findAllForAdmin()).thenReturn(List.of(pending));

        service.saveSelection(new MemoryWallSelectionRequest(List.of()), ADMIN);

        assertEquals(MemoryWallPhoto.PENDING_REVIEW, pending.getStatus());
    }

    @Test void saveSelectionRefusesMoreThanTheLimitOfPublicPhotos() {
        var thrown = assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.saveSelection(new MemoryWallSelectionRequest(List.of(1L, 2L, 3L)), ADMIN));

        assertEquals("Only 2 photos can be shown on the Memory Wall. Uncheck some photos before selecting more.",
                thrown.getMessage());
        verify(photos, never()).saveAllAndFlush(anyList());
    }

    @Test void aRepeatedPhotoIdIsCountedOnce() {
        MemoryWallPhoto one = photo(1, NOW, null);
        when(photos.findAllForAdmin()).thenReturn(List.of(one));

        service.saveSelection(new MemoryWallSelectionRequest(List.of(1L, 1L, 1L)), ADMIN);

        assertTrue(one.isPublished());
    }

    @Test void emptyOrMissingSelectionsUnpublishEverything() {
        MemoryWallPhoto published = publishedPhoto(1, NOW);
        when(photos.findAllForAdmin()).thenReturn(List.of(published));

        service.saveSelection(null, ADMIN);
        assertFalse(published.isPublished());

        published.publish(NOW);
        service.saveSelection(new MemoryWallSelectionRequest(null), ADMIN);
        assertFalse(published.isPublished());
    }

    @Test void aSelectedPhotoThatNoLongerExistsStopsTheWholeSave() {
        MemoryWallPhoto one = photo(1, NOW, null);
        when(photos.findAllForAdmin()).thenReturn(List.of(one));

        var thrown = assertThrows(AppExceptions.ValidationException.class,
                () -> service.saveSelection(new MemoryWallSelectionRequest(List.of(1L, 99L)), ADMIN));

        assertEquals("One of the selected photos no longer exists. Refresh the page and try again.", thrown.getMessage());
        assertFalse(one.isPublished());
        verify(photos, never()).saveAllAndFlush(anyList());
    }

    @Test void aPhotoWhoseTestimonialIsNotApprovedCannotBePublished() {
        MemoryWallPhoto blocked = testimonialPhoto(4, "PENDING", true, "Alex");
        when(photos.findAllForAdmin()).thenReturn(List.of(blocked));

        var thrown = assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.saveSelection(new MemoryWallSelectionRequest(List.of(4L)), ADMIN));

        assertEquals("This photo came with a testimonial that is not approved yet. Approve the testimonial first.",
                thrown.getMessage());
        verify(blocked, never()).publish(any());
        verify(photos, never()).saveAllAndFlush(anyList());
    }

    // ------------------------------------------------------------- delete

    @Test void deleteRemovesTheRecordAndThenTheFileAfterCommit() {
        MemoryWallPhoto photo = photo(1, NOW, null);
        when(photos.findWithTestimonial(1L)).thenReturn(Optional.of(photo));

        service.delete(1L, ADMIN);

        verify(auditTrail).actAs(ADMIN);
        verify(photos).delete(photo);
        verify(photos).flush();
        verify(storage, never()).delete(anyString());

        finishTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(storage).delete("memory-wall/p1.png");
    }

    @Test void theFileIsKeptWhenTheDeleteIsRolledBack() {
        when(photos.findWithTestimonial(1L)).thenReturn(Optional.of(photo(1, NOW, null)));

        service.delete(1L, ADMIN);
        finishTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage, never()).delete(anyString());
    }

    @Test void deletingAnUnknownPhotoIsNotFound() {
        when(photos.findWithTestimonial(9L)).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> service.delete(9L, ADMIN));
        verify(photos, never()).delete(any());
    }
}