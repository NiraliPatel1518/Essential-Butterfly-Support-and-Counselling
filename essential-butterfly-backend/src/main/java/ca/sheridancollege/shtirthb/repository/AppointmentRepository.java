package ca.sheridancollege.shtirthb.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ca.sheridancollege.shtirthb.model.Appointment;

public interface AppointmentRepository
        extends JpaRepository<Appointment, Long> {

    // Requests that are waiting or accepted and touch the given period.
    @Query("""
            select a
              from Appointment a
             where a.status in ('REQUESTED', 'CONFIRMED')
               and a.startsAt < :end
               and a.endsAt > :start
            """)
    List<Appointment> findActiveBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            select case when count(a) > 0 then true else false end
              from Appointment a
             where a.status in ('REQUESTED', 'CONFIRMED')
               and a.startsAt < :end
               and a.endsAt > :start
            """)
    boolean existsActiveBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            select a
              from Appointment a
             where a.client.id = :clientId
             order by a.startsAt desc
            """)
    List<Appointment> findAllForClient(@Param("clientId") Long clientId);

    /*
     * Every appointment (newest first), optionally narrowed to one status and
     * to the clients whose name, e-mail address or phone number contains the
     * search text. "all" = true skips the text search.
     */
    @Query("""
            select a
              from Appointment a
              left join a.client c
             where (:status = '' or a.status = :status)
               and (:all = true
                    or lower(c.fullName) like :pattern
                    or c.phoneNumber like :pattern
                    or exists (
                        select e.id
                          from AccountEmail e
                         where e.clientUser = c
                           and e.email like :pattern))
             order by a.startsAt desc
            """)
    List<Appointment> search(
            @Param("status") String status,
            @Param("all") boolean all,
            @Param("pattern") String pattern);

    // Accepted appointments whose time has passed become Completed.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Appointment a
               set a.status = 'COMPLETED',
                   a.updatedAt = :now
             where a.status = 'CONFIRMED'
               and a.endsAt <= :now
            """)
    int completeFinished(@Param("now") LocalDateTime now);
}
