package ca.sheridancollege.shtirthb.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LibraryResourceAdminResponse(
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
    String status,
    LocalDateTime publishedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
