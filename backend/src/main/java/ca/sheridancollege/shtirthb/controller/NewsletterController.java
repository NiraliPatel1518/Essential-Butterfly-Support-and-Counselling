package ca.sheridancollege.shtirthb.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ca.sheridancollege.shtirthb.service.NewsletterService;

@RestController
public class NewsletterController {

    private final NewsletterService newsletterService;

    public NewsletterController(NewsletterService newsletterService) {
        this.newsletterService = newsletterService;
    }

    // PUBLIC: visitor subscribes without an account
    @PostMapping("/api/newsletter/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody Map<String, Object> body) {
        String email = body.get("email") == null
                ? ""
                : body.get("email").toString().trim();

        boolean consent = Boolean.TRUE.equals(body.get("consent"));

        if (email.isBlank() || !email.matches(
                "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Enter a valid email address."));
        }

        if (!consent) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Please provide consent to subscribe."));
        }

        return ResponseEntity.ok(newsletterService.subscribe(email, true));
    }

    // PUBLIC: unsubscribe link from an email
    @GetMapping("/api/newsletter/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestParam String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Unsubscribe token is required."));
        }

        return ResponseEntity.ok(newsletterService.unsubscribe(token));
    }

    // ADMIN: overview and active subscriber count
    @GetMapping("/api/admin/newsletter")
    public ResponseEntity<?> overview() {
        return ResponseEntity.ok(newsletterService.getAdminOverview());
    }

    // ADMIN: create a draft
    @PostMapping("/api/admin/newsletter")
    public ResponseEntity<?> createDraft(@RequestBody Map<String, String> body) {
        String subject = body.getOrDefault("subject", "").trim();
        String content = body.getOrDefault("content", "").trim();

        if (subject.isBlank() || content.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Subject and content are required."));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(newsletterService.createCampaign(subject, content));
    }

    // ADMIN: update a draft
    @PutMapping("/api/admin/newsletter/{id}")
    public ResponseEntity<?> updateDraft(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String subject = body.getOrDefault("subject", "").trim();
        String content = body.getOrDefault("content", "").trim();

        if (subject.isBlank() || content.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Subject and content are required."));
        }

        return ResponseEntity.ok(
                newsletterService.updateCampaign(id, subject, content));
    }

    // ADMIN: preview before sending
    @GetMapping("/api/admin/newsletter/{id}/preview")
    public ResponseEntity<?> preview(@PathVariable Long id) {
        return ResponseEntity.ok(newsletterService.preview(id));
    }

    // ADMIN: send to active subscribers
    @PostMapping("/api/admin/newsletter/{id}/send")
    public ResponseEntity<?> send(@PathVariable Long id) {
        return ResponseEntity.ok(newsletterService.send(id));
    }

    // ADMIN: retry only failed deliveries
    @PostMapping("/api/admin/newsletter/{id}/retry-failed")
    public ResponseEntity<?> retryFailed(@PathVariable Long id) {
        return ResponseEntity.ok(newsletterService.retryFailed(id));
    }

    // ADMIN: individual delivery results
    @GetMapping("/api/admin/newsletter/{id}/deliveries")
    public ResponseEntity<?> deliveries(@PathVariable Long id) {
        return ResponseEntity.ok(newsletterService.getCampaignDetails(id));
    }
}