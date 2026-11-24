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

    /*
     * Checks: The appointments feature is ON when no setting exists.
     * How:    Mockito fakes, with the clock fixed on Monday 2026-11-16. The feature repository returns
     *   nothing. isFeatureEnabled() is true.
     */
    @Test
    void featureIsOnWhenNoSettingExists() {
        assertTrue(service.isFeatureEnabled());
    }

    /*
     * Checks: The feature follows the admin's switch.
     * How:    A switch set to off makes isFeatureEnabled() false.
     */
    @Test
    void featureFollowsTheAdministratorsSwitch() {
        FeatureVisibility off = mock(FeatureVisibility.class);
        when(off.isEnabled()).thenReturn(false);
        when(features.findByFeatureKey("APPOINTMENTS")).thenReturn(Optional.of(off));

        assertFalse(service.isFeatureEnabled());
    }

    /*
     * Checks: No slots are shown when the feature is off.
     * How:    getOpenSlots() is empty.
     */
    @Test
    void showsNoSlotsWhenTheFeatureIsOff() {
        FeatureVisibility off = mock(FeatureVisibility.class);
        when(off.isEnabled()).thenReturn(false);
        when(features.findByFeatureKey("APPOINTMENTS")).thenReturn(Optional.of(off));
        existing(weekly(1, 10, 0, 60));

        assertTrue(service.getOpenSlots().isEmpty());
    }

    // --------------------------------------------------------- open slots

    /*
     * Checks: Nothing is shown when there are no slot rules.
     * How:    The rules list is empty, so getOpenSlots() is empty.
     */
    @Test
    void showsNothingWhenThereAreNoSlots() {
        assertTrue(service.getOpenSlots().isEmpty());
    }

    /*
     * Checks: A weekly slot becomes the next matching day.
     * How:    A Monday 10:00 rule for 60 minutes gives one slot on 2026-11-16 from 10:00 to 11:00 with
     *   60 minutes.
     */
    @Test
    void turnsAWeeklySlotIntoTheNextMatchingDay() {
        existing(weekly(1, 10, 0, 60));   // Monday 10:00

        List<AppointmentSlotResponse> slots = service.getOpenSlots();

        assertEquals(1, slots.size());
        assertEquals("2026-11-16T10:00", slots.get(0).startsAt());
        assertEquals("2026-11-16T11:00", slots.get(0).endsAt());
        assertEquals(60, slots.get(0).minutes());
    }

    /*
     * Checks: Slots are listed earliest first.
     * How:    Several rules in a mixed order come out sorted by start time.
     */
    @Test
    void listsSlotsEarliestFirst() {
        existing(weekly(3, 9, 0, 30), weekly(1, 15, 0, 45), weekly(1, 10, 0, 60));

        List<String> starts = service.getOpenSlots().stream()
                .map(AppointmentSlotResponse::startsAt).toList();

        assertEquals(List.of(
                "2026-11-16T10:00", "2026-11-16T15:00", "2026-11-18T09:00"), starts);
    }

    /*
     * Checks: Only slots inside the booking window are shown.
     * How:    With a 7-day window only today's Monday shows. With 8 and 14 days the next Monday shows
     *   too (2 slots).
     */
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

    /*
     * Checks: A window of 0 days still shows today.
     * How:    bookingWindowDays 0 still gives 1 slot.
     */
    @Test
    void aWindowOfZeroDaysStillShowsToday() {
        properties.getAppointments().setBookingWindowDays(0);
        existing(weekly(1, 10, 0, 60));

        assertEquals(1, service.getOpenSlots().size());
    }

    /*
     * Checks: A slot that has already started is hidden.
     * How:    The slot time is before the fixed "now". The list is empty.
     */
    @Test
    void hidesASlotThatHasAlreadyStarted() {
        existing(weekly(1, 8, 0, 60));   // it is 09:00 now

        assertTrue(service.getOpenSlots().isEmpty());
    }

    /*
     * Checks: A slot that starts exactly now or later is shown.
     * How:    Two slots at and after "now" are both listed.
     */
    @Test
    void showsASlotThatStartsExactlyNowOrLater() {
        existing(weekly(1, 9, 0, 60), weekly(1, 9, 15, 30));

        assertEquals(2, service.getOpenSlots().size());
    }

    /*
     * Checks: Slots too close to their start are hidden.
     * How:    With 24 hide-hours, Monday 10:00 and Tuesday 08:00 are hidden. Only Tuesday 09:00 is
     *   left.
     */
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

    /*
     * Checks: A slot that is already requested or accepted is hidden.
     * How:    An active appointment exists at 10:00. Only the 12:00 slot is listed.
     */
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

    /*
     * Checks: A slot that overlaps a booking is hidden.
     * How:    A booking from 10:30 to 11:30 hides the 10:00-11:00 slot.
     */
    @Test
    void hidesASlotThatOverlapsABooking() {
        existing(weekly(1, 10, 0, 60));
        Appointment booked = new Appointment(null,
                LocalDateTime.of(2026, 11, 16, 10, 30),
                LocalDateTime.of(2026, 11, 16, 11, 30), NOW);
        when(appointments.findActiveBetween(any(), any())).thenReturn(List.of(booked));

        assertTrue(service.getOpenSlots().isEmpty());
    }

    /*
     * Checks: A booking that ends when a slot starts does not block it.
     * How:    A booking from 10:00 to 11:00 and a slot at 11:00 give 1 open slot.
     */
    @Test
    void aBookingThatEndsWhenTheSlotStartsDoesNotBlockIt() {
        existing(weekly(1, 11, 0, 60));
        Appointment booked = new Appointment(null,
                LocalDateTime.of(2026, 11, 16, 10, 0),
                LocalDateTime.of(2026, 11, 16, 11, 0), NOW);
        when(appointments.findActiveBetween(any(), any())).thenReturn(List.of(booked));

        assertEquals(1, service.getOpenSlots().size());
    }

    /*
     * Checks: A one-date slot shows only on its date.
     * How:    The slot rule for 2026-11-19 gives one slot at 13:00 on that date.
     */
    @Test
    void showsAOneDateSlotOnItsDateOnly() {
        existing(onDate(LocalDate.of(2026, 11, 19), 13, 0, 60));

        List<AppointmentSlotResponse> slots = service.getOpenSlots();

        assertEquals(1, slots.size());
        assertEquals("2026-11-19T13:00", slots.get(0).startsAt());
    }

    /*
     * Checks: A one-date slot outside the window is hidden.
     * How:    A date after the window gives an empty list.
     */
    @Test
    void hidesAOneDateSlotOutsideTheWindow() {
        existing(onDate(LocalDate.of(2026, 12, 25), 13, 0, 60));

        assertTrue(service.getOpenSlots().isEmpty());
    }

    /*
     * Checks: A slot is listed once when a weekly rule and a date rule give the same time.
     * How:    The result has 1 slot, not 2.
     */
    @Test
    void listsASlotOnceWhenAWeeklyAndADateSlotStartTogether() {
        existing(weekly(4, 13, 0, 60), onDate(LocalDate.of(2026, 11, 19), 13, 0, 60));

        assertEquals(1, service.getOpenSlots().size());
    }

    /*
     * Checks: The service asks for the bookings of the whole window.
     * How:    findActiveBetween is called from 2026-11-16 00:00 to 2026-11-23 00:00.
     */
    @Test
    void asksForBookingsOfTheWholeWindow() {
        service.getOpenSlots();

        verify(appointments).findActiveBetween(
                LocalDateTime.of(2026, 11, 16, 0, 0),
                LocalDateTime.of(2026, 11, 23, 0, 0));
    }

    // ------------------------------------------------------ requireOpenSlot

    /*
     * Checks: requireOpenSlot returns the slot that starts at the asked time.
     * How:    For Monday 10:00 we get a slot from 10:00 to 11:00.
     */
    @Test
    void returnsTheSlotThatStartsExactlyWhenAsked() {
        existing(weekly(1, 10, 0, 60));

        AppointmentScheduleService.OpenSlot slot =
                service.requireOpenSlot(LocalDateTime.of(2026, 11, 16, 10, 0));

        assertEquals(LocalDateTime.of(2026, 11, 16, 10, 0), slot.startsAt());
        assertEquals(LocalDateTime.of(2026, 11, 16, 11, 0), slot.endsAt());
    }

    /*
     * Checks: A time that is not an open slot is refused.
     * How:    It throws ConflictException with SLOT_UNAVAILABLE_MESSAGE.
     */
    @Test
    void refusesATimeThatIsNotAnOpenSlot() {
        existing(weekly(1, 10, 0, 60));

        AppExceptions.ConflictException error = assertThrows(
                AppExceptions.ConflictException.class,
                () -> service.requireOpenSlot(LocalDateTime.of(2026, 11, 16, 10, 30)));

        assertEquals(AppointmentScheduleService.SLOT_UNAVAILABLE_MESSAGE, error.getMessage());
    }

    /*
     * Checks: A slot that is taken is refused with the same message.
     * How:    A booked slot throws ConflictException with SLOT_UNAVAILABLE_MESSAGE, so visitors cannot
     *   tell why.
     */
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

    /*
     * Checks: A time outside the booking window is refused.
     * How:    It throws ConflictException.
     */
    @Test
    void refusesATimeOutsideTheBookingWindow() {
        existing(weekly(1, 10, 0, 60));

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.requireOpenSlot(LocalDateTime.of(2026, 11, 23, 10, 0)));
    }

    // ----------------------------------------------------------- admin list

    /*
     * Checks: The admin list shows weekly slots first, then one-date slots, and hides past dates.
     * How:    We give 2 weekly, 3 future/today date slots and 1 past date. The list has 5 items, in
     *   the order Monday, Tuesday, 2026-11-16, 2026-11-18, 2026-11-25.
     */
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

    /*
     * Checks: A slot rule shows its start time, end time and length.
     * How:    09:30 for 45 minutes gives start 09:30, end 10:15, slotMinutes 45, and no date.
     */
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

    /*
     * Checks: Adding a weekly slot works and records the admin.
     * How:    The new rule has weekday 3 and the right start and end times. The audit trail records
     *   the admin.
     */
    @Test
    void addsAWeeklySlotAndRecordsWhoDidIt() {
        AvailabilityRuleResponse added = service.addRule(weeklyRequest(3, "10:00", 60), ADMIN);

        assertEquals(3, added.weekday());
        assertEquals("10:00", added.startTime());
        assertEquals("11:00", added.endTime());
        verify(auditTrail).actAs(ADMIN);
        verify(rules).saveAndFlush(any(AppointmentAvailability.class));
    }

    /*
     * Checks: Adding a one-date slot works.
     * How:    The new rule has the date and no weekday.
     */
    @Test
    void addsAOneDateSlot() {
        AvailabilityRuleResponse added =
                service.addRule(dateRequest("2026-11-20", "13:00", 30), ADMIN);

        assertEquals("2026-11-20", added.date());
        assertNull(added.weekday());
    }

    /*
     * Checks: Spaces around the date and time are accepted.
     * How:    The saved date and start time are clean.
     */
    @Test
    void acceptsSurroundingSpacesInTheDateAndTime() {
        AvailabilityRuleResponse added =
                service.addRule(dateRequest(" 2026-11-20 ", " 13:00 ", 30), ADMIN);

        assertEquals("2026-11-20", added.date());
        assertEquals("13:00", added.startTime());
    }

    /*
     * Checks: The date wins when both a date and a weekday are sent.
     * How:    The rule has the date and no weekday.
     */
    @Test
    void aDateWinsWhenBothADateAndAWeekdayAreSent() {
        AvailabilityRuleResponse added = service.addRule(
                new AvailabilityRuleRequest(2, "2026-11-20", "13:00", 30), ADMIN);

        assertEquals("2026-11-20", added.date());
        assertNull(added.weekday());
    }

    /*
     * Checks: An empty request is refused.
     * How:    The message is "Choose the day of the week or a date."
     */
    @Test
    void refusesARequestWithNothingInIt() {
        assertEquals("Choose the day of the week or a date.", invalid(null).getMessage());
    }

    /*
     * Checks: A missing or out-of-range weekday is refused.
     * How:    null, 0, 8 and -1 all give "Choose the day of the week or a date."
     */
    @Test
    void refusesAMissingOrOutOfRangeWeekday() {
        for (Integer weekday : new Integer[] {null, 0, 8, -1}) {
            assertEquals("Choose the day of the week or a date.",
                    invalid(weeklyRequest(weekday, "10:00", 60)).getMessage());
        }
    }

    /*
     * Checks: A date that is not a date is refused.
     * How:    "20-11-2026" gives "Enter the date, for example 2026-11-16."
     */
    @Test
    void refusesADateThatIsNotADate() {
        assertEquals("Enter the date, for example 2026-11-16.",
                invalid(dateRequest("20-11-2026", "10:00", 60)).getMessage());
    }

    /*
     * Checks: An empty or wrong start time is refused.
     * How:    null, empty, "ten", "25:00" and "10.00" give "Enter the start time, for example 10:00."
     */
    @Test
    void refusesAnEmptyOrWrongStartTime() {
        for (String start : new String[] {null, "", "ten", "25:00", "10.00"}) {
            assertEquals("Enter the start time, for example 10:00.",
                    invalid(weeklyRequest(1, start, 60)).getMessage());
        }
    }

    /*
     * Checks: A slot length that is not allowed is refused.
     * How:    null, 0, 15, 20, 46, 135 and 150 minutes give "Choose a slot length from 30 minutes to 2
     *   hours..."
     */
    @Test
    void refusesASlotLengthThatIsNotAllowed() {
        for (Integer minutes : new Integer[] {null, 0, 15, 20, 45 + 1, 135, 150}) {
            assertEquals(
                    "Choose a slot length from 30 minutes to 2 hours (in steps of 15 minutes).",
                    invalid(weeklyRequest(1, "10:00", minutes)).getMessage());
        }
    }

    /*
     * Checks: Every slot length from 30 to 120 minutes in steps of 15 is accepted.
     * How:    A loop tries 30, 45, 60, 75, 90, 105 and 120. Each returns the same length.
     */
    @Test
    void acceptsEverySlotLengthFrom30To120MinutesInStepsOf15() {
        for (int minutes : new int[] {30, 45, 60, 75, 90, 105, 120}) {
            assertEquals(minutes,
                    service.addRule(weeklyRequest(1, "08:00", minutes), ADMIN).slotMinutes());
        }
    }

    /*
     * Checks: A slot that runs past midnight is refused.
     * How:    The message is "A slot must finish before midnight."
     */
    @Test
    void refusesASlotThatRunsPastMidnight() {
        assertEquals("A slot must finish before midnight.",
                invalid(weeklyRequest(1, "23:30", 60)).getMessage());
    }

    /*
     * Checks: A slot that ends exactly at midnight is accepted.
     * How:    The end time is 00:00.
     */
    @Test
    void acceptsASlotThatEndsExactlyAtMidnight() {
        AvailabilityRuleResponse added = service.addRule(weeklyRequest(1, "23:00", 60), ADMIN);

        assertEquals("00:00", added.endTime());
    }

    /*
     * Checks: A date and time that have passed are refused.
     * How:    The message is "Choose a time that has not passed yet."
     */
    @Test
    void refusesADateAndTimeThatHavePassed() {
        assertEquals("Choose a time that has not passed yet.",
                invalid(dateRequest("2026-11-16", "09:00", 60)).getMessage());
        assertEquals("Choose a time that has not passed yet.",
                invalid(dateRequest("2026-11-10", "15:00", 60)).getMessage());
    }

    /*
     * Checks: A date and time one minute from now are accepted.
     * How:    addRule does not throw.
     */
    @Test
    void acceptsADateAndTimeOneMinuteFromNow() {
        service.addRule(dateRequest("2026-11-16", "09:01", 30), ADMIN);
    }

    /*
     * Checks: Nothing is saved when a rule is refused.
     * How:    saveAndFlush is never called and the admin is not recorded.
     */
    @Test
    void doesNotSaveAnythingWhenARuleIsRefused() {
        invalid(weeklyRequest(9, "10:00", 60));

        verify(rules, never()).saveAndFlush(any(AppointmentAvailability.class));
        verify(auditTrail, never()).actAs(any());
    }

    // -------------------------------------------------------------- overlaps

    /*
     * Checks: A weekly slot that overlaps another on the same day is refused.
     * How:    It throws ConflictException: "This time overlaps another slot on the same day."
     */
    @Test
    void refusesAWeeklySlotThatOverlapsAnotherOnTheSameDay() {
        existing(weekly(1, 10, 0, 60));

        AppExceptions.ConflictException error = assertThrows(
                AppExceptions.ConflictException.class,
                () -> service.addRule(weeklyRequest(1, "10:30", 60), ADMIN));

        assertEquals("This time overlaps another slot on the same day.", error.getMessage());
    }

    /*
     * Checks: Slots that only touch are accepted.
     * How:    One ends when the next starts. It does not throw.
     */
    @Test
    void acceptsSlotsThatTouchButDoNotOverlap() {
        existing(weekly(1, 10, 0, 60));

        service.addRule(weeklyRequest(1, "11:00", 60), ADMIN);
        service.addRule(weeklyRequest(1, "09:00", 60), ADMIN);
    }

    /*
     * Checks: The same time on another weekday is accepted.
     * How:    It does not throw.
     */
    @Test
    void acceptsTheSameTimeOnAnotherWeekday() {
        existing(weekly(1, 10, 0, 60));

        service.addRule(weeklyRequest(2, "10:00", 60), ADMIN);
    }

    /*
     * Checks: A one-date slot that overlaps a weekly slot on that weekday is refused.
     * How:    It throws ConflictException.
     */
    @Test
    void refusesAOneDateSlotThatOverlapsAWeeklySlotOnThatWeekday() {
        existing(weekly(5, 10, 0, 60));   // Fridays

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.addRule(dateRequest("2026-11-20", "10:30", 30), ADMIN));
    }

    /*
     * Checks: A one-date slot that overlaps another on the same date is refused.
     * How:    It throws ConflictException.
     */
    @Test
    void refusesAOneDateSlotThatOverlapsAnotherOnTheSameDate() {
        existing(onDate(LocalDate.of(2026, 11, 20), 10, 0, 60));

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.addRule(dateRequest("2026-11-20", "10:15", 30), ADMIN));
    }

    /*
     * Checks: A one-date slot at the same time on another date is accepted.
     * How:    It does not throw.
     */
    @Test
    void acceptsAOneDateSlotAtTheSameTimeOnAnotherDate() {
        existing(onDate(LocalDate.of(2026, 11, 20), 10, 0, 60));

        service.addRule(dateRequest("2026-11-21", "10:00", 60), ADMIN);
    }

    /*
     * Checks: A weekly slot is not blocked by a one-date slot.
     * How:    Adding a weekly slot next to an existing date slot does not throw.
     */
    @Test
    void aWeeklySlotIsNotBlockedByAOneDateSlot() {
        existing(onDate(LocalDate.of(2026, 11, 20), 10, 0, 60));

        service.addRule(weeklyRequest(5, "10:00", 60), ADMIN);
    }

    // ------------------------------------------------------------ change rule

    /*
     * Checks: Changing a slot works and records the admin.
     * How:    The changed rule has the new weekday and times, and the admin is recorded.
     */
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

    /*
     * Checks: A slot does not count as overlapping itself.
     * How:    Changing the rule by 15 minutes works.
     */
    @Test
    void aSlotDoesNotOverlapItself() {
        AppointmentAvailability rule = weekly(1, 10, 0, 60);
        existing(rule);
        when(rules.findById(rule.getId())).thenReturn(Optional.of(rule));

        AvailabilityRuleResponse changed = service.changeRule(
                rule.getId(), weeklyRequest(1, "10:15", 60), ADMIN);

        assertEquals("10:15", changed.startTime());
    }

    /*
     * Checks: A slot cannot be moved onto another slot.
     * How:    It throws ConflictException.
     */
    @Test
    void refusesToMoveASlotOntoAnotherSlot() {
        AppointmentAvailability first = weekly(1, 10, 0, 60);
        AppointmentAvailability second = weekly(1, 13, 0, 60);
        existing(first, second);
        when(rules.findById(second.getId())).thenReturn(Optional.of(second));

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.changeRule(second.getId(), weeklyRequest(1, "10:30", 60), ADMIN));
    }

    /*
     * Checks: A change with a bad value is refused.
     * How:    It throws ValidationException.
     */
    @Test
    void refusesToChangeASlotWithABadValue() {
        AppointmentAvailability rule = weekly(1, 10, 0, 60);
        when(rules.findById(rule.getId())).thenReturn(Optional.of(rule));

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.changeRule(rule.getId(), weeklyRequest(1, "10:00", 20), ADMIN));
    }

    /*
     * Checks: Changing an unknown slot gives not found.
     * How:    It throws ResourceNotFoundException.
     */
    @Test
    void reportsAnUnknownSlotWhenChanging() {
        when(rules.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.changeRule(99L, weeklyRequest(1, "10:00", 60), ADMIN));
    }

    // ------------------------------------------------------------ remove rule

    /*
     * Checks: Removing a slot works and records the admin.
     * How:    The rule is deleted and flushed, and the admin is recorded.
     */
    @Test
    void removesASlotAndRecordsWhoDidIt() {
        AppointmentAvailability rule = weekly(1, 10, 0, 60);
        when(rules.findById(rule.getId())).thenReturn(Optional.of(rule));

        service.removeRule(rule.getId(), ADMIN);

        verify(auditTrail).actAs(ADMIN);
        verify(rules).delete(rule);
        verify(rules).flush();
    }

    /*
     * Checks: Removing an unknown slot gives not found.
     * How:    It throws ResourceNotFoundException and nothing is deleted.
     */
    @Test
    void reportsAnUnknownSlotWhenRemoving() {
        when(rules.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.removeRule(99L, ADMIN));
        verify(rules, never()).delete(any(AppointmentAvailability.class));
    }

    // ---------------------------------------------------------------- settings

    /*
     * Checks: The settings are shown.
     * How:    Hide hours 12, booking window 7 days and time zone America/Toronto.
     */
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

    /*
     * Checks: The booking window is shown as at least 1 day.
     * How:    With the window set to 0, the settings show 1.
     */
    @Test
    void showsAtLeastOneBookingDay() {
        properties.getAppointments().setBookingWindowDays(0);

        assertEquals(1, service.getSettings().bookingWindowDays());
    }

    /*
     * Checks: Missing settings give not found.
     * How:    It throws ResourceNotFoundException.
     */
    @Test
    void reportsMissingSettings() {
        when(settingsRepository.findById(AppointmentSettings.SINGLE_ROW_ID))
                .thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class, () -> service.getSettings());
    }

    /*
     * Checks: The hide hours can be changed.
     * How:    We set 36. The response and the entity show 36, the admin is recorded and the settings
     *   are saved.
     */
    @Test
    void changesTheHideHours() {
        AppointmentSettingsResponse response = service.changeSettings(
                new AppointmentSettingsRequest(36), ADMIN);

        assertEquals(36, response.hideHoursBeforeStart());
        assertEquals(36, settings.getHideHoursBeforeStart());
        verify(auditTrail).actAs(ADMIN);
        verify(settingsRepository).saveAndFlush(settings);
    }

    /*
     * Checks: 0 and 168 hide hours are accepted.
     * How:    Both limits are saved as given.
     */
    @Test
    void acceptsZeroAnd168HideHours() {
        assertEquals(0, service.changeSettings(new AppointmentSettingsRequest(0), ADMIN)
                .hideHoursBeforeStart());
        assertEquals(168, service.changeSettings(new AppointmentSettingsRequest(168), ADMIN)
                .hideHoursBeforeStart());
    }

    /*
     * Checks: Hide hours outside 0 to 168 are refused.
     * How:    Values outside the range throw ValidationException with "Enter a whole number of hours
     *   from 0 to 168." Nothing is saved.
     */
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

    /*
     * Checks: Changing missing settings gives not found.
     * How:    It throws ResourceNotFoundException.
     */
    @Test
    void reportsMissingSettingsWhenChanging() {
        when(settingsRepository.findForBooking()).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.changeSettings(new AppointmentSettingsRequest(5), ADMIN));
    }

    /*
     * Checks: The settings can be locked for booking.
     * How:    lockForBooking() returns the settings row (used so two bookings cannot happen at the
     *   same moment).
     */
    @Test
    void locksTheSettingsForBooking() {
        assertEquals(settings, service.lockForBooking());
    }

    /*
     * Checks: Locking missing settings gives not found.
     * How:    It throws ResourceNotFoundException.
     */
    @Test
    void reportsMissingSettingsWhenLocking() {
        when(settingsRepository.findForBooking()).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.lockForBooking());
    }
}