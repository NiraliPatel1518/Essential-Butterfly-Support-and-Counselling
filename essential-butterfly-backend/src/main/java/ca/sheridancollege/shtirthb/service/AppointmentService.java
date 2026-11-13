package ca.sheridancollege.shtirthb.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.dto.AdminAppointmentResponse;
import ca.sheridancollege.shtirthb.dto.AppointmentBookingRequest;
import ca.sheridancollege.shtirthb.dto.AppointmentFeedbackRequest;
import ca.sheridancollege.shtirthb.dto.ClientAppointmentResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.Appointment;
import ca.sheridancollege.shtirthb.model.AppointmentFeedback;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.AppointmentFeedbackRepository;
import ca.sheridancollege.shtirthb.repository.AppointmentRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

/*
 * Booking requests and the administrator's decisions. Everything here is one
 * database transaction per call; talking to Google Calendar or sending e-mail
 * happens afterwards in AppointmentWorkflowService, so a slow or failing
 * outside service can never leave a half-saved appointment behind.
 */
@Service
public class AppointmentService {

    private static final int REASON_MAX = 500;
    private static final int LINK_MAX = 2000;
    private static final int TEXT_MAX = 4000;
    private static final int SEARCH_MAX = 80;

    private static final Set<String> STATUSES = Set.of(
            Appointment.REQUESTED,
            Appointment.CONFIRMED,
            Appointment.DECLINED,
            Appointment.CANCELLED,
            Appointment.COMPLETED);

    private final AppointmentRepository appointments;
    private final AppointmentFeedbackRepository feedbacks;
    private final ClientUserRepository clients;
    private final AppointmentScheduleService schedule;
    private final AuditTrailService auditTrail;
    private final AppointmentClock clock;

