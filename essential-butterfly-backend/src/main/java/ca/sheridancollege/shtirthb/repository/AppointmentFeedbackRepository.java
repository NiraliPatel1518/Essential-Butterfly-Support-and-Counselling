package ca.sheridancollege.shtirthb.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.AppointmentFeedback;

public interface AppointmentFeedbackRepository
        extends JpaRepository<AppointmentFeedback, Long> {

    List<AppointmentFeedback> findAllByAppointmentIdIn(
            Collection<Long> appointmentIds);
}
