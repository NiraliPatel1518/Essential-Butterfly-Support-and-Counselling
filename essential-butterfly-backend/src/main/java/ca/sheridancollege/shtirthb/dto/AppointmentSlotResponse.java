package ca.sheridancollege.shtirthb.dto;

// An open slot shown on the public website. Local clinic time, ISO format.
public record AppointmentSlotResponse(
    String startsAt,
    String endsAt,
    int minutes
) {}
