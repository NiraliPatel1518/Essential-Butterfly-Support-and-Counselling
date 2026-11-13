package ca.sheridancollege.shtirthb.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import ca.sheridancollege.shtirthb.dto.AdminAppointmentResponse;
import ca.sheridancollege.shtirthb.dto.AppointmentActionResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;

/*
 * The administrator actions that touch an outside service after the database
 * change has been saved (Google Calendar, e-mail). It is deliberately not
 * transactional: the appointment is saved first and stays saved even if the
 * calendar or the mail server is down. The administrator is told in plain
 * words when a follow-up step still needs doing.
 */
@Service
public class AppointmentWorkflowService {

    static final String CALENDAR_NOT_CONNECTED =
            "The appointment is confirmed, but Google Calendar is not connected, "
            + "so no calendar event was created.";

    static final String CALENDAR_FAILED =
            "The appointment is confirmed, but it could not be added to Google "
            + "Calendar. Use \"Add to Google Calendar\" to try again. ";

    static final String CALENDAR_REMOVE_FAILED =
            "The appointment is cancelled, but its Google Calendar event could "
            + "not be removed. Please delete it in Google Calendar.";

    static final String EMAIL_FAILED =
            "The meeting link is saved, but the email could not be sent. "
            + "Use \"Send invitation\" to try again.";

    private final AppointmentService appointments;
    private final GoogleCalendarService calendar;
    private final AppointmentMailService mail;

    public AppointmentWorkflowService(
            AppointmentService appointments,
            GoogleCalendarService calendar,
            AppointmentMailService mail) {

        this.appointments = appointments;
        this.calendar = calendar;
        this.mail = mail;
    }

    public AppointmentActionResponse confirm(Long id, String adminEmail) {

        AdminAppointmentResponse confirmed =
                appointments.confirm(id, adminEmail);

        return addToCalendar(confirmed, adminEmail, CALENDAR_FAILED);
    }

    // Retry for an accepted appointment that has no calendar event yet.
    public AppointmentActionResponse addToCalendar(
            Long id,
            String adminEmail) {

        AdminAppointmentResponse current = appointments.adminView(id);

        if (!"CONFIRMED".equals(current.status())) {
            throw new AppExceptions.BusinessRuleException(
                    "Only a confirmed appointment can be added to the calendar.");
        }

        if (current.calendarEventAdded()) {
            return new AppointmentActionResponse(current, null);
        }

        return addToCalendar(current, adminEmail, CALENDAR_FAILED);
    }

    public AppointmentActionResponse decline(
            Long id,
            String reason,
            String adminEmail) {

        return new AppointmentActionResponse(
                appointments.decline(id, reason, adminEmail), null);
    }

    public AppointmentActionResponse cancel(
            Long id,
            String reason,
            String adminEmail) {

        AppointmentService.CancelResult result =
                appointments.cancel(id, reason, adminEmail);

        String removedEventId = result.removedEventId();

        boolean needsRemoval =
                removedEventId != null && !removedEventId.isBlank();

        if (needsRemoval && !calendar.deleteEvent(removedEventId)) {
            return new AppointmentActionResponse(
                    result.appointment(), CALENDAR_REMOVE_FAILED);
        }

        return new AppointmentActionResponse(result.appointment(), null);
    }

    // Saves the link, then e-mails it to the client.
    public AppointmentActionResponse sendInvitation(
            Long id,
            String link,
            String adminEmail) {

        AdminAppointmentResponse saved =
                appointments.saveMeetingLink(id, link, adminEmail);

        boolean sent = mail.sendInvitation(
                saved.clientName(),
                saved.clientEmail(),
                LocalDateTime.parse(saved.startsAt()),
                LocalDateTime.parse(saved.endsAt()),
                saved.meetingLink());

        if (!sent) {
            return new AppointmentActionResponse(saved, EMAIL_FAILED);
        }

        return new AppointmentActionResponse(
                appointments.markInvitationSent(id, adminEmail), null);
    }

    // Re-sends the link that is already saved.
    public AppointmentActionResponse resendInvitation(
            Long id,
            String adminEmail) {

        AdminAppointmentResponse current = appointments.adminView(id);

        if (current.meetingLink() == null) {
            throw new AppExceptions.BusinessRuleException(
                    "Add a meeting link first.");
        }

        return sendInvitation(id, current.meetingLink(), adminEmail);
    }

    private AppointmentActionResponse addToCalendar(
            AdminAppointmentResponse appointment,
            String adminEmail,
            String failureWarning) {

        if (!calendar.isConfigured()) {
            return new AppointmentActionResponse(
                    appointment, CALENDAR_NOT_CONNECTED);
        }

        GoogleCalendarService.CalendarResult result = calendar.createEvent(
                "Appointment: " + appointment.clientName(),
                describe(appointment),
                LocalDateTime.parse(appointment.startsAt()),
                LocalDateTime.parse(appointment.endsAt()));

        if (!result.added()) {
            return new AppointmentActionResponse(
                    appointment,
                    failureWarning + "Google said: " + result.problem());
        }

        return new AppointmentActionResponse(
                appointments.recordCalendarEvent(
                        appointment.id(), result.eventId(), adminEmail),
                null);
    }

    private String describe(AdminAppointmentResponse appointment) {

        StringBuilder text = new StringBuilder("Client: ")
                .append(appointment.clientName());

        if (appointment.clientEmail() != null) {
            text.append("\nEmail: ").append(appointment.clientEmail());
        }

        if (appointment.clientPhone() != null) {
            text.append("\nPhone: ").append(appointment.clientPhone());
        }

        return text.toString();
    }
}
