package ca.sheridancollege.shtirthb.dto;

import java.util.List;

// The limits and how much of them is used, so the page never hard-codes them.
public record MemoryWallSummaryResponse(
    int totalPhotos,
    int maxPhotos,
    int remainingUploads,
    int publishedPhotos,
    int maxPublishedPhotos,
    long storageUsedBytes,
    long maxFileSizeBytes,
    String maxFileSizeLabel,
    List<String> allowedExtensions,
    String acceptedFormats
) {}
