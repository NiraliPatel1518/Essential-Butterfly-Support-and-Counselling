package ca.sheridancollege.shtirthb.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.config.AppProperties;
import ca.sheridancollege.shtirthb.dto.AppointmentSettingsRequest;
import ca.sheridancollege.shtirthb.dto.AppointmentSettingsResponse;
import ca.sheridancollege.shtirthb.dto.AppointmentSlotResponse;
import ca.sheridancollege.shtirthb.dto.AvailabilityRuleRequest;
import ca.sheridancollege.shtirthb.dto.AvailabilityRuleResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.Appointment;
import ca.sheridancollege.shtirthb.model.AppointmentAvailability;
import ca.sheridancollege.shtirthb.model.AppointmentSettings;
import ca.sheridancollege.shtirthb.model.FeatureVisibility;
import ca.sheridancollege.shtirthb.repository.AppointmentAvailabilityRepository;
import ca.sheridancollege.shtirthb.repository.AppointmentRepository;
import ca.sheridancollege.shtirthb.repository.AppointmentSettingsRepository;
import ca.sheridancollege.shtirthb.repository.FeatureVisibilityRepository;

/*
 * The administrator's weekly availability, the "hide before start" setting,
 * and the list of open slots the public website shows.
 */
@Service
public class AppointmentScheduleService {

    public static final String FEATURE_KEY = "APPOINTMENTS";

    public static final String SLOT_UNAVAILABLE_MESSAGE =
            "This appointment time is no longer available. Please select another time.";

    private static final DateTimeFormatter HOUR_MINUTE =
            DateTimeFormatter.ofPattern("HH:mm");

    private static final int MIN_SLOT_MINUTES = 30;
    private static final int MAX_SLOT_MINUTES = 120;
    private static final int SLOT_STEP_MINUTES = 15;
    private static final int MAX_HIDE_HOURS = 168;

    private final AppointmentAvailabilityRepository rules;
    private final AppointmentSettingsRepository settings;
    private final AppointmentRepository appointments;
    private final FeatureVisibilityRepository features;
    private final AuditTrailService auditTrail;
    private final AppointmentClock clock;
    private final AppProperties properties;

    public AppointmentScheduleService(
            AppointmentAvailabilityRepository rules,
            AppointmentSettingsRepository settings,
            AppointmentRepository appointments,
            FeatureVisibilityRepository features,
            AuditTrailService auditTrail,
            AppointmentClock clock,
            AppProperties properties) {

        this.rules = rules;
        this.settings = settings;
        this.appointments = appointments;
        this.features = features;
        this.auditTrail = auditTrail;
        this.clock = clock;
        this.properties = properties;
    }

    // ------------------------------------------------------------ public

    // True unless an administrator switched the Appointments feature off.
    @Transactional(readOnly = true)
    public boolean isFeatureEnabled() {

        return features.findByFeatureKey(FEATURE_KEY)
                .map(FeatureVisibility::isEnabled)
                .orElse(true);
    }

    /*
     * Open slots for the next days (today included), earliest first. A slot is
     * left out when it is too close to its start, or already requested or
     * accepted. Nothing about who booked it is ever exposed.
     */
    @Transactional(readOnly = true)
    public List<AppointmentSlotResponse> getOpenSlots() {

        if (!isFeatureEnabled()) {
            return List.of();
        }

        return generateSlots().stream()
                .map(slot -> new AppointmentSlotResponse(
                        slot.startsAt().toString(),
                        slot.endsAt().toString(),
                        slot.minutes()))
                .toList();
    }

    /*
     * The slot that starts exactly at startsAt, if it is open right now.
     * Anything else (no such weekly slot, outside the published days, too
     * close to the start, already taken) gives the same friendly message.
     */
    @Transactional(readOnly = true)
    public OpenSlot requireOpenSlot(LocalDateTime startsAt) {

        return generateSlots().stream()
                .filter(slot -> slot.startsAt().equals(startsAt))
                .findFirst()
                .orElseThrow(() ->
                        new AppExceptions.ConflictException(
                                SLOT_UNAVAILABLE_MESSAGE));
    }

    public record OpenSlot(LocalDateTime startsAt, LocalDateTime endsAt) {

        int minutes() {
            return (int) java.time.Duration.between(startsAt, endsAt).toMinutes();
        }
    }

