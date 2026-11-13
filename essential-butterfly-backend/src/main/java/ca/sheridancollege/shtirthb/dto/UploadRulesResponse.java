package ca.sheridancollege.shtirthb.dto;

import java.util.List;

public record UploadRulesResponse(
    long maxFileSizeBytes,
    String maxFileSizeLabel,
    List<String> allowedExtensions,
    String acceptedFormats
) {}