    public AppointmentService(
            AppointmentRepository appointments,
            AppointmentFeedbackRepository feedbacks,
            ClientUserRepository clients,
            AppointmentScheduleService schedule,
            AuditTrailService auditTrail,
            AppointmentClock clock) {

        this.appointments = appointments;
        this.feedbacks = feedbacks;
        this.clients = clients;
        this.schedule = schedule;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    // ------------------------------------------------------------- client

    @Transactional
    public ClientAppointmentResponse request(
            String clientEmail,
            AppointmentBookingRequest request) {

        ClientUser client = clients.findByEmail(clientEmail)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Client account"));

        if (!client.isEmailVerified()) {
            throw new AppExceptions.EmailNotVerifiedException();
        }

        if (!schedule.isFeatureEnabled()) {
            throw new AppExceptions.BusinessRuleException(
                    "Appointment booking is not available right now.");
        }

        LocalDateTime startsAt = parseStart(request);

        // One booking at a time: the second of two simultaneous requests for
        // the same slot waits here, then finds the slot taken.
        schedule.lockForBooking();

        AppointmentScheduleService.OpenSlot slot =
                schedule.requireOpenSlot(startsAt);

        auditTrail.actAsClient(client.getId());

        Appointment saved = appointments.saveAndFlush(
                new Appointment(
                        client,
                        slot.startsAt(),
                        slot.endsAt(),
                        clock.now()));

        return toClientResponse(saved, null);
    }

    @Transactional
    public List<ClientAppointmentResponse> mine(String clientEmail) {

        ClientUser client = clients.findByEmail(clientEmail)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Client account"));

        appointments.completeFinished(clock.now());

        List<Appointment> own = appointments.findAllForClient(client.getId());

        Map<Long, AppointmentFeedback> feedbackById = feedbackFor(own);

        return own.stream()
                .map(a -> toClientResponse(a, feedbackById.get(a.getId())))
                .toList();
    }

    // -------------------------------------------------------------- admin

    @Transactional
    public List<AdminAppointmentResponse> adminList(
            String search,
            String status) {

        String wantedStatus = status == null
                ? ""
                : status.trim().toUpperCase(Locale.ROOT);

        if (!wantedStatus.isEmpty() && !STATUSES.contains(wantedStatus)) {
            throw new AppExceptions.ValidationException(
                    "Choose a valid appointment status.");
        }

        String text = search == null ? "" : search.trim();

        if (text.length() > SEARCH_MAX) {
            throw new AppExceptions.ValidationException(
                    "Search text is too long.");
        }

        LocalDateTime now = clock.now();

        appointments.completeFinished(now);

        List<Appointment> found = appointments.search(
                wantedStatus,
                text.isEmpty(),
                "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%");

        Map<Long, AppointmentFeedback> feedbackById = feedbackFor(found);

        return found.stream()
                .map(a -> toAdminResponse(a, feedbackById.get(a.getId()), now))
                .toList();
    }

    @Transactional
    public AdminAppointmentResponse confirm(Long id, String adminEmail) {

        Appointment appointment = find(id);
        LocalDateTime now = clock.now();

        requireStatus(appointment, Appointment.REQUESTED,
                "Only a requested appointment can be accepted.");

        if (!now.isBefore(appointment.getStartsAt())) {
            throw new AppExceptions.BusinessRuleException(
                    "This time has already started. Decline the request instead.");
        }

        auditTrail.actAs(adminEmail);

        appointment.confirm(now);
        appointments.saveAndFlush(appointment);

        return toAdminResponse(appointment, feedbackOf(id), now);
    }

    @Transactional
    public AdminAppointmentResponse decline(
            Long id,
            String reason,
            String adminEmail) {

        String cleanReason = cleanText(reason, REASON_MAX);

        if (cleanReason == null) {
            throw new AppExceptions.ValidationException(
                    "Enter a short reason for declining this request.");
        }

        Appointment appointment = find(id);
        LocalDateTime now = clock.now();

        requireStatus(appointment, Appointment.REQUESTED,
                "Only a requested appointment can be declined.");

        auditTrail.actAs(adminEmail);

        appointment.decline(cleanReason, now);
        appointments.saveAndFlush(appointment);

        return toAdminResponse(appointment, feedbackOf(id), now);
    }

    /*
     * An appointment can be cancelled at any time until it starts. The
     * calendar event that has to be removed is returned with the result.
     */
    @Transactional
    public CancelResult cancel(
            Long id,
            String reason,
            String adminEmail) {

        String cleanReason = cleanText(reason, REASON_MAX);

        Appointment appointment = find(id);
        LocalDateTime now = clock.now();

        if (!Appointment.REQUESTED.equals(appointment.getStatus())
                && !Appointment.CONFIRMED.equals(appointment.getStatus())) {

            throw new AppExceptions.BusinessRuleException(
                    "This appointment can no longer be cancelled.");
        }

        if (!now.isBefore(appointment.getStartsAt())) {
            throw new AppExceptions.BusinessRuleException(
                    "An appointment cannot be cancelled once it has started.");
        }

        String removedEventId = appointment.getGoogleEventId();

        auditTrail.actAs(adminEmail);

        appointment.cancel(cleanReason, now);
        appointments.saveAndFlush(appointment);

        return new CancelResult(
                toAdminResponse(appointment, feedbackOf(id), now),
                removedEventId);
    }

    public record CancelResult(
            AdminAppointmentResponse appointment,
            String removedEventId) {
    }

    // The link is saved first; e-mailing it is a separate step.
    @Transactional
    public AdminAppointmentResponse saveMeetingLink(
            Long id,
            String link,
            String adminEmail) {

        String cleanLink = requireHttpsLink(link);

        Appointment appointment = find(id);
        LocalDateTime now = clock.now();

        requireStatus(appointment, Appointment.CONFIRMED,
                "A meeting link can only be added to a confirmed appointment.");

        auditTrail.actAs(adminEmail);

        if (!cleanLink.equals(appointment.getMeetingLink())) {
            appointment.saveMeetingLink(cleanLink, now);
            appointments.saveAndFlush(appointment);
        }

        return toAdminResponse(appointment, feedbackOf(id), now);
    }

    @Transactional
    public AdminAppointmentResponse markInvitationSent(
            Long id,
            String adminEmail) {

        Appointment appointment = find(id);
        LocalDateTime now = clock.now();

        auditTrail.actAs(adminEmail);

        appointment.markInvitationSent(now);
        appointments.saveAndFlush(appointment);

        return toAdminResponse(appointment, feedbackOf(id), now);
    }

    @Transactional
    public AdminAppointmentResponse recordCalendarEvent(
            Long id,
            String eventId,
            String adminEmail) {

        Appointment appointment = find(id);
        LocalDateTime now = clock.now();

        auditTrail.actAs(adminEmail);

        appointment.saveGoogleEventId(eventId, now);
        appointments.saveAndFlush(appointment);

        return toAdminResponse(appointment, feedbackOf(id), now);
    }

    /*
     * Feedback (shown to the client) and private notes (administrator only)
     * can be written once the appointment has started, and changed later.
     */
    @Transactional
    public AdminAppointmentResponse saveFeedback(
            Long id,
            AppointmentFeedbackRequest request,
            String adminEmail) {

        /*
         * A field that is left out is kept as it is, so the Feedback popup
         * and the Private notes popup can each save on their own.
         */
        boolean hasFeedback = request != null && request.feedback() != null;
        boolean hasNotes = request != null && request.privateNotes() != null;

        if (!hasFeedback && !hasNotes) {
            throw new AppExceptions.ValidationException(
                    "Write some feedback or a private note before saving.");
        }

        String feedback = hasFeedback
                ? cleanText(request.feedback(), TEXT_MAX)
                : null;

        String privateNotes = hasNotes
                ? cleanText(request.privateNotes(), TEXT_MAX)
                : null;

        if (hasFeedback && feedback == null) {
            throw new AppExceptions.ValidationException(
                    "Write some feedback before saving.");
        }

        if (hasNotes && privateNotes == null) {
            throw new AppExceptions.ValidationException(
                    "Write a private note before saving.");
        }

        appointments.completeFinished(clock.now());

        Appointment appointment = find(id);
        LocalDateTime now = clock.now();

        boolean canWrite =
                (Appointment.CONFIRMED.equals(appointment.getStatus())
                        || Appointment.COMPLETED.equals(appointment.getStatus()))
                && !now.isBefore(appointment.getStartsAt());

        if (!canWrite) {
            throw new AppExceptions.BusinessRuleException(
                    "Feedback can be written once a confirmed appointment has started.");
        }

        auditTrail.actAs(adminEmail);

        AppointmentFeedback saved = feedbacks.findById(id)
                .map(existing -> {
                    existing.change(
                            hasFeedback ? feedback : existing.getFeedback(),
                            hasNotes ? privateNotes : existing.getPrivateNotes(),
                            now);
                    return existing;
                })
                .orElseGet(() -> new AppointmentFeedback(
                        id, feedback, privateNotes, now));

        feedbacks.saveAndFlush(saved);

        return toAdminResponse(appointment, saved, now);
    }

    // For the workflow: the current admin view of one appointment.
    @Transactional(readOnly = true)
    public AdminAppointmentResponse adminView(Long id) {

        return toAdminResponse(find(id), feedbackOf(id), clock.now());
    }

    // ------------------------------------------------------------ helpers

    private Appointment find(Long id) {

        return appointments.findById(id)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Appointment"));
    }

    private AppointmentFeedback feedbackOf(Long id) {
        return feedbacks.findById(id).orElse(null);
    }

    private Map<Long, AppointmentFeedback> feedbackFor(
            Collection<Appointment> list) {

        if (list.isEmpty()) {
            return Map.of();
        }

        return feedbacks.findAllByAppointmentIdIn(
                        list.stream().map(Appointment::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        AppointmentFeedback::getAppointmentId,
                        Function.identity()));
    }

    private void requireStatus(
            Appointment appointment,
            String expected,
            String message) {

        if (!expected.equals(appointment.getStatus())) {
            throw new AppExceptions.BusinessRuleException(message);
        }
    }

    private LocalDateTime parseStart(AppointmentBookingRequest request) {

        try {
            return LocalDateTime.parse(
                    request == null || request.startsAt() == null
                            ? ""
                            : request.startsAt().trim());
        } catch (DateTimeException ex) {
            throw new AppExceptions.ValidationException(
                    "Choose an appointment time from the list.");
        }
    }

    // Trimmed text, or null when empty. Too long text is refused, not cut.
    private String cleanText(String value, int max) {

        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.length() > max) {
            throw new AppExceptions.ValidationException(
                    "Please keep this under " + max + " characters.");
        }

        return trimmed;
    }

