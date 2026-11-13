package ca.sheridancollege.shtirthb.dto;

public record AppointmentFeedbackRequest(
    String feedback,
    String privateNotes
) {}
