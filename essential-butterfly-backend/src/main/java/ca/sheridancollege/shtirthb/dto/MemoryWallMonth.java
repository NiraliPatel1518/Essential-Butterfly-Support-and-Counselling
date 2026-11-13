package ca.sheridancollege.shtirthb.dto;

import java.util.List;

// The public wall groups photos by the month and year they were uploaded.
public record MemoryWallMonth(
    String key,
    String label,
    List<MemoryWallPublicPhoto> photos
) {}
