package ca.sheridancollege.shtirthb.dto;

import java.util.List;

public record MemoryWallAdminOverview(
    List<MemoryWallPhotoAdminResponse> photos,
    MemoryWallSummaryResponse summary
) {}
