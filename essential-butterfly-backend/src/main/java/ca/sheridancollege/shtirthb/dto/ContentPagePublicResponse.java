package ca.sheridancollege.shtirthb.dto;

import java.time.LocalDateTime;

public record ContentPagePublicResponse(
    String pageKey,
    String title,
    String body,
    LocalDateTime publishedAt
) {}
