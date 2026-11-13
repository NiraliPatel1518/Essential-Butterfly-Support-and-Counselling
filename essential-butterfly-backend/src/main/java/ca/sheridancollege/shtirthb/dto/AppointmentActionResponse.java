package ca.sheridancollege.shtirthb.dto;

// The updated appointment plus, when a follow-up step could not be done
// (calendar or e-mail), a plain explanation for the administrator.
public record AppointmentActionResponse(
    AdminAppointmentResponse appointment,
    String warning
) {}
