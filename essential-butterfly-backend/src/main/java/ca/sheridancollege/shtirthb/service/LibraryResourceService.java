package ca.sheridancollege.shtirthb.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ca.sheridancollege.shtirthb.dto.LibraryResourceAdminResponse;
import ca.sheridancollege.shtirthb.dto.LibraryResourcePublicResponse;
import ca.sheridancollege.shtirthb.dto.LibraryResourceRequest;
import ca.sheridancollege.shtirthb.dto.ResourceDownload;
import ca.sheridancollege.shtirthb.dto.UploadRulesResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.LibraryResource;
import ca.sheridancollege.shtirthb.model.LibraryResourceFile;
import ca.sheridancollege.shtirthb.repository.LibraryResourceFileRepository;
import ca.sheridancollege.shtirthb.repository.LibraryResourceRepository;
import ca.sheridancollege.shtirthb.service.ResourceUploadValidator.ValidatedUpload;

/*
 * The Resource Library.
 *
 * Every save is one transaction: the resource row, its file and the audit
 * trail are stored together or not at all, so a failed save never leaves a
 * half-published resource and never changes what visitors already see.
 */
@Service
public class LibraryResourceService {

    static final int MAX_TITLE_LENGTH = 80;
    static final int MAX_DESCRIPTION_LENGTH = 300;
    static final int MAX_CATEGORY_LENGTH = 40;
    static final int MAX_ALT_TEXT_LENGTH = 150;
    static final int MAX_LINKS = 5;

    private final LibraryResourceRepository resources;
    private final LibraryResourceFileRepository files;
    private final ResourceUploadValidator validator;
    private final AuditTrailService auditTrail;

    public LibraryResourceService(
            LibraryResourceRepository resources,
            LibraryResourceFileRepository files,
            ResourceUploadValidator validator,
            AuditTrailService auditTrail) {

        this.resources = resources;
        this.files = files;
        this.validator = validator;
        this.auditTrail = auditTrail;
    }

    // ------------------------------------------------------------- public

    // Visitors: published resources only. Drafts and removed ones never appear.
    @Transactional(readOnly = true)
    public List<LibraryResourcePublicResponse> getPublicResources() {

        return resources
                .findAllByStatusOrderByTitleAsc(LibraryResource.STATUS_PUBLISHED)
                .stream()
                .map(this::toPublicResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResourceDownload getPublicDownload(Long id) {

        LibraryResource resource = resources
                .findByIdAndStatus(id, LibraryResource.STATUS_PUBLISHED)
                .filter(LibraryResource::isFile)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException("Resource")
                );

        LibraryResourceFile stored = files.findById(id)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException("Resource file")
                );

        return new ResourceDownload(
                resource.getFileName(),
                resource.getFileContentType(),
                stored.getContent(),
                !resource.getFileContentType().startsWith("application/vnd.")
        );
    }

    // -------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<LibraryResourceAdminResponse> getAdminResources() {

        return resources
                .findAllByStatusNotOrderByUpdatedAtDesc(LibraryResource.STATUS_DELETED)
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    public UploadRulesResponse getUploadRules() {
        return validator.rules();
    }

    @Transactional
    public LibraryResourceAdminResponse create(
            LibraryResourceRequest request,
            MultipartFile file,
            String adminEmail) {

        return save(null, request, file, adminEmail);
    }

    @Transactional
    public LibraryResourceAdminResponse update(
            Long id,
            LibraryResourceRequest request,
            MultipartFile file,
            String adminEmail) {

        LibraryResource existing = findLive(id);

        return save(existing, request, file, adminEmail);
    }

    @Transactional
    public void remove(Long id, String adminEmail) {

        LibraryResource resource = findLive(id);

        auditTrail.actAs(adminEmail);

        resource.changeStatus(LibraryResource.STATUS_DELETED);
        resources.save(resource);

        files.deleteById(id);
    }

    // ------------------------------------------------------------ helpers

