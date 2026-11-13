package ca.sheridancollege.shtirthb.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import ca.sheridancollege.shtirthb.config.AppProperties;
import ca.sheridancollege.shtirthb.dto.MemoryWallAdminOverview;
import ca.sheridancollege.shtirthb.dto.MemoryWallDescriptionRequest;
import ca.sheridancollege.shtirthb.dto.MemoryWallImage;
import ca.sheridancollege.shtirthb.dto.MemoryWallMonth;
import ca.sheridancollege.shtirthb.dto.MemoryWallPhotoAdminResponse;
import ca.sheridancollege.shtirthb.dto.MemoryWallPublicPhoto;
import ca.sheridancollege.shtirthb.dto.MemoryWallSelectionRequest;
import ca.sheridancollege.shtirthb.dto.MemoryWallSummaryResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.model.CustomerTestimonial;
import ca.sheridancollege.shtirthb.model.FeatureVisibility;
import ca.sheridancollege.shtirthb.model.MemoryWallPhoto;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.FeatureVisibilityRepository;
import ca.sheridancollege.shtirthb.repository.MemoryWallPhotoRepository;
import ca.sheridancollege.shtirthb.service.MemoryWallImageValidator.ValidatedImage;

/*
 * The Memory Wall (UC-12).
 *
 *   - Every photo starts as PENDING_REVIEW and is never public until the
 *     administrator selects it and saves.
 *   - The public wall shows PUBLISHED photos only, grouped by upload month.
 *   - At most max-photos photos exist, and at most max-published-photos are
 *     public (both set in application.properties).
 *   - A photo's picture lives in the PhotoStorage (a folder now, S3 later) or,
 *     for a photo that came with a testimonial, with that testimonial.
 *
 * Counting changes are serialised with a database lock, so two
 * administrators can never both pass a limit.
 */
@Service
public class MemoryWallService {

    private static final long CHANGE_LOCK_KEY = 120_001L;
    private static final int DESCRIPTION_MAX = 200;

    private static final DateTimeFormatter MONTH_KEY =
            DateTimeFormatter.ofPattern("yyyy-MM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private final MemoryWallPhotoRepository photos;
    private final PhotoStorage storage;
    private final MemoryWallImageValidator validator;
    private final AuditTrailService auditTrail;
    private final AdminUserRepository adminUsers;
    private final AppointmentClock clock;
    private final AppProperties properties;
    private final FeatureVisibilityRepository features;

    public MemoryWallService(
            MemoryWallPhotoRepository photos,
            PhotoStorage storage,
            MemoryWallImageValidator validator,
            AuditTrailService auditTrail,
            AdminUserRepository adminUsers,
            AppointmentClock clock,
            AppProperties properties,
            FeatureVisibilityRepository features) {

        this.photos = photos;
        this.storage = storage;
        this.validator = validator;
        this.auditTrail = auditTrail;
        this.adminUsers = adminUsers;
        this.clock = clock;
        this.properties = properties;
        this.features = features;
    }

    // ------------------------------------------------------------- public

    // Published photos only, grouped by upload month (newest month first).
    @Transactional(readOnly = true)
    public List<MemoryWallMonth> getPublicWall() {

        requireFeatureOn();

        Map<String, List<MemoryWallPublicPhoto>> byMonth = new LinkedHashMap<>();
        Map<String, String> labels = new HashMap<>();

        for (MemoryWallPhoto photo : photos.findAllPublic()) {

            LocalDateTime uploaded = photo.getUploadedAt();
            String key = uploaded.format(MONTH_KEY);

            labels.putIfAbsent(key, uploaded.format(MONTH_LABEL));

            byMonth.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new MemoryWallPublicPhoto(
                            photo.getId(),
                            altText(photo),
                            uploaded.toString(),
                            uploaded.format(DAY_LABEL)));
        }

        List<MemoryWallMonth> months = new ArrayList<>();

        byMonth.forEach((key, list) ->
                months.add(new MemoryWallMonth(key, labels.get(key), list)));

        return months;
    }

    // A photo that is not published (or is gone) is simply "not found".
    @Transactional(readOnly = true)
    public MemoryWallImage getPublicImage(Long id) {

        requireFeatureOn();

        MemoryWallPhoto photo = photos.findPublicById(id)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException("Photo"));

