package ca.sheridancollege.shtirthb.service;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import ca.sheridancollege.shtirthb.config.AppProperties;

/*
 * "Now" in the clinic's time zone (app.appointments.time-zone). The server
 * may run in another zone (Docker uses UTC), but slots, cut-off times and
 * calendar events are all clinic wall-clock time.
 */
@Component
public class AppointmentClock {

    private final ZoneId zone;

    public AppointmentClock(AppProperties properties) {

        ZoneId resolved;

        try {
            resolved = ZoneId.of(properties.getAppointments().getTimeZone());
        } catch (DateTimeException ex) {
            throw new IllegalStateException(
                    "app.appointments.time-zone is not a valid time zone.", ex);
        }

        this.zone = resolved;
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(zone).withNano(0);
    }
}
