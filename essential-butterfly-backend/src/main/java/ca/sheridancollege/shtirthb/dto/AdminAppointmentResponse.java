package ca.sheridancollege.shtirthb.dto;

public record AdminAppointmentResponse(
    Long id,
    String status,
    String startsAt,
    String endsAt,
    String clientName,
    String clientEmail,
    String clientPhone,
    String statusReason,
    String meetingLink,
    String invitationSentAt,
    boolean calendarEventAdded,
    String feedback,
    String feedbackUpdatedAt,
    String privateNotes,
    String requestedAt,
    boolean started,
    boolean canCancel,
    boolean feedbackDue
) {}
