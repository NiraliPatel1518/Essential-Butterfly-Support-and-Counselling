package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import ca.sheridancollege.shtirthb.config.AppProperties;

class AppointmentClockTest {

    private static AppointmentClock clockFor(String zone) {
        AppProperties properties = new AppProperties();
        properties.getAppointments().setTimeZone(zone);
        return new AppointmentClock(properties);
    }

    /*
     * Checks: The clock uses the time zone from the settings.
     * How:    We build the clock with America/Toronto and Asia/Kolkata and check zone() each time.
     */
    @Test void usesTheClinicTimeZoneFromTheSettings() {
        assertEquals(ZoneId.of("America/Toronto"), clockFor("America/Toronto").zone());
        assertEquals(ZoneId.of("Asia/Kolkata"), clockFor("Asia/Kolkata").zone());
    }

    /*
     * Checks: The default time zone is Toronto.
     * How:    A clock built from empty AppProperties must have the zone America/Toronto.
     */
    @Test void defaultsToToronto() {
        assertEquals(ZoneId.of("America/Toronto"), new AppointmentClock(new AppProperties()).zone());
    }

    /*
     * Checks: A bad time zone name is refused with a clear message.
     * How:    We use "Not/AZone". It must throw IllegalStateException with the message
     *   "app.appointments.time-zone is not a valid time zone."
     */
    @Test void refusesAnInvalidTimeZoneWithAClearMessage() {
        var thrown = assertThrows(IllegalStateException.class, () -> clockFor("Not/AZone"));

        assertEquals("app.appointments.time-zone is not a valid time zone.", thrown.getMessage());
    }

    /*
     * Checks: now() is in the clinic zone and has no nanoseconds.
     * How:    We take Toronto time before and after the call, with 2 seconds of room. now() must be
     *   between them and getNano() must be 0.
     */
    @Test void nowIsInTheClinicZoneAndHasNoNanoseconds() {
        AppointmentClock clock = clockFor("America/Toronto");

        LocalDateTime before = LocalDateTime.now(ZoneId.of("America/Toronto")).minusSeconds(2);
        LocalDateTime now = clock.now();
        LocalDateTime after = LocalDateTime.now(ZoneId.of("America/Toronto")).plusSeconds(2);

        assertEquals(0, now.getNano());
        assertFalse(now.isBefore(before));
        assertFalse(now.isAfter(after));
    }

    /*
     * Checks: Different time zones give different clock times.
     * How:    Toronto and Tokyo clocks must show a different hour.
     */
    @Test void twoZonesGiveDifferentWallClockTimes() {
        LocalDateTime toronto = clockFor("America/Toronto").now();
        LocalDateTime tokyo = clockFor("Asia/Tokyo").now();

        assertNotEquals(toronto.getHour(), tokyo.getHour());
    }
}