package ca.sheridancollege.shtirthb.dto;

// A weekly slot sends weekday (1 = Monday ... 7 = Sunday); a one-date slot
// sends date ("2026-11-16") instead. startTime is "HH:mm".
public record AvailabilityRuleRequest(
    Integer weekday,
    String date,
    String startTime,
    Integer slotMinutes
) {}