    private String requireHttpsLink(String link) {

        String trimmed = link == null ? "" : link.trim();

        if (trimmed.isEmpty() || trimmed.length() > LINK_MAX) {
            throw new AppExceptions.ValidationException(
                    "Paste the meeting link (https://…), up to "
                    + LINK_MAX + " characters.");
        }

        try {

            URI uri = new URI(trimmed);

            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isBlank()) {

                throw new AppExceptions.ValidationException(
                        "The meeting link must be a web address that starts with https://");
            }

        } catch (URISyntaxException ex) {
            throw new AppExceptions.ValidationException(
                    "The meeting link is not a valid web address.");
        }

        return trimmed;
    }

    // Search text is matched literally, never as a pattern.
    private String escapeLike(String text) {

        return text
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private ClientAppointmentResponse toClientResponse(
            Appointment a,
            AppointmentFeedback feedback) {

        return new ClientAppointmentResponse(
                a.getId(),
                a.getStatus(),
                a.getStartsAt().toString(),
                a.getEndsAt().toString(),
                a.getStatusReason(),
                a.getMeetingLink(),
                feedback == null ? null : feedback.getFeedback(),
                feedback == null || feedback.getFeedbackUpdatedAt() == null
                        ? null
                        : feedback.getFeedbackUpdatedAt().toString(),
                a.getRequestedAt().toString());
    }

    private AdminAppointmentResponse toAdminResponse(
            Appointment a,
            AppointmentFeedback feedback,
            LocalDateTime now) {

        ClientUser client = a.getClient();

        boolean started = !now.isBefore(a.getStartsAt());

        boolean cancellable =
                !started
                && (Appointment.REQUESTED.equals(a.getStatus())
                        || Appointment.CONFIRMED.equals(a.getStatus()));

        boolean feedbackDue =
                Appointment.COMPLETED.equals(a.getStatus())
                && (feedback == null || feedback.getFeedback() == null);

        return new AdminAppointmentResponse(
                a.getId(),
                a.getStatus(),
                a.getStartsAt().toString(),
                a.getEndsAt().toString(),
                client == null ? "Deleted account" : client.getFullName(),
                client == null ? null : client.getEmail(),
                client == null ? null : client.getPhoneNumber(),
                a.getStatusReason(),
                a.getMeetingLink(),
                a.getMeetingLinkSentAt() == null
                        ? null
                        : a.getMeetingLinkSentAt().toString(),
                a.getGoogleEventId() != null,
                feedback == null ? null : feedback.getFeedback(),
                feedback == null || feedback.getFeedbackUpdatedAt() == null
                        ? null
                        : feedback.getFeedbackUpdatedAt().toString(),
                feedback == null ? null : feedback.getPrivateNotes(),
                a.getRequestedAt().toString(),
                started,
                cancellable,
                feedbackDue);
    }
}
