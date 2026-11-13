package ca.sheridancollege.shtirthb.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LibraryResourcePublicResponse(
    Long id,
    String title,
    String description,
    String category,
    String resourceType,
    String linkUrl,
    List<String> links,
    String fileName,
    String fileContentType,
    Long fileSize,
    String altText,
    LocalDateTime publishedAt
) {}
