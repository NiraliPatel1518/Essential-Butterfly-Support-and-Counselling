package ca.sheridancollege.shtirthb.dto;

public record AuditLogResponse(
    Long id,
    String occurredAt,
    String action,
    String entityType,
    Long entityId,
    String actor,
    String details
) {}