    private List<OpenSlot> generateSlots() {

        LocalDateTime now = clock.now();
        int hideHours = currentSettings().getHideHoursBeforeStart();
        LocalDateTime earliest = now.plusHours(hideHours);

        int windowDays = Math.max(
                1, properties.getAppointments().getBookingWindowDays());

        LocalDate firstDay = now.toLocalDate();
        LocalDate lastDay = firstDay.plusDays(windowDays - 1L);

        List<AppointmentAvailability> weekly =
                rules.findAllByOrderByWeekdayAscStartTimeAsc();

        List<Appointment> taken = appointments.findActiveBetween(
                firstDay.atStartOfDay(),
                lastDay.plusDays(1).atStartOfDay());

        List<OpenSlot> open = new ArrayList<>();

        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {

            for (AppointmentAvailability rule : weekly) {

                boolean applies = rule.getSlotDate() == null
                        ? rule.getWeekday() == day.getDayOfWeek().getValue()
                        : rule.getSlotDate().equals(day);

                if (!applies) {
                    continue;
                }

                LocalDateTime start = day.atTime(rule.getStartTime());
                LocalDateTime end = start.plusMinutes(rule.getSlotMinutes());

                if (start.isBefore(earliest)) {
                    continue;
                }

                boolean isTaken = taken.stream().anyMatch(booked ->
                        booked.getStartsAt().isBefore(end)
                        && booked.getEndsAt().isAfter(start));

                boolean listed = open.stream()
                        .anyMatch(slot -> slot.startsAt().equals(start));

                if (!isTaken && !listed) {
                    open.add(new OpenSlot(start, end));
                }
            }
        }

        open.sort(Comparator.comparing(OpenSlot::startsAt));

        return open;
    }

    // -------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<AvailabilityRuleResponse> getRules() {

        LocalDate today = clock.now().toLocalDate();

        // Weekly slots first, then upcoming one-date slots by date.
        return rules.findAllByOrderByWeekdayAscStartTimeAsc()
                .stream()
                .filter(rule -> rule.getSlotDate() == null
                        || !rule.getSlotDate().isBefore(today))
                .sorted(Comparator
                        .comparing((AppointmentAvailability rule) ->
                                rule.getSlotDate() != null)
                        .thenComparing(rule -> rule.getSlotDate() == null
                                ? LocalDate.MIN
                                : rule.getSlotDate())
                        .thenComparing(rule -> rule.getWeekday() == null
                                ? 0
                                : rule.getWeekday())
                        .thenComparing(AppointmentAvailability::getStartTime))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AvailabilityRuleResponse addRule(
            AvailabilityRuleRequest request,
            String adminEmail) {

        ParsedRule parsed = parse(request);

        requireNoOverlap(parsed, null);

        auditTrail.actAs(adminEmail);

        AppointmentAvailability saved = rules.saveAndFlush(
                new AppointmentAvailability(
                        parsed.weekday(),
                        parsed.date(),
                        parsed.startTime(),
                        parsed.slotMinutes(),
                        clock.now()));

        return toResponse(saved);
    }

    @Transactional
    public AvailabilityRuleResponse changeRule(
            Long id,
            AvailabilityRuleRequest request,
            String adminEmail) {

        AppointmentAvailability rule = findRule(id);

        ParsedRule parsed = parse(request);

        requireNoOverlap(parsed, id);

        auditTrail.actAs(adminEmail);

        rule.change(
                parsed.weekday(),
                parsed.date(),
                parsed.startTime(),
                parsed.slotMinutes(),
                clock.now());

        rules.saveAndFlush(rule);

        return toResponse(rule);
    }

    /*
     * Removing a weekly slot stops it appearing on the website from now on.
     * Requests already made for it are not touched.
     */
    @Transactional
    public void removeRule(Long id, String adminEmail) {

        AppointmentAvailability rule = findRule(id);

        auditTrail.actAs(adminEmail);

        rules.delete(rule);
        rules.flush();
    }

    @Transactional(readOnly = true)
    public AppointmentSettingsResponse getSettings() {

        return toResponse(currentSettings());
    }

    @Transactional
    public AppointmentSettingsResponse changeSettings(
            AppointmentSettingsRequest request,
            String adminEmail) {

        Integer hours = request == null
                ? null
                : request.hideHoursBeforeStart();

        if (hours == null || hours < 0 || hours > MAX_HIDE_HOURS) {
            throw new AppExceptions.ValidationException(
                    "Enter a whole number of hours from 0 to "
                    + MAX_HIDE_HOURS + ".");
        }

        AppointmentSettings current = settings.findForBooking()
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Appointment settings"));

        auditTrail.actAs(adminEmail);

        current.changeHideHours(hours, clock.now());
        settings.saveAndFlush(current);

        return toResponse(current);
    }

    // The settings row, locked so concurrent bookings queue up one by one.
    @Transactional
    public AppointmentSettings lockForBooking() {

        return settings.findForBooking()
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Appointment settings"));
    }

    // ------------------------------------------------------------ helpers

    private AppointmentSettings currentSettings() {

        return settings.findById(AppointmentSettings.SINGLE_ROW_ID)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Appointment settings"));
    }

