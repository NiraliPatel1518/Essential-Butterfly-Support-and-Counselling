package ca.sheridancollege.shtirthb.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.AppointmentAvailability;

public interface AppointmentAvailabilityRepository
        extends JpaRepository<AppointmentAvailability, Long> {

    List<AppointmentAvailability> findAllByOrderByWeekdayAscStartTimeAsc();
}
