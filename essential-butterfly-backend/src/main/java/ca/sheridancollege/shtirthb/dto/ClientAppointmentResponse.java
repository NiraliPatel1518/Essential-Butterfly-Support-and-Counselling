package ca.sheridancollege.shtirthb.dto;

// What a client may see about their own booking. Private notes are never here.
public record ClientAppointmentResponse(
    Long id,
    String status,
    String startsAt,
    String endsAt,
    String statusReason,
    String meetingLink,
    String feedback,
    String feedbackUpdatedAt,
    String requestedAt
) {}