    private AppointmentAvailability findRule(Long id) {

        return rules.findById(id)
                .orElseThrow(() ->
                        new AppExceptions.ResourceNotFoundException(
                                "Availability slot"));
    }

    /*
     * weekday is set for a weekly slot, date for a one-date slot (never both).
     */
    private record ParsedRule(
            Integer weekday,
            LocalDate date,
            LocalTime startTime,
            int slotMinutes) {

        int startMinute() {
            return startTime.getHour() * 60 + startTime.getMinute();
        }
    }

    private ParsedRule parse(AvailabilityRuleRequest request) {

        if (request == null) {
            throw new AppExceptions.ValidationException(
                    "Choose the day of the week or a date.");
        }

        LocalDate date = null;
        Integer weekday = null;

        if (request.date() != null && !request.date().isBlank()) {

            try {
                date = LocalDate.parse(request.date().trim());
            } catch (DateTimeException ex) {
                throw new AppExceptions.ValidationException(
                        "Enter the date, for example 2026-11-16.");
            }

        } else {

            if (request.weekday() == null
                    || request.weekday() < 1 || request.weekday() > 7) {

                throw new AppExceptions.ValidationException(
                        "Choose the day of the week or a date.");
            }

            weekday = request.weekday();
        }

        LocalTime start;

        try {
            start = LocalTime.parse(
                    request.startTime() == null
                            ? ""
                            : request.startTime().trim());
        } catch (DateTimeException ex) {
            throw new AppExceptions.ValidationException(
                    "Enter the start time, for example 10:00.");
        }

        Integer minutes = request.slotMinutes();

        if (minutes == null
                || minutes < MIN_SLOT_MINUTES
                || minutes > MAX_SLOT_MINUTES
                || minutes % SLOT_STEP_MINUTES != 0) {

            throw new AppExceptions.ValidationException(
                    "Choose a slot length from 30 minutes to 2 hours "
                    + "(in steps of 15 minutes).");
        }

        ParsedRule parsed = new ParsedRule(
                weekday,
                date,
                start.withSecond(0).withNano(0),
                minutes);

        if (parsed.startMinute() + minutes > 24 * 60) {
            throw new AppExceptions.ValidationException(
                    "A slot must finish before midnight.");
        }

        // A one-date slot can start any time from the next minute on.
        if (date != null && !date.atTime(parsed.startTime()).isAfter(clock.now())) {
            throw new AppExceptions.ValidationException(
                    "Choose a time that has not passed yet.");
        }

        return parsed;
    }

    // Two slots on the same day may not overlap (the database also enforces
    // this between two weekly slots and between two one-date slots).
    private void requireNoOverlap(ParsedRule candidate, Long ignoreId) {

        int start = candidate.startMinute();
        int end = start + candidate.slotMinutes();

        int candidateWeekday = candidate.date() == null
                ? candidate.weekday()
                : candidate.date().getDayOfWeek().getValue();

        boolean overlaps = rules.findAllByOrderByWeekdayAscStartTimeAsc()
                .stream()
                .filter(rule -> !rule.getId().equals(ignoreId))
                .filter(rule -> {
                    if (candidate.date() != null) {
                        // same date, or a weekly slot on that weekday
                        return rule.getSlotDate() == null
                                ? rule.getWeekday() == candidateWeekday
                                : rule.getSlotDate().equals(candidate.date());
                    }
                    // weekly candidate: other weekly slots on the same weekday
                    return rule.getSlotDate() == null
                            && rule.getWeekday() == candidateWeekday;
                })
                .anyMatch(rule -> {
                    int otherStart = rule.getStartTime().getHour() * 60
                            + rule.getStartTime().getMinute();
                    int otherEnd = otherStart + rule.getSlotMinutes();
                    return otherStart < end && otherEnd > start;
                });

        if (overlaps) {
            throw new AppExceptions.ConflictException(
                    "This time overlaps another slot on the same day.");
        }
    }

    private AvailabilityRuleResponse toResponse(AppointmentAvailability rule) {

        return new AvailabilityRuleResponse(
                rule.getId(),
                rule.getWeekday(),
                rule.getSlotDate() == null
                        ? null
                        : rule.getSlotDate().toString(),
                rule.getStartTime().format(HOUR_MINUTE),
                rule.getStartTime()
                        .plusMinutes(rule.getSlotMinutes())
                        .format(HOUR_MINUTE),
                rule.getSlotMinutes());
    }

    private AppointmentSettingsResponse toResponse(AppointmentSettings value) {

        return new AppointmentSettingsResponse(
                value.getHideHoursBeforeStart(),
                Math.max(1, properties.getAppointments().getBookingWindowDays()),
                properties.getAppointments().getTimeZone());
    }
}
