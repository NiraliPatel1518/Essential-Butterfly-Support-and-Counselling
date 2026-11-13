package ca.sheridancollege.shtirthb.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/*
 * One slot the administrator offers. It is either
 *   - weekly: "every Monday at 10:00 for 60 minutes" (weekday is set), which
 *     repeats every week until changed or removed, or
 *   - one-date: "Friday 13 November at 14:31" (slotDate is set), which
 *     happens once.
 * Exactly one of weekday / slotDate is filled. The slots shown on the website
 * are worked out from these rows.
 */
@Entity
@Table(name = "appointment_availability")
public class AppointmentAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ISO day of week: 1 = Monday ... 7 = Sunday (weekly slots only).
    private Short weekday;

    // The date of a one-date slot (null for a weekly slot).
    private LocalDate slotDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private short slotMinutes;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected AppointmentAvailability() {
    }

    public AppointmentAvailability(
            Integer weekday,
            LocalDate slotDate,
            LocalTime startTime,
            int slotMinutes,
            LocalDateTime now) {

        this.weekday = weekday == null ? null : weekday.shortValue();
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.slotMinutes = (short) slotMinutes;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void change(
            Integer weekday,
            LocalDate slotDate,
            LocalTime startTime,
            int slotMinutes,
            LocalDateTime now) {

        this.weekday = weekday == null ? null : weekday.shortValue();
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.slotMinutes = (short) slotMinutes;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    // null for a one-date slot
    public Integer getWeekday() {
        return weekday == null ? null : weekday.intValue();
    }

    // null for a weekly slot
    public LocalDate getSlotDate() {
        return slotDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public int getSlotMinutes() {
        return slotMinutes;
    }
}