    /*
     * Shared by create and update. "existing" is null when adding.
     * Everything is checked before anything is written.
     */
    private LibraryResourceAdminResponse save(
            LibraryResource existing,
            LibraryResourceRequest request,
            MultipartFile file,
            String adminEmail) {

        String title = clean(request == null ? null : request.title());
        String description = clean(request == null ? null : request.description());
        String category = clean(request == null ? null : request.category());
        String altText = clean(request == null ? null : request.altText());

        boolean linksProvided = request != null && request.linkUrls() != null;
        List<String> linkInputs = parseLinks(request == null ? null : request.linkUrls());

        boolean hasNewFile = file != null && !file.isEmpty();
        boolean removeFile = request != null && request.removeFile();

        boolean keepsExistingFile = existing != null
                && existing.isFile()
                && !hasNewFile
                && !removeFile;

        boolean keepsExistingLinks = existing != null
                && !linksProvided
                && !existing.getLinks().isEmpty();

        // Missing fields are reported together, before any file is opened.
        List<String> missing = new ArrayList<>();

        if (title.isEmpty()) {
            missing.add("title");
        }

        if (description.isEmpty()) {
            missing.add("short description");
        }

        if (category.isEmpty()) {
            missing.add("category");
        }

        if (linkInputs.isEmpty() && !keepsExistingLinks
                && !hasNewFile && !keepsExistingFile) {
            missing.add("link or document");
        }

        if (!missing.isEmpty()) {
            throw new AppExceptions.ValidationException(
                    "Required content is missing: "
                            + String.join(", ", missing) + "."
            );
        }

        validateLengths(title, description, category, altText);

        if (linkInputs.size() > MAX_LINKS) {
            throw new AppExceptions.ValidationException(
                    "You can add up to " + MAX_LINKS + " links to one resource."
            );
        }

        ValidatedUpload upload = hasNewFile
                ? validator.validateFile(file, adminEmail)
                : null;

        List<String> links = new ArrayList<>();

        for (String input : linkInputs) {
            links.add(validator.validateLink(input, adminEmail));
        }

        boolean willBeImage = upload != null
                ? upload.image()
                : keepsExistingFile && existing.isImage();

        if (willBeImage && altText.isEmpty()) {
            throw new AppExceptions.ValidationException(
                    "Required content is missing: alternative text. "
                    + "Describe the image for people who cannot see it."
            );
        }

        ensureNotDuplicate(title, links.isEmpty() ? null : links.get(0), existing);

        auditTrail.actAs(adminEmail);

        LibraryResource resource = existing != null
                ? existing
                : new LibraryResource(title, description, category, altText);

        boolean hadFile = existing != null && existing.isFile();

        resource.setTitle(title);
        resource.setDescription(description);
        resource.setCategory(category);
        resource.setAltText(altText.isEmpty() ? null : altText);

        if (linksProvided || existing == null) {
            resource.setLinks(links);
        }

        if (upload != null) {
            resource.setFile(
                    upload.displayName(),
                    upload.contentType(),
                    upload.content().length
            );
        } else if (!keepsExistingFile) {
            resource.clearFile();
            resource.setAltText(null);
        }

        resource.changeStatus(
                request.publish()
                        ? LibraryResource.STATUS_PUBLISHED
                        : LibraryResource.STATUS_DRAFT
        );

        LibraryResource saved = resources.save(resource);

        if (upload != null) {
            files.save(new LibraryResourceFile(saved.getId(), upload.content()));
        } else if (hadFile && !keepsExistingFile) {
            files.deleteById(saved.getId());
        }

        return toAdminResponse(saved);
    }

    // One link per line; blank lines and repeats are ignored.
    private List<String> parseLinks(String text) {

        List<String> found = new ArrayList<>();

        if (text == null) {
            return found;
        }

        for (String line : text.split("\\R")) {
            String link = line.trim();

            if (!link.isEmpty() && !found.contains(link)) {
                found.add(link);
            }
        }

        return found;
    }

    private void validateLengths(
            String title,
            String description,
            String category,
            String altText) {

        if (title.length() > MAX_TITLE_LENGTH) {
            throw new AppExceptions.ValidationException(
                    "The title must be " + MAX_TITLE_LENGTH + " characters or fewer."
            );
        }

        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new AppExceptions.ValidationException(
                    "The short description must be " + MAX_DESCRIPTION_LENGTH
                            + " characters or fewer."
            );
        }

        if (category.length() > MAX_CATEGORY_LENGTH) {
            throw new AppExceptions.ValidationException(
                    "The category must be " + MAX_CATEGORY_LENGTH
                            + " characters or fewer."
            );
        }

        if (altText.length() > MAX_ALT_TEXT_LENGTH) {
            throw new AppExceptions.ValidationException(
                    "The alternative text must be " + MAX_ALT_TEXT_LENGTH
                            + " characters or fewer."
            );
        }
    }

    /*
     * A resource with the same title (any capitalisation) or the same link
     * already exists. The administrator is warned and can cancel or update the
     * existing resource instead.
     */
    private void ensureNotDuplicate(
            String title,
            String link,
            LibraryResource self) {

        Long selfId = self == null ? null : self.getId();

        List<LibraryResource> sameTitle =
                resources.findAllByTitleIgnoreCaseAndStatusNot(
                        title, LibraryResource.STATUS_DELETED);

        List<LibraryResource> sameLink = link == null
                ? List.of()
                : resources.findAllByLinkUrlAndStatusNot(
                        link, LibraryResource.STATUS_DELETED);

        List<LibraryResource> matches = new ArrayList<>(sameTitle);
        matches.addAll(sameLink);

        for (LibraryResource match : matches) {

            if (selfId == null || !selfId.equals(match.getId())) {
                throw new AppExceptions.ConflictException(
                        "A resource with the same title or link already exists (\""
                                + match.getTitle() + "\"). "
                                + "Edit the existing resource instead, or cancel."
                );
            }
        }
    }

    private LibraryResource findLive(Long id) {

        return resources
                .findByIdAndStatusNot(id, LibraryResource.STATUS_DELETED)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException("Resource")
                );
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private LibraryResourceAdminResponse toAdminResponse(LibraryResource resource) {

        return new LibraryResourceAdminResponse(
                resource.getId(),
                resource.getTitle(),
                resource.getDescription(),
                resource.getCategory(),
                resource.getResourceType(),
                resource.getLinkUrl(),
                resource.getLinks(),
                resource.getFileName(),
                resource.getFileContentType(),
                resource.getFileSize(),
                resource.getAltText(),
                resource.getStatus(),
                resource.getPublishedAt(),
                resource.getCreatedAt(),
                resource.getUpdatedAt()
        );
    }

    private LibraryResourcePublicResponse toPublicResponse(LibraryResource resource) {

        return new LibraryResourcePublicResponse(
                resource.getId(),
                resource.getTitle(),
                resource.getDescription(),
                resource.getCategory(),
                resource.getResourceType(),
                resource.getLinkUrl(),
                resource.getLinks(),
                resource.getFileName(),
                resource.getFileContentType(),
                resource.getFileSize(),
                resource.getAltText(),
                resource.getPublishedAt()
        );
    }
}
