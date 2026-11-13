package ca.sheridancollege.shtirthb.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/*
 * The single settings row (id = 1): how many hours before its start an
 * unbooked slot disappears from the public website.
 */
@Entity
@Table(name = "appointment_settings")
public class AppointmentSettings {

    public static final short SINGLE_ROW_ID = 1;

    @Id
    private short id;

    @Column(nullable = false)
    private short hideHoursBeforeStart;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected AppointmentSettings() {
    }

    public int getHideHoursBeforeStart() {
        return hideHoursBeforeStart;
    }

    public void changeHideHours(int hours, LocalDateTime now) {
        this.hideHoursBeforeStart = (short) hours;
        this.updatedAt = now;
    }
}
