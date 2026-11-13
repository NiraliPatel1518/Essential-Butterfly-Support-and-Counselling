package ca.sheridancollege.shtirthb.controller;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ca.sheridancollege.shtirthb.dto.MemoryWallAdminOverview;
import ca.sheridancollege.shtirthb.dto.MemoryWallDescriptionRequest;
import ca.sheridancollege.shtirthb.dto.MemoryWallImage;
import ca.sheridancollege.shtirthb.dto.MemoryWallMonth;
import ca.sheridancollege.shtirthb.dto.MemoryWallPhotoAdminResponse;
import ca.sheridancollege.shtirthb.dto.MemoryWallSelectionRequest;
import ca.sheridancollege.shtirthb.service.MemoryWallService;

@RestController
public class MemoryWallController {

    private final MemoryWallService service;

    public MemoryWallController(MemoryWallService service) {
        this.service = service;
    }

    // ------------------------------------------------------------- public

    // Visitors: published photos only, grouped by upload month.
    @GetMapping("/api/memory-wall")
    public ResponseEntity<List<MemoryWallMonth>> getWall() {
        return ResponseEntity.ok(service.getPublicWall());
    }

    // Visitors: the picture of a published photo (anything else is 404).
    @GetMapping("/api/memory-wall/photos/{id}/image")
    public ResponseEntity<byte[]> getPublicImage(@PathVariable Long id) {

        MemoryWallImage image = service.getPublicImage(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60))
                        .cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }

    // -------------------------------------------------------------- admin

    // Every photo (newest first) with the limits and how much is used.
    @GetMapping("/api/admin/memory-wall")
    public ResponseEntity<MemoryWallAdminOverview> getOverview() {
        return ResponseEntity.ok(service.getOverview());
    }

    // The picture of any photo, for the administrator's page.
    @GetMapping("/api/admin/memory-wall/photos/{id}/image")
    public ResponseEntity<byte[]> getAdminImage(@PathVariable Long id) {

        MemoryWallImage image = service.getAdminImage(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }

    // One photo per request, so each file gets its own clear success or error.
    @PostMapping(
            value = "/api/admin/memory-wall/photos",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MemoryWallPhotoAdminResponse> uploadPhoto(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.upload(file, description, authentication.getName()));
    }

    @PutMapping("/api/admin/memory-wall/photos/{id}")
    public ResponseEntity<MemoryWallPhotoAdminResponse> changeDescription(
            @PathVariable Long id,
            @RequestBody MemoryWallDescriptionRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.changeDescription(id, request, authentication.getName()));
    }

    // Save changes: these photos are public, every other photo is not.
    @PutMapping("/api/admin/memory-wall/selection")
    public ResponseEntity<MemoryWallAdminOverview> saveSelection(
            @RequestBody MemoryWallSelectionRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.saveSelection(request, authentication.getName()));
    }

    @DeleteMapping("/api/admin/memory-wall/photos/{id}")
    public ResponseEntity<Void> deletePhoto(
            @PathVariable Long id,
            Authentication authentication) {

        service.delete(id, authentication.getName());

        return ResponseEntity.noContent().build();
    }
}
