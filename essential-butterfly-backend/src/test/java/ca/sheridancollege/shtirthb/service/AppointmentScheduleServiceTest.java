package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

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
 * Unit tests for AppointmentScheduleService.
 *
 * The administrator sets weekly (or one-date) time slots. This class turns
 * them into the list of open slots visitors see, and checks every rule the
 * administrator can change. It checks that:
 *   - A slot is hidden when it is too close to its start, in the past, or taken.
 *   - Only the booking window (7 days by default) is shown.
 *   - Slot length, start time, date and overlaps are validated with clear messages.
 *   - Nothing is shown when the Appointments feature is switched off.
 *
 * "Now" is fixed to Monday 16 November 2026, 09:00, so the tests never depend on the real date.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentScheduleServiceTest {

    private static final String ADMIN = "admin@mail.com";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 9, 0);

    @Mock private AppointmentAvailabilityRepository rules;
    @Mock private AppointmentSettingsRepository settingsRepository;
    @Mock private AppointmentRepository appointments;
    @Mock private FeatureVisibilityRepository features;
    @Mock private AuditTrailService auditTrail;
    @Mock private AppointmentClock clock;

    private AppProperties properties;
    private AppointmentSettings settings;
    private AppointmentScheduleService service;

    private long nextId = 1;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        settings = settings(0);

        when(clock.now()).thenReturn(NOW);
        when(settingsRepository.findById(AppointmentSettings.SINGLE_ROW_ID))
                .thenReturn(Optional.of(settings));
        when(settingsRepository.findForBooking()).thenReturn(Optional.of(settings));
        when(features.findByFeatureKey("APPOINTMENTS")).thenReturn(Optional.empty());
        when(rules.findAllByOrderByWeekdayAscStartTimeAsc()).thenReturn(List.of());
        when(appointments.findActiveBetween(any(), any())).thenReturn(List.of());
        when(rules.saveAndFlush(any(AppointmentAvailability.class)))
                .thenAnswer(call -> call.getArgument(0));

        service = new AppointmentScheduleService(
                rules, settingsRepository, appointments, features,
                auditTrail, clock, properties);
    }

    // ------------------------------------------------------------- helpers

    private static AppointmentSettings settings(int hideHours) {
        try {
            var constructor = AppointmentSettings.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            AppointmentSettings value = constructor.newInstance();
            ReflectionTestUtils.setField(value, "hideHoursBeforeStart", (short) hideHours);
            return value;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private AppointmentAvailability weekly(int weekday, int hour, int minute, int minutes) {
        AppointmentAvailability rule = new AppointmentAvailability(
                weekday, null, LocalTime.of(hour, minute), minutes, NOW);
        ReflectionTestUtils.setField(rule, "id", nextId++);
        return rule;
    }

    private AppointmentAvailability onDate(LocalDate date, int hour, int minute, int minutes) {
        AppointmentAvailability rule = new AppointmentAvailability(
                null, date, LocalTime.of(hour, minute), minutes, NOW);
        ReflectionTestUtils.setField(rule, "id", nextId++);
        return rule;
    }

    private void existing(AppointmentAvailability... list) {
        when(rules.findAllByOrderByWeekdayAscStartTimeAsc()).thenReturn(List.of(list));
    }

    private static AvailabilityRuleRequest weeklyRequest(Integer weekday, String start, Integer minutes) {
        return new AvailabilityRuleRequest(weekday, null, start, minutes);
    }

    private static AvailabilityRuleRequest dateRequest(String date, String start, Integer minutes) {
        return new AvailabilityRuleRequest(null, date, start, minutes);
    }

    private AppExceptions.ValidationException invalid(AvailabilityRuleRequest request) {
        return assertThrows(AppExceptions.ValidationException.class,
                () -> service.addRule(request, ADMIN));
    }

    // ------------------------------------------------------ feature switch

    @Test
    void featureIsOnWhenNoSettingExists() {
        assertTrue(service.isFeatureEnabled());
    }

    @Test
    void featureFollowsTheAdministratorsSwitch() {
        FeatureVisibility off = mock(FeatureVisibility.class);
        when(off.isEnabled()).thenReturn(false);
        when(features.findByFeatureKey("APPOINTMENTS")).thenReturn(Optional.of(off));

        assertFalse(service.isFeatureEnabled());
    }

    @Test
    void showsNoSlotsWhenTheFeatureIsOff() {
        FeatureVisibility off = mock(FeatureVisibility.class);
        when(off.isEnabled()).thenReturn(false);
        when(features.findByFeatureKey("APPOINTMENTS")).thenReturn(Optional.of(off));
        existing(weekly(1, 10, 0, 60));

        assertTrue(service.getOpenSlots().isEmpty());
    }

    // --------------------------------------------------------- open slots

    @Test
    void showsNothingWhenThereAreNoSlots() {
        assertTrue(service.getOpenSlots().isEmpty());
    }

    @Test
    void turnsAWeeklySlotIntoTheNextMatchingDay() {
        existing(weekly(1, 10, 0, 60));   // Monday 10:00

        List<AppointmentSlotResponse> slots = service.getOpenSlots();

        assertEquals(1, slots.size());
        assertEquals("2026-11-16T10:00", slots.get(0).startsAt());
        assertEquals("2026-11-16T11:00", slots.get(0).endsAt());
        assertEquals(60, slots.get(0).minutes());
    }

    @Test
    void listsSlotsEarliestFirst() {
        existing(weekly(3, 9, 0, 30), weekly(1, 15, 0, 45), weekly(1, 10, 0, 60));

        List<String> starts = service.getOpenSlots().stream()
                .map(AppointmentSlotResponse::startsAt).toList();

        assertEquals(List.of(
                "2026-11-16T10:00", "2026-11-16T15:00", "2026-11-18T09:00"), starts);
    }

    @Test
    void showsOnlyTheBookingWindow() {
        existing(weekly(1, 10, 0, 60));   // Mondays

        // 7 days: today is Monday, so next Monday is outside the window.
        assertEquals(1, service.getOpenSlots().size());

        properties.getAppointments().setBookingWindowDays(8);
        assertEquals(2, service.getOpenSlots().size());

        properties.getAppointments().setBookingWindowDays(14);
        assertEquals(2, service.getOpenSlots().size());
    }

    @Test
    void aWindowOfZeroDaysStillShowsToday() {
        properties.getAppointments().setBookingWindowDays(0);
        existing(weekly(1, 10, 0, 60));

        assertEquals(1, service.getOpenSlots().size());
    }

    @Test
    void hidesASlotThatHasAlreadyStarted() {
        existing(weekly(1, 8, 0, 60));   // it is 09:00 now

        assertTrue(service.getOpenSlots().isEmpty());
    }

    @Test
    void showsASlotThatStartsExactlyNowOrLater() {
        existing(weekly(1, 9, 0, 60), weekly(1, 9, 15, 30));

        assertEquals(2, service.getOpenSlots().size());
    }

    @Test
    void hidesSlotsTooCloseToTheirStart() {
        settings = settings(24);
        when(settingsRepository.findById(AppointmentSettings.SINGLE_ROW_ID))
                .thenReturn(Optional.of(settings));
        existing(weekly(1, 10, 0, 60), weekly(2, 8, 0, 60), weekly(2, 9, 0, 60));

        List<String> starts = service.getOpenSlots().stream()
                .map(AppointmentSlotResponse::startsAt).toList();

        // Hidden until 24 hours ahead: Monday 10:00 and Tuesday 08:00 are inside it.
        assertEquals(List.of("2026-11-17T09:00"), starts);
    }

    @Test
    void hidesASlotThatIsAlreadyRequestedOrAccepted() {
        existing(weekly(1, 10, 0, 60), weekly(1, 12, 0, 60));
        Appointment booked = new Appointment(null,
                LocalDateTime.of(2026, 11, 16, 10, 0),
                LocalDateTime.of(2026, 11, 16, 11, 0), NOW);
        when(appointments.findActiveBetween(any(), any())).thenReturn(List.of(booked));

        List<String> starts = service.getOpenSlots().stream()
                .map(AppointmentSlotResponse::startsAt).toList();

        assertEquals(List.of("2026-11-16T12:00"), starts);
    }

    @Test
    void hidesASlotThatOverlapsABooking() {
        existing(weekly(1, 10, 0, 60));
        Appointment booked = new Appointment(null,
                LocalDateTime.of(2026, 11, 16, 10, 30),
                LocalDateTime.of(2026, 11, 16, 11, 30), NOW);
        when(appointments.findActiveBetween(any(), any())).thenReturn(List.of(booked));

        assertTrue(service.getOpenSlots().isEmpty());
    }

    @Test
    void aBookingThatEndsWhenTheSlotStartsDoesNotBlockIt() {
        existing(weekly(1, 11, 0, 60));
        Appointment booked = new Appointment(null,
                LocalDateTime.of(2026, 11, 16, 10, 0),
                LocalDateTime.of(2026, 11, 16, 11, 0), NOW);
        when(appointments.findActiveBetween(any(), any())).thenReturn(List.of(booked));

        assertEquals(1, service.getOpenSlots().size());
    }

    @Test
    void showsAOneDateSlotOnItsDateOnly() {
        existing(onDate(LocalDate.of(2026, 11, 19), 13, 0, 60));

        List<AppointmentSlotResponse> slots = service.getOpenSlots();

        assertEquals(1, slots.size());
        assertEquals("2026-11-19T13:00", slots.get(0).startsAt());
    }

    @Test
    void hidesAOneDateSlotOutsideTheWindow() {
        existing(onDate(LocalDate.of(2026, 12, 25), 13, 0, 60));

        assertTrue(service.getOpenSlots().isEmpty());
    }

    @Test
    void listsASlotOnceWhenAWeeklyAndADateSlotStartTogether() {
        existing(weekly(4, 13, 0, 60), onDate(LocalDate.of(2026, 11, 19), 13, 0, 60));

        assertEquals(1, service.getOpenSlots().size());
    }

    @Test
    void asksForBookingsOfTheWholeWindow() {
        service.getOpenSlots();

        verify(appointments).findActiveBetween(
                LocalDateTime.of(2026, 11, 16, 0, 0),
                LocalDateTime.of(2026, 11, 23, 0, 0));
    }

    // ------------------------------------------------------ requireOpenSlot

    @Test
    void returnsTheSlotThatStartsExactlyWhenAsked() {
        existing(weekly(1, 10, 0, 60));

        AppointmentScheduleService.OpenSlot slot =
                service.requireOpenSlot(LocalDateTime.of(2026, 11, 16, 10, 0));

        assertEquals(LocalDateTime.of(2026, 11, 16, 10, 0), slot.startsAt());
        assertEquals(LocalDateTime.of(2026, 11, 16, 11, 0), slot.endsAt());
    }

    @Test
    void refusesATimeThatIsNotAnOpenSlot() {
        existing(weekly(1, 10, 0, 60));

        AppExceptions.ConflictException error = assertThrows(
                AppExceptions.ConflictException.class,
                () -> service.requireOpenSlot(LocalDateTime.of(2026, 11, 16, 10, 30)));

        assertEquals(AppointmentScheduleService.SLOT_UNAVAILABLE_MESSAGE, error.getMessage());
    }

    @Test
    void refusesATakenSlotWithTheSameMessage() {
        existing(weekly(1, 10, 0, 60));
        Appointment booked = new Appointment(null,
                LocalDateTime.of(2026, 11, 16, 10, 0),
                LocalDateTime.of(2026, 11, 16, 11, 0), NOW);
        when(appointments.findActiveBetween(any(), any())).thenReturn(List.of(booked));

        AppExceptions.ConflictException error = assertThrows(
                AppExceptions.ConflictException.class,
                () -> service.requireOpenSlot(LocalDateTime.of(2026, 11, 16, 10, 0)));

        assertEquals(AppointmentScheduleService.SLOT_UNAVAILABLE_MESSAGE, error.getMessage());
    }

    @Test
    void refusesATimeOutsideTheBookingWindow() {
        existing(weekly(1, 10, 0, 60));

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.requireOpenSlot(LocalDateTime.of(2026, 11, 23, 10, 0)));
    }

    // ----------------------------------------------------------- admin list

    @Test
    void listsWeeklySlotsBeforeOneDateSlotsAndHidesPastDates() {
        AppointmentAvailability tuesday = weekly(2, 14, 0, 60);
        AppointmentAvailability monday = weekly(1, 9, 30, 30);
        AppointmentAvailability later = onDate(LocalDate.of(2026, 11, 25), 10, 0, 60);
        AppointmentAvailability sooner = onDate(LocalDate.of(2026, 11, 18), 10, 0, 60);
        AppointmentAvailability past = onDate(LocalDate.of(2026, 11, 10), 10, 0, 60);
        AppointmentAvailability today = onDate(LocalDate.of(2026, 11, 16), 16, 0, 60);
        existing(tuesday, monday, later, sooner, past, today);

        List<AvailabilityRuleResponse> list = service.getRules();

        assertEquals(5, list.size());
        assertEquals(1, list.get(0).weekday());
        assertEquals(2, list.get(1).weekday());
        assertEquals("2026-11-16", list.get(2).date());
        assertEquals("2026-11-18", list.get(3).date());
        assertEquals("2026-11-25", list.get(4).date());
    }

    @Test
    void showsTheStartAndEndTimeOfASlot() {
        existing(weekly(1, 9, 30, 45));

        AvailabilityRuleResponse rule = service.getRules().get(0);

        assertEquals("09:30", rule.startTime());
        assertEquals("10:15", rule.endTime());
        assertEquals(45, rule.slotMinutes());
        assertNull(rule.date());
    }

    // -------------------------------------------------------------- add rule

    @Test
    void addsAWeeklySlotAndRecordsWhoDidIt() {
        AvailabilityRuleResponse added = service.addRule(weeklyRequest(3, "10:00", 60), ADMIN);

        assertEquals(3, added.weekday());
        assertEquals("10:00", added.startTime());
        assertEquals("11:00", added.endTime());
        verify(auditTrail).actAs(ADMIN);
        verify(rules).saveAndFlush(any(AppointmentAvailability.class));
    }

    @Test
    void addsAOneDateSlot() {
        AvailabilityRuleResponse added =
                service.addRule(dateRequest("2026-11-20", "13:00", 30), ADMIN);

        assertEquals("2026-11-20", added.date());
        assertNull(added.weekday());
    }

    @Test
    void acceptsSurroundingSpacesInTheDateAndTime() {
        AvailabilityRuleResponse added =
                service.addRule(dateRequest(" 2026-11-20 ", " 13:00 ", 30), ADMIN);

        assertEquals("2026-11-20", added.date());
        assertEquals("13:00", added.startTime());
    }

    @Test
    void aDateWinsWhenBothADateAndAWeekdayAreSent() {
        AvailabilityRuleResponse added = service.addRule(
                new AvailabilityRuleRequest(2, "2026-11-20", "13:00", 30), ADMIN);

        assertEquals("2026-11-20", added.date());
        assertNull(added.weekday());
    }

    @Test
    void refusesARequestWithNothingInIt() {
        assertEquals("Choose the day of the week or a date.", invalid(null).getMessage());
    }

    @Test
    void refusesAMissingOrOutOfRangeWeekday() {
        for (Integer weekday : new Integer[] {null, 0, 8, -1}) {
            assertEquals("Choose the day of the week or a date.",
                    invalid(weeklyRequest(weekday, "10:00", 60)).getMessage());
        }
    }

    @Test
    void refusesADateThatIsNotADate() {
        assertEquals("Enter the date, for example 2026-11-16.",
                invalid(dateRequest("20-11-2026", "10:00", 60)).getMessage());
    }

    @Test
    void refusesAnEmptyOrWrongStartTime() {
        for (String start : new String[] {null, "", "ten", "25:00", "10.00"}) {
            assertEquals("Enter the start time, for example 10:00.",
                    invalid(weeklyRequest(1, start, 60)).getMessage());
        }
    }

    @Test
    void refusesASlotLengthThatIsNotAllowed() {
        for (Integer minutes : new Integer[] {null, 0, 15, 20, 45 + 1, 135, 150}) {
            assertEquals(
                    "Choose a slot length from 30 minutes to 2 hours (in steps of 15 minutes).",
                    invalid(weeklyRequest(1, "10:00", minutes)).getMessage());
        }
    }

    @Test
    void acceptsEverySlotLengthFrom30To120MinutesInStepsOf15() {
        for (int minutes : new int[] {30, 45, 60, 75, 90, 105, 120}) {
            assertEquals(minutes,
                    service.addRule(weeklyRequest(1, "08:00", minutes), ADMIN).slotMinutes());
        }
    }

    @Test
    void refusesASlotThatRunsPastMidnight() {
        assertEquals("A slot must finish before midnight.",
                invalid(weeklyRequest(1, "23:30", 60)).getMessage());
    }

    @Test
    void acceptsASlotThatEndsExactlyAtMidnight() {
        AvailabilityRuleResponse added = service.addRule(weeklyRequest(1, "23:00", 60), ADMIN);

        assertEquals("00:00", added.endTime());
    }

    @Test
    void refusesADateAndTimeThatHavePassed() {
        assertEquals("Choose a time that has not passed yet.",
                invalid(dateRequest("2026-11-16", "09:00", 60)).getMessage());
        assertEquals("Choose a time that has not passed yet.",
                invalid(dateRequest("2026-11-10", "15:00", 60)).getMessage());
    }

    @Test
    void acceptsADateAndTimeOneMinuteFromNow() {
        service.addRule(dateRequest("2026-11-16", "09:01", 30), ADMIN);
    }

    @Test
    void doesNotSaveAnythingWhenARuleIsRefused() {
        invalid(weeklyRequest(9, "10:00", 60));

        verify(rules, never()).saveAndFlush(any(AppointmentAvailability.class));
        verify(auditTrail, never()).actAs(any());
    }

    // -------------------------------------------------------------- overlaps

    @Test
    void refusesAWeeklySlotThatOverlapsAnotherOnTheSameDay() {
        existing(weekly(1, 10, 0, 60));

        AppExceptions.ConflictException error = assertThrows(
                AppExceptions.ConflictException.class,
                () -> service.addRule(weeklyRequest(1, "10:30", 60), ADMIN));

        assertEquals("This time overlaps another slot on the same day.", error.getMessage());
    }

    @Test
    void acceptsSlotsThatTouchButDoNotOverlap() {
        existing(weekly(1, 10, 0, 60));

        service.addRule(weeklyRequest(1, "11:00", 60), ADMIN);
        service.addRule(weeklyRequest(1, "09:00", 60), ADMIN);
    }

    @Test
    void acceptsTheSameTimeOnAnotherWeekday() {
        existing(weekly(1, 10, 0, 60));

        service.addRule(weeklyRequest(2, "10:00", 60), ADMIN);
    }

    @Test
    void refusesAOneDateSlotThatOverlapsAWeeklySlotOnThatWeekday() {
        existing(weekly(5, 10, 0, 60));   // Fridays

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.addRule(dateRequest("2026-11-20", "10:30", 30), ADMIN));
    }

    @Test
    void refusesAOneDateSlotThatOverlapsAnotherOnTheSameDate() {
        existing(onDate(LocalDate.of(2026, 11, 20), 10, 0, 60));

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.addRule(dateRequest("2026-11-20", "10:15", 30), ADMIN));
    }

    @Test
    void acceptsAOneDateSlotAtTheSameTimeOnAnotherDate() {
        existing(onDate(LocalDate.of(2026, 11, 20), 10, 0, 60));

        service.addRule(dateRequest("2026-11-21", "10:00", 60), ADMIN);
    }

    @Test
    void aWeeklySlotIsNotBlockedByAOneDateSlot() {
        existing(onDate(LocalDate.of(2026, 11, 20), 10, 0, 60));

        service.addRule(weeklyRequest(5, "10:00", 60), ADMIN);
    }

    // ------------------------------------------------------------ change rule

    @Test
    void changesASlotAndRecordsWhoDidIt() {
        AppointmentAvailability rule = weekly(1, 10, 0, 60);
        existing(rule);
        when(rules.findById(rule.getId())).thenReturn(Optional.of(rule));

        AvailabilityRuleResponse changed = service.changeRule(
                rule.getId(), weeklyRequest(2, "14:00", 90), ADMIN);

        assertEquals(2, changed.weekday());
        assertEquals("14:00", changed.startTime());
        assertEquals("15:30", changed.endTime());
        verify(auditTrail).actAs(ADMIN);
    }

    @Test
    void aSlotDoesNotOverlapItself() {
        AppointmentAvailability rule = weekly(1, 10, 0, 60);
        existing(rule);
        when(rules.findById(rule.getId())).thenReturn(Optional.of(rule));

        AvailabilityRuleResponse changed = service.changeRule(
                rule.getId(), weeklyRequest(1, "10:15", 60), ADMIN);

        assertEquals("10:15", changed.startTime());
    }

    @Test
    void refusesToMoveASlotOntoAnotherSlot() {
        AppointmentAvailability first = weekly(1, 10, 0, 60);
        AppointmentAvailability second = weekly(1, 13, 0, 60);
        existing(first, second);
        when(rules.findById(second.getId())).thenReturn(Optional.of(second));

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.changeRule(second.getId(), weeklyRequest(1, "10:30", 60), ADMIN));
    }

    @Test
    void refusesToChangeASlotWithABadValue() {
        AppointmentAvailability rule = weekly(1, 10, 0, 60);
        when(rules.findById(rule.getId())).thenReturn(Optional.of(rule));

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.changeRule(rule.getId(), weeklyRequest(1, "10:00", 20), ADMIN));
    }

    @Test
    void reportsAnUnknownSlotWhenChanging() {
        when(rules.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.changeRule(99L, weeklyRequest(1, "10:00", 60), ADMIN));
    }

    // ------------------------------------------------------------ remove rule

    @Test
    void removesASlotAndRecordsWhoDidIt() {
        AppointmentAvailability rule = weekly(1, 10, 0, 60);
        when(rules.findById(rule.getId())).thenReturn(Optional.of(rule));

        service.removeRule(rule.getId(), ADMIN);

        verify(auditTrail).actAs(ADMIN);
        verify(rules).delete(rule);
        verify(rules).flush();
    }

    @Test
    void reportsAnUnknownSlotWhenRemoving() {
        when(rules.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.removeRule(99L, ADMIN));
        verify(rules, never()).delete(any(AppointmentAvailability.class));
    }

    // ---------------------------------------------------------------- settings

    @Test
    void showsTheSettings() {
        settings = settings(12);
        when(settingsRepository.findById(AppointmentSettings.SINGLE_ROW_ID))
                .thenReturn(Optional.of(settings));

        AppointmentSettingsResponse response = service.getSettings();

        assertEquals(12, response.hideHoursBeforeStart());
        assertEquals(7, response.bookingWindowDays());
        assertEquals("America/Toronto", response.timeZone());
    }

    @Test
    void showsAtLeastOneBookingDay() {
        properties.getAppointments().setBookingWindowDays(0);

        assertEquals(1, service.getSettings().bookingWindowDays());
    }

    @Test
    void reportsMissingSettings() {
        when(settingsRepository.findById(AppointmentSettings.SINGLE_ROW_ID))
                .thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> service.getSettings());
    }

    @Test
    void changesTheHideHours() {
        AppointmentSettingsResponse response = service.changeSettings(
                new AppointmentSettingsRequest(36), ADMIN);

        assertEquals(36, response.hideHoursBeforeStart());
        assertEquals(36, settings.getHideHoursBeforeStart());
        verify(auditTrail).actAs(ADMIN);
        verify(settingsRepository).saveAndFlush(settings);
    }

    @Test
    void acceptsZeroAnd168HideHours() {
        assertEquals(0, service.changeSettings(new AppointmentSettingsRequest(0), ADMIN)
                .hideHoursBeforeStart());
        assertEquals(168, service.changeSettings(new AppointmentSettingsRequest(168), ADMIN)
                .hideHoursBeforeStart());
    }

    @Test
    void refusesHideHoursOutsideZeroTo168() {
        for (AppointmentSettingsRequest request : new AppointmentSettingsRequest[] {
                null,
                new AppointmentSettingsRequest(null),
                new AppointmentSettingsRequest(-1),
                new AppointmentSettingsRequest(169)}) {

            AppExceptions.ValidationException error = assertThrows(
                    AppExceptions.ValidationException.class,
                    () -> service.changeSettings(request, ADMIN));

            assertEquals("Enter a whole number of hours from 0 to 168.", error.getMessage());
        }

        verify(settingsRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportsMissingSettingsWhenChanging() {
        when(settingsRepository.findForBooking()).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.changeSettings(new AppointmentSettingsRequest(5), ADMIN));
    }

    @Test
    void locksTheSettingsForBooking() {
        assertEquals(settings, service.lockForBooking());
    }

    @Test
    void reportsMissingSettingsWhenLocking() {
        when(settingsRepository.findForBooking()).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.lockForBooking());
    }
}