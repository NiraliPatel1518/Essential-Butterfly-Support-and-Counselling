package ca.sheridancollege.shtirthb.dto;

import java.util.List;

// The photos that should be on the public wall. Everything else comes off.
public record MemoryWallSelectionRequest(List<Long> photoIds) {}
