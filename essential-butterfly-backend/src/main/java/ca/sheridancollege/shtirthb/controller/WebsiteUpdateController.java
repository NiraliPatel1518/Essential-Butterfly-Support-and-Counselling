package ca.sheridancollege.shtirthb.controller;

import java.util.List; 

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ca.sheridancollege.shtirthb.dto.PublicUpdateSummaryResponse;
import ca.sheridancollege.shtirthb.dto.WebsiteUpdateRequest;
import ca.sheridancollege.shtirthb.dto.WebsiteUpdateResponse;
import ca.sheridancollege.shtirthb.service.WebsiteUpdateService;
import jakarta.validation.Valid;

@RestController
public class WebsiteUpdateController {

    private final WebsiteUpdateService service;

    public WebsiteUpdateController(WebsiteUpdateService service) {
        this.service = service;
    }

    // Public: only published titles and previews.
    @GetMapping("/api/updates")
    public ResponseEntity<List<PublicUpdateSummaryResponse>> getPublicUpdates() {
        return ResponseEntity.ok(service.getPublicUpdates());
    }

    // Authenticated client or admin: full published update.
    @GetMapping("/api/updates/{id}")
    public ResponseEntity<WebsiteUpdateResponse> getPublicUpdate(
            @PathVariable Long id) {
        return ResponseEntity.ok(service.getPublicUpdate(id));
    }

    // Admin only: list all updates, including unpublished ones.
    @GetMapping("/api/admin/updates")
    public ResponseEntity<List<WebsiteUpdateResponse>> getAdminUpdates() {
        return ResponseEntity.ok(service.getAllAdminUpdates());
    }

    @PostMapping("/api/admin/updates")
    public ResponseEntity<WebsiteUpdateResponse> createUpdate(
            @Valid @RequestBody WebsiteUpdateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.createUpdate(request));
    }

    @PutMapping("/api/admin/updates/{id}")
    public ResponseEntity<WebsiteUpdateResponse> editUpdate(
            @PathVariable Long id,
            @Valid @RequestBody WebsiteUpdateRequest request) {
        return ResponseEntity.ok(service.editUpdate(id, request));
    }

    @DeleteMapping("/api/admin/updates/{id}")
    public ResponseEntity<Void> deleteUpdate(@PathVariable Long id) {
        service.deleteUpdate(id);
        return ResponseEntity.noContent().build();
    }
}