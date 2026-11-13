package ca.sheridancollege.shtirthb.dto;

public record MemoryWallPhotoAdminResponse(
    Long id,
    String status,
    String source,
    String uploadedBy,
    String uploadedAt,
    String publishedAt,
    String fileName,
    String contentType,
    long sizeBytes,
    String description,
    String altText,
    String testimonialStatus,
    boolean canPublish,
    String publishBlockedReason
) {}
