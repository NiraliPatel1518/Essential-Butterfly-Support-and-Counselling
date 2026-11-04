package ca.sheridancollege.shtirthb.dto;

import java.time.LocalDateTime;
import java.util.List;

public record WebsiteUpdateResponse(
    Long id,
    String title,
    String content,
    boolean published,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<String> photos,
    List<String> socialLinks
) {}