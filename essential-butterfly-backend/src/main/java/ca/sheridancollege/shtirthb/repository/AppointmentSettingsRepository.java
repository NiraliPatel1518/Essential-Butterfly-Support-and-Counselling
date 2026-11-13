package ca.sheridancollege.shtirthb.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import ca.sheridancollege.shtirthb.model.AppointmentSettings;

public interface AppointmentSettingsRepository
        extends JpaRepository<AppointmentSettings, Short> {

    /*
     * Locks the single settings row until the transaction ends. Booking
     * requests take this lock one after another, so two clients can never
     * book the same slot at the same moment (the database constraint is the
     * second line of defence).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AppointmentSettings s where s.id = 1")
    Optional<AppointmentSettings> findForBooking();
}
