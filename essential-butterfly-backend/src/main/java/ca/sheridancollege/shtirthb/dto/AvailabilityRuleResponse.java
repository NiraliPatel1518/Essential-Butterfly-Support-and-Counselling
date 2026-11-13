package ca.sheridancollege.shtirthb.dto;

// weekday is set for a weekly slot, date for a one-date slot.
public record AvailabilityRuleResponse(
    Long id,
    Integer weekday,
    String date,
    String startTime,
    String endTime,
    int slotMinutes
) {}
