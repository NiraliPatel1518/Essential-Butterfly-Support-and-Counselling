package ca.sheridancollege.shtirthb.dto;

public record AppointmentSettingsResponse(
    int hideHoursBeforeStart,
    int bookingWindowDays,
    String timeZone
) {}
