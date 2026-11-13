package ca.sheridancollege.shtirthb.dto;

import java.time.LocalDateTime;

public record ContentPageAdminResponse(
    String pageKey,
    String label,
    boolean published,
    boolean hasUnpublishedChanges,
    String publishedTitle,
    String publishedBody,
    String draftTitle,
    String draftBody,
    LocalDateTime publishedAt,
    LocalDateTime updatedAt
) {}
