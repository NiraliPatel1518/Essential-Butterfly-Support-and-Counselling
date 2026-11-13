package ca.sheridancollege.shtirthb.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ca.sheridancollege.shtirthb.dto.AuditLogResponse;
import ca.sheridancollege.shtirthb.service.AuditTrailService;

@RestController
public class AuditLogController {

    private final AuditTrailService auditTrail;

    public AuditLogController(AuditTrailService auditTrail) {
        this.auditTrail = auditTrail;
    }

    // Admin only: recent content-management activity.
    @GetMapping("/api/admin/audit-log")
    public ResponseEntity<List<AuditLogResponse>> getRecentActivity(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        return ResponseEntity.ok(auditTrail.recentContentActivity(limit));
    }
}
