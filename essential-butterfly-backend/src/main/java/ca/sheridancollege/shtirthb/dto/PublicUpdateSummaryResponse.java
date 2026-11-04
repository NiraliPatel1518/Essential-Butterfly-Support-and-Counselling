package ca.sheridancollege.shtirthb.dto;

import java.time.LocalDateTime;

public record PublicUpdateSummaryResponse(
    Long id,
    String title,
    String preview,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String coverImage
) {}