        return load(photo);
    }

    // -------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public MemoryWallAdminOverview getOverview() {
        return overview(photos.findAllForAdmin());
    }

    @Transactional(readOnly = true)
    public MemoryWallImage getAdminImage(Long id) {
        return load(find(id));
    }

    @Transactional
    public MemoryWallPhotoAdminResponse upload(
            MultipartFile file,
            String description,
            String adminEmail) {

        ValidatedImage image = validator.validate(file, adminEmail);
        String cleanedDescription = cleanDescription(description);

        photos.lockForChanges(CHANGE_LOCK_KEY);

        int max = properties.getMemoryWall().getMaxPhotos();

        if (photos.count() >= max) {
            throw new AppExceptions.BusinessRuleException(
                    "The Memory Wall already holds the maximum of " + max
                    + " photos. Delete some photos on this page before adding more.");
        }

        String key = "memory-wall/" + UUID.randomUUID() + "." + image.extension();

        storage.store(key, image.content(), image.contentType());
        removeFileUnlessCommitted(key);

        auditTrail.actAs(adminEmail);

        MemoryWallPhoto saved = photos.saveAndFlush(new MemoryWallPhoto(
                key,
                image.fileName(),
                image.contentType(),
                image.content().length,
                cleanedDescription,
                adminUsers.findByEmail(adminEmail)
                        .map(AdminUser::getId)
                        .orElse(null),
                clock.now()));

        return toAdminResponse(saved);
    }

    @Transactional
    public MemoryWallPhotoAdminResponse changeDescription(
            Long id,
            MemoryWallDescriptionRequest request,
            String adminEmail) {

        MemoryWallPhoto photo = find(id);

        auditTrail.actAs(adminEmail);

        photo.describe(
                cleanDescription(request == null ? null : request.description()),
                clock.now());

        photos.saveAndFlush(photo);

        return toAdminResponse(photo);
    }

    /*
     * "Save changes" on Manage Memory Wall: the given photos are shown on the
     * public wall and every other published photo is taken off it. Photos that
     * were never published stay pending. All or nothing.
     */
    @Transactional
    public MemoryWallAdminOverview saveSelection(
            MemoryWallSelectionRequest request,
            String adminEmail) {

        Set<Long> wanted = new LinkedHashSet<>();

        if (request != null && request.photoIds() != null) {
            for (Long id : request.photoIds()) {
                if (id != null) {
                    wanted.add(id);
                }
            }
        }

        int max = properties.getMemoryWall().getMaxPublishedPhotos();

        if (wanted.size() > max) {
            throw new AppExceptions.BusinessRuleException(
                    "Only " + max + " photos can be shown on the Memory Wall. "
                    + "Uncheck some photos before selecting more.");
        }

        photos.lockForChanges(CHANGE_LOCK_KEY);

        List<MemoryWallPhoto> all = photos.findAllForAdmin();
        Set<Long> known = new LinkedHashSet<>();

        for (MemoryWallPhoto photo : all) {
            known.add(photo.getId());
        }

        if (!known.containsAll(wanted)) {
            throw new AppExceptions.ValidationException(
                    "One of the selected photos no longer exists. "
                    + "Refresh the page and try again.");
        }

        LocalDateTime now = clock.now();

        auditTrail.actAs(adminEmail);

        for (MemoryWallPhoto photo : all) {

            boolean shouldShow = wanted.contains(photo.getId());

            if (shouldShow && !photo.isPublished()) {

                String blocked = publishBlockedReason(photo);

                if (blocked != null) {
                    throw new AppExceptions.BusinessRuleException(blocked);
                }

                photo.publish(now);

            } else if (!shouldShow && photo.isPublished()) {

                photo.unpublish(now);
            }
        }

        photos.saveAllAndFlush(all);

        return overview(all);
    }

    @Transactional
    public void delete(Long id, String adminEmail) {

        photos.lockForChanges(CHANGE_LOCK_KEY);

        MemoryWallPhoto photo = find(id);
        String key = photo.getStorageKey();

        auditTrail.actAs(adminEmail);

        photos.delete(photo);
        photos.flush();

        // The file goes only once the record is really gone.
        if (key != null) {
            removeFileAfterCommit(key);
        }
    }

    // ------------------------------------------------------------ helpers

    /*
     * While the administrator has the Memory Wall switched off (Feature
     * Visibility), visitors get "not found": nothing shows that the feature
     * exists. Managing the photos is not affected.
     */
    private void requireFeatureOn() {

        boolean on = features.findByFeatureKey("MEMORY_WALL")
                .map(FeatureVisibility::isEnabled)
                .orElse(false);

        if (!on) {
            throw new AppExceptions.ResourceNotFoundException("Page");
        }
    }

    private MemoryWallPhoto find(Long id) {

        return photos.findWithTestimonial(id)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException("Photo"));
    }

    private MemoryWallImage load(MemoryWallPhoto photo) {

        if (photo.isFromTestimonial()) {

            CustomerTestimonial testimonial = photo.getTestimonial();
            Integer order = photo.getPhotoOrder();

            if (testimonial == null
                    || order == null
                    || order < 0
                    || order >= testimonial.getPhotos().size()) {

                throw new AppExceptions.ResourceNotFoundException("Photo");
            }

            // "data:image/png;base64,AAAA…": the picture is after the comma.
            String dataUrl = testimonial.getPhotos().get(order);

            return new MemoryWallImage(
                    photo.getContentType(),
                    Base64.getDecoder().decode(
                            dataUrl.substring(dataUrl.indexOf(',') + 1)));
        }

        return new MemoryWallImage(
                photo.getContentType(),
                storage.load(photo.getStorageKey()));
    }

    private MemoryWallAdminOverview overview(List<MemoryWallPhoto> all) {

        List<MemoryWallPhotoAdminResponse> items = new ArrayList<>();
        long bytes = 0;
        int published = 0;

        for (MemoryWallPhoto photo : all) {

            items.add(toAdminResponse(photo));
            bytes += photo.getSizeBytes();

            if (photo.isPublished()) {
                published++;
            }
        }

        AppProperties.MemoryWall rules = properties.getMemoryWall();

        return new MemoryWallAdminOverview(
                items,
                new MemoryWallSummaryResponse(
                        all.size(),
                        rules.getMaxPhotos(),
                        Math.max(0, rules.getMaxPhotos() - all.size()),
                        published,
                        rules.getMaxPublishedPhotos(),
                        bytes,
                        rules.getMaxFileSizeBytes(),
                        ResourceUploadValidator.formatSize(
                                rules.getMaxFileSizeBytes()),
                        validator.allowedExtensionList(),
                        validator.acceptedFormats()));
    }

    private MemoryWallPhotoAdminResponse toAdminResponse(MemoryWallPhoto photo) {

        CustomerTestimonial testimonial = photo.isFromTestimonial()
                ? photo.getTestimonial()
                : null;

        String blocked = publishBlockedReason(photo);

        return new MemoryWallPhotoAdminResponse(
                photo.getId(),
                photo.getStatus(),
                photo.getSource(),
                testimonial != null
                        ? "Customer: " + testimonial.getDisplayName()
                        : photo.isFromTestimonial()
                                ? "Customer"
                                : "Administrator",
                photo.getUploadedAt().toString(),
                photo.getPublishedAt() == null
                        ? null
                        : photo.getPublishedAt().toString(),
                photo.getOriginalFilename() == null
                        ? "Photo from a testimonial"
                        : photo.getOriginalFilename(),
                photo.getContentType(),
                photo.getSizeBytes(),
                photo.getDescription(),
                altText(photo),
                testimonial == null ? null : testimonial.getStatus(),
                blocked == null,
                blocked);
    }

    // Null when the photo may be shown on the wall.
    private String publishBlockedReason(MemoryWallPhoto photo) {

        if (!photo.isFromTestimonial()) {
            return null;
        }

        CustomerTestimonial testimonial = photo.getTestimonial();

        if (testimonial != null
                && "APPROVED".equals(testimonial.getStatus())
                && testimonial.isPublicationConsent()) {
            return null;
        }

        return "This photo came with a testimonial that is not approved yet. "
                + "Approve the testimonial first.";
    }

    // The text screen readers get: the description, or a plain fallback.
    private String altText(MemoryWallPhoto photo) {

        if (photo.getDescription() != null) {
            return photo.getDescription();
        }

        CustomerTestimonial testimonial = photo.isFromTestimonial()
                ? photo.getTestimonial()
                : null;

        if (testimonial != null) {
            return "Photo shared by " + testimonial.getDisplayName();
        }

        return "Memory Wall photo from "
                + photo.getUploadedAt().format(DAY_LABEL);
    }

    private String cleanDescription(String value) {

        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.length() > DESCRIPTION_MAX) {
            throw new AppExceptions.ValidationException(
                    "Please keep the description under " + DESCRIPTION_MAX
                    + " characters.");
        }

        return trimmed;
    }

    // A file stored in this transaction is removed again if it is rolled back.
    private void removeFileUnlessCommitted(String key) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            storage.delete(key);
                        }
                    }
                });
    }

    private void removeFileAfterCommit(String key) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        storage.delete(key);
                    }
                });
    }
}
