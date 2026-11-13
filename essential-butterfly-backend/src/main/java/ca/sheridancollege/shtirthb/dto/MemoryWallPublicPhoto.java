package ca.sheridancollege.shtirthb.dto;

// One photo on the public Memory Wall. The picture itself is fetched from
// /api/memory-wall/photos/{id}/image.
public record MemoryWallPublicPhoto(
    Long id,
    String altText,
    String uploadedAt,
    String uploadedOn
) {}
