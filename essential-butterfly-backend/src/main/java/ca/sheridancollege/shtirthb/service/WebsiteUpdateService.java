package ca.sheridancollege.shtirthb.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ca.sheridancollege.shtirthb.dto.PublicUpdateSummaryResponse;
import ca.sheridancollege.shtirthb.dto.WebsiteUpdateRequest;
import ca.sheridancollege.shtirthb.dto.WebsiteUpdateResponse;
import ca.sheridancollege.shtirthb.model.FeatureVisibility;
import ca.sheridancollege.shtirthb.model.WebsiteUpdate;
import ca.sheridancollege.shtirthb.repository.FeatureVisibilityRepository;
import ca.sheridancollege.shtirthb.repository.WebsiteUpdateRepository;

@Service
public class WebsiteUpdateService {

    private final WebsiteUpdateRepository updateRepository;
    private final FeatureVisibilityRepository featureRepository;

    public WebsiteUpdateService(
            WebsiteUpdateRepository updateRepository,
            FeatureVisibilityRepository featureRepository) {
        this.updateRepository = updateRepository;
        this.featureRepository = featureRepository;
    }

    @Transactional(readOnly = true)
    public List<PublicUpdateSummaryResponse> getPublicUpdates() {
        if (!updatesEnabled()) return List.of();

        return updateRepository.findAllByPublishedTrueOrderByUpdatedAtDesc()
            .stream()
            .map(update -> new PublicUpdateSummaryResponse(
                update.getId(),
                update.getTitle(),
                createPreview(update.getContent()),
                update.getCreatedAt(),
                update.getUpdatedAt(),
                update.getPhotos().isEmpty()
                    ? null
                    : update.getPhotos().get(0)
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public WebsiteUpdateResponse getPublicUpdate(Long id) {
        if (!updatesEnabled()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Updates are currently unavailable."
            );
        }

        WebsiteUpdate update = updateRepository.findByIdAndPublishedTrue(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "This update is unavailable."
            ));

        return toResponse(update);
    }

    @Transactional(readOnly = true)
    public List<WebsiteUpdateResponse> getAllAdminUpdates() {
        return updateRepository.findAllByOrderByUpdatedAtDesc()
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public WebsiteUpdateResponse createUpdate(WebsiteUpdateRequest request) {
        validateRequest(request);

        WebsiteUpdate update = new WebsiteUpdate(
            request.getTitle().trim(),
            request.getContent().trim(),
            request.getPublished()
        );

        applyOptionalContent(update, request);
        return toResponse(updateRepository.save(update));
    }

    @Transactional
    public WebsiteUpdateResponse editUpdate(
            Long id, WebsiteUpdateRequest request) {
        validateRequest(request);

        WebsiteUpdate update = updateRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Update not found."
            ));

        update.setTitle(request.getTitle().trim());
        update.setContent(request.getContent().trim());
        update.setPublished(request.getPublished());

        applyOptionalContent(update, request);
        return toResponse(updateRepository.save(update));
    }

    @Transactional
    public void deleteUpdate(Long id) {
        WebsiteUpdate update = updateRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Update not found."
            ));

        updateRepository.delete(update);
    }

    private void applyOptionalContent(
            WebsiteUpdate update, WebsiteUpdateRequest request) {

        List<String> photos = request.getPhotos() == null
            ? List.of() : request.getPhotos();

        List<String> links = request.getSocialLinks() == null
            ? List.of() : request.getSocialLinks();

        if (photos.size() > 5) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Maximum 5 photos are allowed."
            );
        }

        if (links.size() > 5) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Maximum 5 social links are allowed."
            );
        }

        for (String photo : photos) {
            if (photo == null ||
                !(photo.startsWith("data:image/jpeg;base64,") ||
                  photo.startsWith("data:image/png;base64,") ||
                  photo.startsWith("data:image/webp;base64,")) ||
                photo.length() > 3_000_000) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use JPEG, PNG or WebP photos. Each photo must be under 2 MB."
                );
            }
        }

        for (String link : links) {
            if (link == null ||
                !(link.startsWith("https://") || link.startsWith("http://")) ||
                link.length() > 1000) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Social links must be valid HTTP or HTTPS URLs."
                );
            }
        }

        update.setPhotos(photos);
        update.setSocialLinks(links);
    }

    private boolean updatesEnabled() {
        return featureRepository.findByFeatureKey("UPDATES")
            .map(FeatureVisibility::isEnabled)
            .orElse(false);
    }

    private void validateRequest(WebsiteUpdateRequest request) {
        if (request == null ||
            request.getTitle() == null || request.getTitle().isBlank() ||
            request.getContent() == null || request.getContent().isBlank() ||
            request.getPublished() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Title, content and publishing status are required."
            );
        }
    }

    private String createPreview(String content) {
        String plainText = content.replaceAll("\\s+", " ").trim();

        return plainText.length() <= 200
            ? plainText
            : plainText.substring(0, 200).trim() + "...";
    }

    private WebsiteUpdateResponse toResponse(WebsiteUpdate update) {
        return new WebsiteUpdateResponse(
            update.getId(),
            update.getTitle(),
            update.getContent(),
            update.isPublished(),
            update.getCreatedAt(),
            update.getUpdatedAt(),
            update.getPhotos(),
            update.getSocialLinks()
        );
    }
}