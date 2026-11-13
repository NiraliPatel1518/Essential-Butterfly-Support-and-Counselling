package ca.sheridancollege.shtirthb.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ca.sheridancollege.shtirthb.dto.LibraryResourceAdminResponse;
import ca.sheridancollege.shtirthb.dto.LibraryResourcePublicResponse;
import ca.sheridancollege.shtirthb.dto.LibraryResourceRequest;
import ca.sheridancollege.shtirthb.dto.ResourceDownload;
import ca.sheridancollege.shtirthb.dto.UploadRulesResponse;
import ca.sheridancollege.shtirthb.service.LibraryResourceService;

@RestController
public class LibraryResourceController {

    private final LibraryResourceService service;

    public LibraryResourceController(LibraryResourceService service) {
        this.service = service;
    }

    // ------------------------------------------------------------- public

    // Visitors: published resources only.
    @GetMapping("/api/resources")
    public ResponseEntity<List<LibraryResourcePublicResponse>> getPublicResources() {
        return ResponseEntity.ok(service.getPublicResources());
    }

    // Visitors: open or download a published file.
    @GetMapping("/api/resources/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {

        ResourceDownload download = service.getPublicDownload(id);

        ContentDisposition disposition = (download.inline()
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.content());
    }

    // -------------------------------------------------------------- admin

    @GetMapping("/api/admin/resources")
    public ResponseEntity<List<LibraryResourceAdminResponse>> getAdminResources() {
        return ResponseEntity.ok(service.getAdminResources());
    }

    // The accepted formats and size limit, so the form never hard-codes them.
    @GetMapping("/api/admin/resources/upload-rules")
    public ResponseEntity<UploadRulesResponse> getUploadRules() {
        return ResponseEntity.ok(service.getUploadRules());
    }

    @PostMapping(
            value = "/api/admin/resources",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LibraryResourceAdminResponse> createResource(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "linkUrls", required = false) String linkUrls,
            @RequestParam(value = "altText", required = false) String altText,
            @RequestParam(value = "publish", defaultValue = "false") boolean publish,
            @RequestParam(value = "removeFile", defaultValue = "false") boolean removeFile,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Authentication authentication) {

        LibraryResourceRequest request = new LibraryResourceRequest(
                title, description, category, linkUrls, altText, publish, removeFile);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request, file, authentication.getName()));
    }

    @PutMapping(
            value = "/api/admin/resources/{id}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LibraryResourceAdminResponse> updateResource(
            @PathVariable Long id,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "linkUrls", required = false) String linkUrls,
            @RequestParam(value = "altText", required = false) String altText,
            @RequestParam(value = "publish", defaultValue = "false") boolean publish,
            @RequestParam(value = "removeFile", defaultValue = "false") boolean removeFile,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Authentication authentication) {

        LibraryResourceRequest request = new LibraryResourceRequest(
                title, description, category, linkUrls, altText, publish, removeFile);

        return ResponseEntity.ok(
                service.update(id, request, file, authentication.getName())
        );
    }

    @DeleteMapping("/api/admin/resources/{id}")
    public ResponseEntity<Void> removeResource(
            @PathVariable Long id,
            Authentication authentication) {

        service.remove(id, authentication.getName());

        return ResponseEntity.noContent().build();
    }
}
