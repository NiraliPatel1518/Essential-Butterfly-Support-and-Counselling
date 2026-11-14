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

    @Test void usesTheClinicTimeZoneFromTheSettings() {
        assertEquals(ZoneId.of("America/Toronto"), clockFor("America/Toronto").zone());
        assertEquals(ZoneId.of("Asia/Kolkata"), clockFor("Asia/Kolkata").zone());
    }

    @Test void defaultsToToronto() {
        assertEquals(ZoneId.of("America/Toronto"), new AppointmentClock(new AppProperties()).zone());
    }

    @Test void refusesAnInvalidTimeZoneWithAClearMessage() {
        var thrown = assertThrows(IllegalStateException.class, () -> clockFor("Not/AZone"));

        assertEquals("app.appointments.time-zone is not a valid time zone.", thrown.getMessage());
    }

    @Test void nowIsInTheClinicZoneAndHasNoNanoseconds() {
        AppointmentClock clock = clockFor("America/Toronto");

        LocalDateTime before = LocalDateTime.now(ZoneId.of("America/Toronto")).minusSeconds(2);
        LocalDateTime now = clock.now();
        LocalDateTime after = LocalDateTime.now(ZoneId.of("America/Toronto")).plusSeconds(2);

        assertEquals(0, now.getNano());
        assertFalse(now.isBefore(before));
        assertFalse(now.isAfter(after));
    }

    @Test void twoZonesGiveDifferentWallClockTimes() {
        LocalDateTime toronto = clockFor("America/Toronto").now();
        LocalDateTime tokyo = clockFor("Asia/Tokyo").now();

        assertNotEquals(toronto.getHour(), tokyo.getHour());
    }
}