package ca.sheridancollege.shtirthb.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ca.sheridancollege.shtirthb.dto.FeatureVisibilityRequest;
import ca.sheridancollege.shtirthb.dto.FeatureVisibilityResponse;
import ca.sheridancollege.shtirthb.service.FeatureVisibilityService;
import jakarta.validation.Valid;

@RestController
public class FeatureVisibilityController {

    private final FeatureVisibilityService service;

    public FeatureVisibilityController(FeatureVisibilityService service) {
        this.service = service;
    }

    @GetMapping("/api/features")
    public ResponseEntity<List<FeatureVisibilityResponse>> getPublicFeatures() {
        return ResponseEntity.ok(service.getFeatures());
    }

    @GetMapping("/api/admin/features")
    public ResponseEntity<List<FeatureVisibilityResponse>> getAdminFeatures() {
        return ResponseEntity.ok(service.getFeatures());
    }

    @PutMapping("/api/admin/features")
    public ResponseEntity<List<FeatureVisibilityResponse>> updateFeatures(
            @Valid @RequestBody
            List<@Valid FeatureVisibilityRequest> requests) {

        return ResponseEntity.ok(service.updateFeatures(requests));
    }
}