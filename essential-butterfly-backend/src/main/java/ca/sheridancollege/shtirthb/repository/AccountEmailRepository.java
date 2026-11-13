package ca.sheridancollege.shtirthb.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ca.sheridancollege.shtirthb.model.AccountEmail;

public interface AccountEmailRepository
        extends JpaRepository<AccountEmail, Long> {

    /*
     * Global lookup across administrators, clients and pending changes.
     * Addresses are stored in lower case, so the search term is lowered.
     */
    @Query("select e from AccountEmail e where e.email = lower(:email)")
    Optional<AccountEmail> findByAddress(@Param("email") String email);

    @Query("""
            select case when count(e) > 0 then true else false end
              from AccountEmail e
             where e.email = lower(:email)
            """)
    boolean addressInUse(@Param("email") String email);
}
