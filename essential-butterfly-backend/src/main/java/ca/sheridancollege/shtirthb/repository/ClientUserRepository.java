package ca.sheridancollege.shtirthb.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ca.sheridancollege.shtirthb.model.ClientUser;

public interface ClientUserRepository extends JpaRepository<ClientUser, Long> {

    // Finds the client whose SIGN-IN (primary) address this is.
    @Query("""
            select c
              from ClientUser c
              join c.emails e
             where e.primaryEmail = true
               and e.email = lower(:email)
            """)
    Optional<ClientUser> findByEmail(@Param("email") String email);

    // True if any client holds this address (verified or not).
    @Query("""
            select case when count(e) > 0 then true else false end
              from AccountEmail e
             where e.clientUser is not null
               and e.email = lower(:email)
            """)
    boolean existsByEmail(@Param("email") String email);

    // True if ANOTHER client already has this verified phone number.
    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);
}