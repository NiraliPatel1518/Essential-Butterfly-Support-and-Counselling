package ca.sheridancollege.shtirthb.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ca.sheridancollege.shtirthb.dto.ContentPageAdminResponse;
import ca.sheridancollege.shtirthb.dto.ContentPagePublicResponse;
import ca.sheridancollege.shtirthb.dto.ContentPageRequest;
import ca.sheridancollege.shtirthb.service.ContentPageService;

@RestController
public class ContentPageController {

    private final ContentPageService service;

    public ContentPageController(ContentPageService service) {
        this.service = service;
    }

    // Public: the published copy, or 204 when the built-in page is still in use.
    @GetMapping("/api/content/{pageKey}")
    public ResponseEntity<ContentPagePublicResponse> getPublished(
            @PathVariable String pageKey) {

        return service.getPublished(pageKey)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // Admin only: every editable page with its draft and published copies.
    @GetMapping("/api/admin/content")
    public ResponseEntity<List<ContentPageAdminResponse>> getAdminPages() {
        return ResponseEntity.ok(service.getAdminPages());
    }

    @GetMapping("/api/admin/content/{pageKey}")
    public ResponseEntity<ContentPageAdminResponse> getAdminPage(
            @PathVariable String pageKey) {

        return ResponseEntity.ok(service.getAdminPage(pageKey));
    }

    @PutMapping("/api/admin/content/{pageKey}")
    public ResponseEntity<ContentPageAdminResponse> savePage(
            @PathVariable String pageKey,
            @RequestBody ContentPageRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                service.save(pageKey, request, authentication.getName())
        );
    }
}
