package ca.sheridancollege.shtirthb.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.dto.ContentPageAdminResponse;
import ca.sheridancollege.shtirthb.dto.ContentPagePublicResponse;
import ca.sheridancollege.shtirthb.dto.ContentPageRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.ContentPage;
import ca.sheridancollege.shtirthb.repository.ContentPageRepository;

@Service
public class ContentPageService {

    static final int MAX_TITLE_LENGTH = 180;
    static final int MAX_BODY_LENGTH = 50_000;

    private static final Map<String, String> LABELS = Map.of(
            "services", "Service page",
            "about", "About and Credentials page",
            "privacy", "Privacy Policy page"
    );

    private final ContentPageRepository pages;
    private final AuditTrailService auditTrail;
    private final ContentBodyValidator bodyValidator;

    public ContentPageService(
            ContentPageRepository pages,
            AuditTrailService auditTrail,
            ContentBodyValidator bodyValidator) {

        this.pages = pages;
        this.auditTrail = auditTrail;
        this.bodyValidator = bodyValidator;
    }

    // Public: the published copy, or empty when nothing has been published.
    @Transactional(readOnly = true)
    public Optional<ContentPagePublicResponse> getPublished(String pageKey) {

        return pages.findByPageKey(normalizeKey(pageKey))
                .filter(ContentPage::isPublished)
                .map(page -> new ContentPagePublicResponse(
                        page.getPageKey(),
                        page.getPublishedTitle(),
                        page.getPublishedBody(),
                        page.getPublishedAt()
                ));
    }

    @Transactional(readOnly = true)
    public List<ContentPageAdminResponse> getAdminPages() {

        return pages.findAllByOrderByIdAsc()
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContentPageAdminResponse getAdminPage(String pageKey) {
        return toAdminResponse(findPage(pageKey));
    }

    @Transactional
    public ContentPageAdminResponse save(
            String pageKey,
            ContentPageRequest request,
            String adminEmail) {

        ContentPage page = findPage(pageKey);

        String title = request == null || request.getTitle() == null
                ? ""
                : request.getTitle().trim();

        String body = request == null || request.getBody() == null
                ? ""
                : request.getBody().strip();

        validate(page.getPageKey(), title, body);

        auditTrail.actAs(adminEmail);

        if (Boolean.TRUE.equals(request.getPublish())) {
            page.publish(title, body);
        } else {
            page.saveDraft(title, body);
        }

        return toAdminResponse(pages.save(page));
    }

    private void validate(String pageKey, String title, String body) {

        List<String> missing = new ArrayList<>();

        if (title.isEmpty()) {
            missing.add("page title");
        }

        if (body.isEmpty()) {
            missing.add("page content");
        }

        if (!missing.isEmpty()) {
            throw new AppExceptions.ValidationException(
                    "Required content is missing: "
                            + String.join(", ", missing) + "."
            );
        }

        if (title.length() > MAX_TITLE_LENGTH) {
            throw new AppExceptions.ValidationException(
                    "The page title must be " + MAX_TITLE_LENGTH
                            + " characters or fewer."
            );
        }

        if (body.length() > MAX_BODY_LENGTH) {
            throw new AppExceptions.ValidationException(
                    "The page content must be " + MAX_BODY_LENGTH
                            + " characters or fewer."
            );
        }

        if (title.indexOf('\u0000') >= 0 || body.indexOf('\u0000') >= 0) {
            throw new AppExceptions.ValidationException(
                    "The content contains characters that are not allowed."
            );
        }

        // Only plain text for the page's named slots is accepted.
        bodyValidator.validate(pageKey, body);
    }

    private ContentPage findPage(String pageKey) {

        return pages.findByPageKey(normalizeKey(pageKey))
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException("Page")
                );
    }

    private String normalizeKey(String pageKey) {
        return pageKey == null
                ? ""
                : pageKey.trim().toLowerCase(Locale.ROOT);
    }

    private ContentPageAdminResponse toAdminResponse(ContentPage page) {

        return new ContentPageAdminResponse(
                page.getPageKey(),
                LABELS.getOrDefault(page.getPageKey(), page.getPageKey()),
                page.isPublished(),
                page.hasUnpublishedChanges(),
                page.getPublishedTitle(),
                page.getPublishedBody(),
                page.getDraftTitle(),
                page.getDraftBody(),
                page.getPublishedAt(),
                page.getUpdatedAt()
        );
    }
}
